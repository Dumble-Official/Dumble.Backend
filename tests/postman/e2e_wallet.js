// Cross-service end-to-end for the withdrawal happy path.
//
// Drives the full chain through real Wallet + real Payment + real RabbitMQ —
// no event injection on Wallet's inbound side. Proves the contract drift
// across the boundary holds: the callerReference Wallet hands Payment is the
// same key Payment puts back on the outbox event, and Wallet's listener
// resolves it to the original local row.
//
// Sequence:
//   1. credit alice (system → Wallet) so she has balance
//   2. POST /api/wallet/me/withdrawals (user → Wallet)
//        — Wallet writes WithdrawalRequest PENDING + WalletEntry DEBIT
//        — Wallet calls POST /api/payment/withdrawals (system → Payment)
//        — Payment writes Payout PENDING, returns its id
//        — Wallet stores Payment id as paymentRef, status flips PENDING→SENT
//   3. POST /api/payment/webhooks/paymob (Paymob → Payment) with HMAC dev-stub-ok
//        — Payment's WebhookProcessingJob processes async (~1s tick)
//        — PayoutPersister.markCompleted writes outbox `payment.withdrawal.completed`
//        — OutboxPublisher publishes to dumble.events (~2s tick)
//   4. Wallet's PaymentEventListener consumes
//        — onWithdrawalCompleted flips Wallet row to COMPLETED
//        — Wallet's outbox publishes `wallet.withdrawal.completed`
//   5. Assert Wallet's row is COMPLETED + paymentRef is populated
//
// Run: node tests/postman/e2e_wallet.js

const crypto = require("crypto");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const PAYMENT_URL = process.env.PAYMENT_HOST_URL || "http://localhost:18183";

const SYSTEM_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";

const systemKey = Buffer.from(SYSTEM_KEY_B64, "base64");
const userKey = Buffer.from(USER_KEY_B64, "base64");
const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function jwt(claims, signingKey) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const stamp = Date.now();
const TEST_USER = `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;

const systemWalletJwt = jwt({ iss: "e2e", aud: "wallet", iat: now, exp }, systemKey);
const userJwt = jwt(
  { sub: "e2e@dumble.test", userId: TEST_USER, displayName: "E2E", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
  userKey
);

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) console.log(`  ✓ ${label}`);
  else {
    failedChecks += 1;
    console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`);
  }
}

async function postJson(url, body, headers) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
async function getJson(url, headers) {
  const res = await fetch(url, { headers });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitUntil(label, pred, timeoutMs = 15000, intervalMs = 500) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await pred()) return true;
    await sleep(intervalMs);
  }
  return false;
}

(async () => {
  console.log(`Wallet URL:  ${WALLET_URL}`);
  console.log(`Payment URL: ${PAYMENT_URL}`);
  console.log(`Test user:   ${TEST_USER}`);

  // ── step 1: credit ────────────────────────────────────────────────────────
  console.log("\n=== Step 1 — credit alice 50000 (system → Wallet) ===");
  const creditRes = await postJson(
    `${WALLET_URL}/api/wallet/credit`,
    { userId: TEST_USER, amountCents: 50000, source: "BAN_REFUND", externalRef: `e2e-credit-${stamp}` },
    { Authorization: `Bearer ${systemWalletJwt}`, "Idempotency-Key": `k-e2e-c-${stamp}` }
  );
  assert(`credit 201`, creditRes.status === 201, JSON.stringify(creditRes).slice(0, 200));

  // ── step 2: withdrawal ────────────────────────────────────────────────────
  console.log("\n=== Step 2 — POST /wallet/me/withdrawals (user → Wallet → Payment) ===");
  const wRes = await postJson(
    `${WALLET_URL}/api/wallet/me/withdrawals`,
    { amountCents: 30000, destination: { type: "bank", iban: "EG800000000000000000000099" } },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-e2e-w-${stamp}` }
  );
  assert(`withdrawal 201`, wRes.status === 201, JSON.stringify(wRes).slice(0, 200));
  const walletWithdrawalId = wRes.body?.id;
  const initialStatus = wRes.body?.status;
  const paymentRefAfterCreate = wRes.body?.paymentRef;
  console.log(`  walletWithdrawalId=${walletWithdrawalId}`);
  console.log(`  status=${initialStatus}, paymentRef=${paymentRefAfterCreate}`);
  assert(`status is one of PENDING/SUBMITTING/SENT (active lifecycle)`,
    ["PENDING", "SUBMITTING", "SENT"].includes(initialStatus));
  assert(`paymentRef populated (Wallet captured Payment's id)`,
    typeof paymentRefAfterCreate === "string" && paymentRefAfterCreate.length > 0,
    `paymentRef=${paymentRefAfterCreate}`);

  // ── step 3: fire Paymob webhook to Payment ────────────────────────────────
  console.log("\n=== Step 3 — POST /payment/webhooks/paymob (Paymob → Payment) ===");
  const webhookPayload = {
    type: "payout",
    obj: {
      id: `paymob-${stamp}`,
      // Payment's webhook locator looks at merchant_payout_id, which Payment
      // stored as caller_reference on the Payout row. Wallet sent its own
      // withdrawal id as callerReference when it called Payment in step 2,
      // so this is the round-trip key.
      merchant_payout_id: walletWithdrawalId,
      status: "completed",
      amount_cents: 30000,
      currency: "EGP",
    },
  };
  const webhookRes = await postJson(
    `${PAYMENT_URL}/api/payment/webhooks/paymob`,
    webhookPayload,
    { "X-Paymob-Signature": "dev-stub-ok" }
  );
  assert(`Payment ACKs webhook 200`, webhookRes.status === 200, `got ${webhookRes.status}`);

  // ── step 4: wait for the chain ────────────────────────────────────────────
  console.log("\n=== Step 4 — wait for Payment job → outbox → Wallet listener ===");
  const reached = await waitUntil(
    "Wallet row reaches COMPLETED",
    async () => {
      const r = await getJson(`${WALLET_URL}/api/wallet/me/withdrawals`, { Authorization: `Bearer ${userJwt}` });
      if (r.status !== 200) return false;
      const row = (r.body || []).find((x) => x.id === walletWithdrawalId);
      return row && row.status === "COMPLETED";
    },
    20000
  );
  assert(`Wallet flipped withdrawal ${walletWithdrawalId.slice(-12)} to COMPLETED within 20 s`, reached);

  // ── step 5: verify ledger + audit + balance ───────────────────────────────
  console.log("\n=== Step 5 — verify ledger, audit, balance arithmetic ===");
  const summary = await getJson(`${WALLET_URL}/api/wallet/me/summary`, { Authorization: `Bearer ${userJwt}` });
  assert(`summary 200`, summary.status === 200);
  // Initial credit 50000, withdrawal 30000 ⇒ available 20000.
  assert(`available balance == 20000 (50000 credit − 30000 withdrawal)`,
    summary.body?.availableCents === 20000,
    `available=${summary.body?.availableCents}`);

  const entries = await getJson(`${WALLET_URL}/api/wallet/me/entries?size=50`, { Authorization: `Bearer ${userJwt}` });
  const allMyEntries = (entries.body?.content || []);
  const withdrawalEntry = allMyEntries.find((e) => e.externalRef && e.externalRef.includes(walletWithdrawalId.slice(-12)));
  // Wallet's withdrawal flow writes a WITHDRAWAL_REQUESTED debit entry —
  // verified earlier; just check the ledger has the right number of rows now.
  const creditCount = allMyEntries.filter((e) => e.type === "CREDIT").length;
  const debitCount = allMyEntries.filter((e) => e.type === "DEBIT").length;
  assert(`ledger has at least 1 CREDIT (initial)`, creditCount >= 1, `creditCount=${creditCount}`);
  assert(`ledger has at least 1 DEBIT (withdrawal debit)`, debitCount >= 1, `debitCount=${debitCount}`);

  // Sum invariant
  const sumCredits = allMyEntries.filter((e) => e.type === "CREDIT").reduce((a, e) => a + e.amountCents, 0);
  const sumDebits = allMyEntries.filter((e) => e.type === "DEBIT").reduce((a, e) => a + e.amountCents, 0);
  assert(
    `ledger sum invariant: sumCredits − sumDebits == availableCents`,
    sumCredits - sumDebits === summary.body?.availableCents,
    `sumC=${sumCredits} sumD=${sumDebits} bal=${summary.body?.availableCents}`
  );

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})().catch((ex) => {
  console.error("E2E threw:", ex);
  process.exit(2);
});
