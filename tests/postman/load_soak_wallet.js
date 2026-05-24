// Sustained-load harness for Wallet. Fires N concurrent workers each looping
// alternating credit/debit until the target duration elapses. Measures
// per-request latency, derives p50 / p95 / p99 / max, asserts no 5xx, and
// validates the final wallet balance equals the arithmetic sum (no
// double-counting, no lost updates).
//
// Run:
//   node tests/postman/load_soak_wallet.js [concurrency] [seconds]
//
// Defaults: 20 concurrent workers for 30 s.

const crypto = require("crypto");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const CONCURRENCY = parseInt(process.argv[2] || "20", 10);
const DURATION_SEC = parseInt(process.argv[3] || "30", 10);

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
const systemJwt = jwt({ iss: "load", aud: "wallet", iat: now, exp }, systemKey);
const userJwt = jwt(
  { sub: "load@dumble.test", userId: TEST_USER, displayName: "Load", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
  userKey
);

async function postJson(path, body, headers) {
  const t0 = process.hrtime.bigint();
  let status = 0;
  let err = null;
  try {
    const res = await fetch(`${WALLET_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
    });
    status = res.status;
    await res.text();   // drain response body so the connection can be reused
  } catch (ex) {
    err = ex.name + ": " + ex.message;
  }
  const elapsedMs = Number(process.hrtime.bigint() - t0) / 1e6;
  return { status, err, elapsedMs };
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

(async () => {
  console.log(`Wallet URL:    ${WALLET_URL}`);
  console.log(`Test user:     ${TEST_USER}`);
  console.log(`Workers:       ${CONCURRENCY}`);
  console.log(`Duration:      ${DURATION_SEC} s`);

  // Bootstrap a wallet with a comfortable floor so debits don't underflow.
  const bootstrap = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 10_000_000, source: "BAN_REFUND", externalRef: `load-boot-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-load-boot-${stamp}` }
  );
  if (bootstrap.status !== 201) {
    console.error("bootstrap failed:", bootstrap);
    process.exit(2);
  }
  const baseline = 10_000_000;   // we just credited 10M

  const startedAt = Date.now();
  const deadline = startedAt + DURATION_SEC * 1000;

  const latencies = [];
  const byStatus = {};
  let netDelta = 0;          // signed sum of all accepted credits - debits
  let total = 0;

  async function worker(workerId) {
    let seq = 0;
    while (Date.now() < deadline) {
      const credit = seq % 2 === 0;   // alternate credit / debit
      const path = credit ? "/api/wallet/credit" : "/api/wallet/debit";
      const idem = `k-load-${workerId}-${seq}-${stamp}`;
      const body = credit
        ? { userId: TEST_USER, amountCents: 10, source: "BAN_REFUND", externalRef: `loadc-${workerId}-${seq}-${stamp}` }
        : { userId: TEST_USER, amountCents: 10, source: "IN_APP_SPEND", externalRef: `loadd-${workerId}-${seq}-${stamp}` };
      const headers = {
        Authorization: `Bearer ${credit ? systemJwt : userJwt}`,
        "Idempotency-Key": idem,
      };
      const r = await postJson(path, body, headers);
      total += 1;
      byStatus[r.status] = (byStatus[r.status] || 0) + 1;
      latencies.push(r.elapsedMs);
      if (r.status === 201) netDelta += credit ? 10 : -10;
      seq += 1;
    }
  }

  await Promise.all(Array.from({ length: CONCURRENCY }, (_, i) => worker(i)));

  const elapsedSec = (Date.now() - startedAt) / 1000;
  latencies.sort((a, b) => a - b);
  const p50 = percentile(latencies, 50);
  const p95 = percentile(latencies, 95);
  const p99 = percentile(latencies, 99);
  const max = latencies[latencies.length - 1] || 0;
  const rps = (total / elapsedSec).toFixed(1);

  console.log("\n— Results —");
  console.log(`total requests:        ${total}`);
  console.log(`duration:              ${elapsedSec.toFixed(1)} s`);
  console.log(`throughput:            ${rps} req/s`);
  console.log(`status distribution:   ${JSON.stringify(byStatus)}`);
  console.log(`latency p50:           ${p50.toFixed(1)} ms`);
  console.log(`latency p95:           ${p95.toFixed(1)} ms`);
  console.log(`latency p99:           ${p99.toFixed(1)} ms`);
  console.log(`latency max:           ${max.toFixed(1)} ms`);

  // Read final balance, compare against the running delta.
  const summaryRes = await fetch(`${WALLET_URL}/api/wallet/${TEST_USER}/summary`, {
    headers: { Authorization: `Bearer ${systemJwt}` },
  });
  const finalBal = (await summaryRes.json()).availableCents ?? 0;
  const expected = baseline + netDelta;
  console.log(`final balance:         ${finalBal}`);
  console.log(`expected (baseline ${baseline} + netDelta ${netDelta}): ${expected}`);

  let failed = 0;
  const check = (label, ok) => {
    if (ok) console.log(`  ✓ ${label}`);
    else { failed += 1; console.log(`  ✗ ${label}`); }
  };

  check(`no 5xx during ${DURATION_SEC}s burst`, !Object.keys(byStatus).some((s) => Number(s) >= 500));
  check(`p99 latency < 2000 ms`, p99 < 2000);
  check(`max latency < 5000 ms`, max < 5000);
  check(`balance arithmetic matches (no lost updates, no double-counting)`, finalBal === expected);

  console.log(`\n${failed === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${4 - failed}/4 SLO checks passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
