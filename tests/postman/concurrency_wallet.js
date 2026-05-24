// Concurrency harness for Wallet — fires N parallel HTTP requests and asserts
// the resulting state distribution. Newman can't express true parallelism, so
// these races live here as a node script.
//
// Three races:
//   1. Idempotency same-key race — N parallel POST /wallet/credit with the
//      SAME Idempotency-Key + same body. Expect exactly 1 ledger row, exactly
//      1 distinct walletEntryId, every response in {201, 200, 409}.
//   2. Concurrent-debit over-spend race — credit alice with X cents, then fire
//      M parallel debits each of (X / floor(M/2)) cents. The pessimistic
//      FOR-UPDATE lock + ledger trigger must guarantee the wallet never goes
//      negative — sum of accepted debits <= X.
//   3. Parallel withdrawal race — credit alice with W cents, fire P parallel
//      withdrawal requests of W/2 each. At most 2 may succeed; the rest must
//      come back with 400 InsufficientBalance.
//
// Run: node tests/postman/concurrency_wallet.js
// Env knobs:
//   WALLET_HOST_URL  default http://localhost:18184
//   SIGNING_KEY      defaults to the dev SERVICE_JWT_SIGNING_KEY
//   JWT_SECRET       defaults to the dev JWT_SECRET

const crypto = require("crypto");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
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
  const sig = b64u(
    crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest()
  );
  return `${hdr}.${payload}.${sig}`;
}

// Use a fresh user-id per run so race assertions are deterministic — no
// interference from previous runs' wallet balances.
const stamp = Date.now();
const RACE_USER = `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`;

const now = Math.floor(stamp / 1000);
const exp = now + 3600;

const systemJwt = jwt(
  { iss: "wallet-race", aud: "wallet", iat: now, exp },
  systemKey
);
const userJwt = jwt(
  {
    sub: "race@dumble.test",
    userId: RACE_USER,
    displayName: "Race",
    userType: "PARTICIPANT",
    roles: ["PARTICIPANT"],
    iat: now,
    exp,
  },
  userKey
);

// ── helpers ─────────────────────────────────────────────────────────────────
async function postJson(path, body, headers) {
  const res = await fetch(`${WALLET_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try {
    parsed = await res.json();
  } catch {
    /* non-JSON */
  }
  return { status: res.status, body: parsed };
}

async function getSummary(userId) {
  const res = await fetch(`${WALLET_URL}/api/wallet/${userId}/summary`, {
    headers: { Authorization: `Bearer ${systemJwt}` },
  });
  return res.ok ? await res.json() : { availableCents: 0, pendingCents: 0 };
}

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

// ── scenario 1: same-key idempotency race ───────────────────────────────────
async function idempotencyRace(N = 16) {
  console.log(`\n=== Scenario 1 — ${N} parallel credits, SAME Idempotency-Key ===`);
  const idemKey = `k-race-${stamp}`;
  const body = {
    userId: RACE_USER,
    amountCents: 5000,
    source: "BAN_REFUND",
    externalRef: `race-${stamp}`,
  };
  const headers = { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": idemKey };

  const t0 = Date.now();
  const results = await Promise.all(
    Array.from({ length: N }, () => postJson("/api/wallet/credit", body, headers))
  );
  const elapsed = Date.now() - t0;
  console.log(`  ${N} requests landed in ${elapsed} ms`);

  const byStatus = {};
  const entryIds = new Set();
  for (const r of results) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.body && r.body.walletEntryId) entryIds.add(r.body.walletEntryId);
  }
  console.log("  status distribution:", JSON.stringify(byStatus));
  console.log("  distinct walletEntryIds:", entryIds.size);

  assert(`at least one 201 winner`, (byStatus[201] || 0) >= 1, JSON.stringify(byStatus));
  assert(`no 5xx`, !Object.keys(byStatus).some((s) => Number(s) >= 500), JSON.stringify(byStatus));
  assert(
    `exactly one walletEntryId materialized (no double-credit)`,
    entryIds.size === 1,
    `distinct=${entryIds.size}, ids=${[...entryIds].slice(0, 3).join(",")}`
  );
  assert(
    `all responses are 201/200/409`,
    Object.keys(byStatus).every((s) => ["200", "201", "409"].includes(s)),
    JSON.stringify(byStatus)
  );

  const summary = await getSummary(RACE_USER);
  console.log(`  wallet balance after race: ${summary.availableCents} cents`);
  assert(
    `wallet balance == 5000 (single credit applied, even with ${N} parallel callers)`,
    summary.availableCents === 5000,
    `balance=${summary.availableCents}`
  );
}

// ── scenario 2: parallel-debit over-spend ───────────────────────────────────
async function overSpendRace() {
  console.log("\n=== Scenario 2 — parallel debits against the same wallet (no over-spend) ===");

  // Top up to a known balance.
  const TOPUP = 100000;          // 1000 EGP
  const M = 12;
  const PIECE = 12000;            // each debit attempts 120 EGP
  // M × PIECE = 144000 > TOPUP=100000, so at most floor(100000/12000) = 8 debits succeed.

  const topupRes = await postJson(
    "/api/wallet/credit",
    {
      userId: RACE_USER,
      amountCents: TOPUP,
      source: "BAN_REFUND",
      externalRef: `topup-${stamp}`,
    },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-topup-${stamp}` }
  );
  assert(`topup 201`, topupRes.status === 201, `got ${topupRes.status}`);
  const before = topupRes.body?.newBalanceCents ?? 0;
  console.log(`  balance pre-race: ${before} cents`);

  const t0 = Date.now();
  const debitResults = await Promise.all(
    Array.from({ length: M }, (_, i) =>
      postJson(
        "/api/wallet/debit",
        {
          userId: RACE_USER,
          amountCents: PIECE,
          source: "IN_APP_SPEND",
          externalRef: `race-debit-${stamp}-${i}`,
        },
        {
          Authorization: `Bearer ${userJwt}`,
          "Idempotency-Key": `k-race-debit-${stamp}-${i}`,
        }
      )
    )
  );
  const elapsed = Date.now() - t0;
  console.log(`  ${M} parallel debits landed in ${elapsed} ms`);

  const byStatus = {};
  let succeededCents = 0;
  for (const r of debitResults) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.status === 201) succeededCents += PIECE;
  }
  console.log("  status distribution:", JSON.stringify(byStatus));
  console.log(`  total accepted debit: ${succeededCents} cents`);

  const after = (await getSummary(RACE_USER)).availableCents;
  const maxPossibleSuccess = Math.floor(before / PIECE);
  console.log(`  balance post-race: ${after} cents (was ${before})`);

  assert(`no 5xx`, !Object.keys(byStatus).some((s) => Number(s) >= 500), JSON.stringify(byStatus));
  assert(
    `total accepted debit <= initial balance (FOR-UPDATE lock holds)`,
    succeededCents <= before,
    `accepted=${succeededCents}, available=${before}`
  );
  assert(
    `succeeded count * PIECE == before - after (ledger integrity)`,
    succeededCents === before - after,
    `accepted=${succeededCents}, before-after=${before - after}`
  );
  assert(
    `succeeded count == floor(balance / PIECE) = ${maxPossibleSuccess}`,
    (byStatus[201] || 0) === maxPossibleSuccess,
    `got ${byStatus[201] || 0} 201s, expected ${maxPossibleSuccess}`
  );
  assert(
    `the rest got 400 InsufficientBalance`,
    (byStatus[400] || 0) === M - maxPossibleSuccess,
    `400s=${byStatus[400] || 0}, expected ${M - maxPossibleSuccess}`
  );
  assert(`balance never went negative`, after >= 0, `balance=${after}`);
}

// ── scenario 3: parallel withdrawal race ────────────────────────────────────
async function withdrawalRace() {
  console.log("\n=== Scenario 3 — parallel withdrawal requests on same wallet ===");

  // Bring balance up to something sufficient for two withdrawals.
  const TOPUP = 200000;
  await postJson(
    "/api/wallet/credit",
    {
      userId: RACE_USER,
      amountCents: TOPUP,
      source: "BAN_REFUND",
      externalRef: `wtopup-${stamp}`,
    },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-wtopup-${stamp}` }
  );

  const before = (await getSummary(RACE_USER)).availableCents;
  const P = 6;
  const WAMT = 50000;             // each withdrawal asks 500 EGP; at most 4 can succeed
  console.log(`  balance pre-race: ${before} cents — firing ${P} parallel ${WAMT}-cent withdrawals`);

  const t0 = Date.now();
  const results = await Promise.all(
    Array.from({ length: P }, (_, i) =>
      postJson(
        "/api/wallet/me/withdrawals",
        {
          amountCents: WAMT,
          destination: { type: "bank", iban: "EG800000000000000000000099" },
        },
        {
          Authorization: `Bearer ${userJwt}`,
          "Idempotency-Key": `k-race-w-${stamp}-${i}`,
        }
      )
    )
  );
  const elapsed = Date.now() - t0;
  console.log(`  ${P} parallel withdrawals landed in ${elapsed} ms`);

  const byStatus = {};
  let acceptedCents = 0;
  for (const r of results) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.status === 201) acceptedCents += WAMT;
  }
  console.log("  status distribution:", JSON.stringify(byStatus));
  console.log(`  total accepted withdrawal: ${acceptedCents} cents`);

  const after = (await getSummary(RACE_USER)).availableCents;
  console.log(`  balance post-race: ${after} cents`);
  const maxPossible = Math.floor(before / WAMT);

  assert(`no 5xx`, !Object.keys(byStatus).some((s) => Number(s) >= 500), JSON.stringify(byStatus));
  assert(
    `accepted cents <= initial balance`,
    acceptedCents <= before,
    `accepted=${acceptedCents}, available=${before}`
  );
  assert(
    `successful withdrawals == floor(balance / WAMT) = ${maxPossible}`,
    (byStatus[201] || 0) === maxPossible,
    `got ${byStatus[201] || 0} 201s`
  );
  assert(`balance never went negative`, after >= 0, `balance=${after}`);
  assert(
    `every response is 201 or 400 (no surprise codes)`,
    Object.keys(byStatus).every((s) => ["201", "400"].includes(s)),
    JSON.stringify(byStatus)
  );
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Wallet URL: ${WALLET_URL}`);
  console.log(`Race user:  ${RACE_USER}`);

  try {
    await idempotencyRace(16);
    await overSpendRace();
    await withdrawalRace();
  } catch (ex) {
    console.error("Harness threw:", ex);
    process.exit(2);
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${
      totalChecks - failedChecks
    }/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
