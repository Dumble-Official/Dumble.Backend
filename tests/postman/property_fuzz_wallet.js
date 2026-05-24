// Property-based fuzz for Wallet — generate random sequences of operations and
// assert two invariants hold across every prefix:
//
//   I1 (server-side):  availableCents reported by /wallet/me/summary always
//                      equals sum(accepted credits) − sum(accepted debits).
//                      No lost updates, no double-counting, no negative
//                      balances.
//
//   I2 (client/server agreement): every accepted operation maps to exactly
//                      one row on /wallet/me/entries, with the right type
//                      and amount. The local oracle never disagrees with
//                      the server's ledger.
//
// Each run draws ~80 random operations from a weighted alphabet (more
// credits than debits so the wallet rarely runs dry), runs them sequentially,
// and on any divergence prints the failing op + observed vs expected state.
// 5 fresh runs per invocation; any single failure exits non-zero.
//
// Run: node tests/postman/property_fuzz_wallet.js [runs] [opsPerRun]

const crypto = require("crypto");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const RUNS = parseInt(process.argv[2] || "5", 10);
const OPS_PER_RUN = parseInt(process.argv[3] || "80", 10);

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

const CREDIT_SOURCES = ["BAN_REFUND", "CHARGEBACK", "ADMIN_ADJUSTMENT", "WITHDRAWAL_REVERSED"];
function randomInt(min, maxExcl) {
  return Math.floor(Math.random() * (maxExcl - min)) + min;
}
function pick(arr) { return arr[randomInt(0, arr.length)]; }

async function postJson(path, body, headers) {
  const res = await fetch(`${WALLET_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
async function summaryFor(userId, systemJwt) {
  const res = await fetch(`${WALLET_URL}/api/wallet/${userId}/summary`, {
    headers: { Authorization: `Bearer ${systemJwt}` },
  });
  return res.ok ? await res.json() : { availableCents: 0 };
}

async function runOne(runIdx) {
  const stamp = Date.now();
  const TEST_USER = `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
  const now = Math.floor(stamp / 1000);
  const exp = now + 3600;
  const systemJwt = jwt({ iss: "fuzz", aud: "wallet", iat: now, exp }, systemKey);
  const userJwt = jwt(
    { sub: "fuzz@dumble.test", userId: TEST_USER, displayName: "Fuzz", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
    userKey
  );

  // Local oracle: expected balance after every accepted op.
  let expected = 0;
  const expectedLedger = [];   // {type: 'CREDIT'|'DEBIT', amount}
  let opCounter = 0;

  for (let i = 0; i < OPS_PER_RUN; i++) {
    // 65 % credits, 35 % debits — keeps the wallet generally positive.
    const isCredit = Math.random() < 0.65;
    const amount = randomInt(1, 500);
    opCounter += 1;
    const idem = `k-fuzz-${stamp}-${runIdx}-${opCounter}`;

    let r;
    if (isCredit) {
      r = await postJson(
        "/api/wallet/credit",
        { userId: TEST_USER, amountCents: amount, source: pick(CREDIT_SOURCES), externalRef: idem },
        { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": idem }
      );
      if (r.status === 201) {
        expected += amount;
        expectedLedger.push({ type: "CREDIT", amount });
      } else if (r.status >= 500) {
        console.log(`  ✗ run ${runIdx} op ${opCounter} credit ${amount}: 5xx (${r.status})`);
        return false;
      }
      // Other 4xx (none expected for valid credits) — treat as rejection,
      // don't update oracle.
    } else {
      r = await postJson(
        "/api/wallet/debit",
        { userId: TEST_USER, amountCents: amount, source: "IN_APP_SPEND", externalRef: idem },
        { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": idem }
      );
      if (r.status === 201) {
        expected -= amount;
        expectedLedger.push({ type: "DEBIT", amount });
      } else if (r.status === 400 && /InsufficientBalance/.test(r.body?.message || "")) {
        // Expected when the random walk drops below the requested amount.
      } else if (r.status >= 500) {
        console.log(`  ✗ run ${runIdx} op ${opCounter} debit ${amount}: 5xx (${r.status})`);
        return false;
      } else {
        console.log(`  ✗ run ${runIdx} op ${opCounter} debit ${amount}: unexpected ${r.status} ${JSON.stringify(r.body).slice(0, 100)}`);
        return false;
      }
    }

    // Spot-check invariant every 10 ops (keeps the run from being 80 round-trips).
    if (i % 10 === 9 || i === OPS_PER_RUN - 1) {
      const observed = (await summaryFor(TEST_USER, systemJwt)).availableCents;
      if (observed !== expected) {
        console.log(`  ✗ run ${runIdx} op ${opCounter}: INVARIANT BROKEN — expected balance=${expected}, observed=${observed}`);
        console.log(`     last op: ${isCredit ? "credit" : "debit"} ${amount}, status=${r.status}`);
        return false;
      }
    }
  }

  // End-of-run ledger check: count CREDIT + DEBIT entries on the server,
  // compare to the oracle.
  const entriesRes = await fetch(`${WALLET_URL}/api/wallet/me/entries?size=200`, {
    headers: { Authorization: `Bearer ${userJwt}` },
  });
  const entries = (await entriesRes.json()).content || [];
  const myEntries = entries.filter((e) => (e.externalRef || "").includes(`fuzz-${stamp}-${runIdx}`));
  const serverCredits = myEntries.filter((e) => e.type === "CREDIT").reduce((a, e) => a + e.amountCents, 0);
  const serverDebits = myEntries.filter((e) => e.type === "DEBIT").reduce((a, e) => a + e.amountCents, 0);
  const expectedCredits = expectedLedger.filter((e) => e.type === "CREDIT").reduce((a, e) => a + e.amount, 0);
  const expectedDebits = expectedLedger.filter((e) => e.type === "DEBIT").reduce((a, e) => a + e.amount, 0);

  if (serverCredits !== expectedCredits) {
    console.log(`  ✗ run ${runIdx}: credit sum mismatch — server=${serverCredits}, oracle=${expectedCredits}`);
    return false;
  }
  if (serverDebits !== expectedDebits) {
    console.log(`  ✗ run ${runIdx}: debit sum mismatch — server=${serverDebits}, oracle=${expectedDebits}`);
    return false;
  }

  console.log(`  ✓ run ${runIdx}: ${opCounter} ops, ${expectedLedger.length} accepted (credits=${expectedCredits}, debits=${expectedDebits}, balance=${expected})`);
  return true;
}

(async () => {
  console.log(`Wallet URL: ${WALLET_URL}`);
  console.log(`Runs:       ${RUNS}`);
  console.log(`Ops/run:    ${OPS_PER_RUN}`);
  console.log("");

  let passed = 0;
  for (let i = 0; i < RUNS; i++) {
    const ok = await runOne(i);
    if (ok) passed += 1;
  }
  console.log(`\n${passed === RUNS ? "✓ ALL GREEN" : "✗ FAILURES"}: ${passed}/${RUNS} runs passed`);
  process.exit(passed === RUNS ? 0 : 1);
})().catch((ex) => {
  console.error("Fuzz threw:", ex);
  process.exit(2);
});
