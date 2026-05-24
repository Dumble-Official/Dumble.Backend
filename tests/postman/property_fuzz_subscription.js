// Property-based fuzz for Subscription's PRO-upgrade idempotency contract.
//
// Each "run" picks a single (user, idem-key) and fires K replays sequentially
// then M concurrent. Two invariants checked on every replay:
//
//   I1: planCode + startedAt are STABLE across all replays under the same key
//       (cached response replay integrity)
//   I2: the platform_subscriptions row count for the user doesn't grow beyond
//       1 regardless of how many times we POST under the same idem key
//       (the dedup row is the gate; persist-race fix proven across patterns)
//
// We seed each run with a random idem key from the valid charset and a fresh
// user id. Across N runs, no run may show I1 or I2 violation.
//
// Run: node tests/postman/property_fuzz_subscription.js [runs] [replays] [concurrent]

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];
const RUNS = parseInt(process.argv[2] || "5", 10);
const REPLAYS = parseInt(process.argv[3] || "10", 10);
const CONCURRENT = parseInt(process.argv[4] || "8", 10);

const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const userKey = Buffer.from(USER_KEY_B64, "base64");

const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function jwt(claims, key) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", key).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

function randHex(n) {
  const chars = "0123456789abcdef";
  let s = "";
  for (let i = 0; i < n; i++) s += chars.charAt(Math.floor(Math.random() * 16));
  return s;
}
function randIdemKey(len) {
  // valid charset per regex: ^[A-Za-z0-9._:\-]+$
  const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._:-";
  let s = "";
  for (let i = 0; i < len; i++) s += chars.charAt(Math.floor(Math.random() * chars.length));
  return s;
}

async function postJson(path, body, headers) {
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "subscription-db", "psql",
    "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql], { encoding: "utf8" });
  return { code: r.status || 0, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}

let passed = 0;
let failed = 0;
async function runOne(runIdx) {
  const stamp = Date.now() + runIdx;
  const tail = randHex(12);
  const userId = `00000099-0000-0000-${randHex(4)}-${tail}`;
  const now = Math.floor(stamp / 1000);
  const exp = now + 3600;
  const token = jwt({
    sub: `fuzz-${stamp}@dumble.test`,
    userId,
    displayName: "Fuzz",
    userType: "PARTICIPANT",
    roles: ["PARTICIPANT"],
    iat: now,
    exp,
  }, userKey);
  const idem = `k-fuzz-${randIdemKey(20)}`;
  const body = {
    paymentMethodToken: `tok-fuzz-${stamp}`,
    paymentMethodType: "CARD",
  };
  const headers = { Authorization: `Bearer ${token}`, "Idempotency-Key": idem };

  // Phase 1: K sequential replays
  const seqResults = [];
  for (let i = 0; i < REPLAYS; i++) {
    seqResults.push(await postJson("/api/me/plan/upgrade", body, headers));
  }
  // Phase 2: M concurrent
  const concResults = await Promise.all(
    Array.from({ length: CONCURRENT }, () => postJson("/api/me/plan/upgrade", body, headers))
  );

  const all = [...seqResults, ...concResults];
  const byStatus = {};
  const planCodes = new Set();
  const startedAts = new Set();
  for (const r of all) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.body && r.body.planCode) planCodes.add(r.body.planCode);
    if (r.body && r.body.startedAt) startedAts.add(r.body.startedAt);
  }
  const no5xx = !Object.keys(byStatus).some((s) => Number(s) >= 500);
  const okStable = startedAts.size <= 1 && planCodes.size <= 1;

  // I2: DB count
  const cnt = psql(`SELECT count(*) FROM platform_subscriptions WHERE user_id = '${userId}';`);
  const rowCount = parseInt(cnt.stdout || "0", 10);

  const ok = no5xx && okStable && rowCount <= 1;
  if (ok) {
    passed++;
    console.log(`  ✓ run ${runIdx}: ${all.length} calls, status ${JSON.stringify(byStatus)}, planCode=${[...planCodes].join(",")}, startedAt distinct=${startedAts.size}, db rows=${rowCount}`);
  } else {
    failed++;
    console.log(`  ✗ run ${runIdx}: status ${JSON.stringify(byStatus)}, planCodes=${[...planCodes].join(",")}, startedAt distinct=${startedAts.size}, db rows=${rowCount}`);
  }
}

(async () => {
  console.log(`Subscription URL: ${SUBSCRIPTION_URL}`);
  console.log(`Runs: ${RUNS}, replays/run: ${REPLAYS}, concurrent/run: ${CONCURRENT}`);
  console.log("");
  for (let i = 0; i < RUNS; i++) await runOne(i);
  console.log(`\n${failed === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${passed}/${passed + failed} runs passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
