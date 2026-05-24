// Sustained-load harness for Subscription — N RPS × M seconds against the
// /me/plan/upgrade endpoint, each call with its own (user, idempotency key)
// so we measure throughput without idempotency-conflict noise.
//
// SLO: no 5xx, p99 < 3 s. The 3-second p99 budget reflects that each upgrade
// makes an outbound HTTP call to Payment (charge dispatch) — Wallet's tighter
// 2-second budget doesn't apply because Wallet's hot path stays in-process.
//
// Run: node tests/postman/load_soak_subscription.js [RPS=20] [SECONDS=15]

const crypto = require("crypto");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const RPS = parseInt(process.argv[2] || "20", 10);
const SECONDS = parseInt(process.argv[3] || "15", 10);

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

const stamp = Date.now();
const now = Math.floor(stamp / 1000);
const exp = now + 3600;

function userJwtFor(i) {
  const userId = `00000099-0000-0000-${stamp.toString(16).padStart(16, "0").slice(-4)}-${i.toString(16).padStart(12, "0").slice(-12)}`;
  return {
    userId,
    token: jwt(
      { sub: `load-${i}@dumble.test`, userId, displayName: "L",
        userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
      userKey
    ),
  };
}

(async () => {
  console.log(`Load test: ${RPS} req/s × ${SECONDS} s → ~${RPS * SECONDS} requests`);
  console.log(`Subscription URL: ${SUBSCRIPTION_URL}`);
  const intervalMs = 1000 / RPS;
  console.log(`Spacing: ${intervalMs.toFixed(2)} ms between request kickoffs\n`);

  const latencies = [];
  let kicked = 0;
  let errors = 0;
  const statusDist = {};
  const inflight = [];

  const start = Date.now();
  const deadline = start + SECONDS * 1000;
  let i = 0;

  while (Date.now() < deadline) {
    const targetSendAt = start + i * intervalMs;
    const wait = targetSendAt - Date.now();
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
    const idx = i++;
    kicked++;
    const t0 = Date.now();
    const caller = userJwtFor(idx);
    const p = fetch(`${SUBSCRIPTION_URL}/api/me/plan/upgrade`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${caller.token}`,
        "Idempotency-Key": `k-load-${stamp}-${idx}`,
      },
      body: JSON.stringify({
        paymentMethodToken: `tok-load-${stamp}-${idx}`,
        paymentMethodType: "CARD",
      }),
    })
      .then((res) => {
        const elapsed = Date.now() - t0;
        latencies.push(elapsed);
        statusDist[res.status] = (statusDist[res.status] || 0) + 1;
      })
      .catch((ex) => {
        errors++;
        const elapsed = Date.now() - t0;
        latencies.push(elapsed);
      });
    inflight.push(p);
  }

  console.log(`Kickoffs complete (${kicked}). Awaiting in-flight responses ...`);
  await Promise.all(inflight);
  const wallClockSec = ((Date.now() - start) / 1000).toFixed(2);

  latencies.sort((a, b) => a - b);
  const pct = (p) => latencies[Math.min(latencies.length - 1, Math.floor((p / 100) * latencies.length))];
  const p50 = pct(50), p95 = pct(95), p99 = pct(99), max = latencies[latencies.length - 1];
  const min = latencies[0];

  console.log("\n── results ─────────────────────────────────────────────");
  console.log(`requests kicked off:   ${kicked}`);
  console.log(`responses received:    ${latencies.length}`);
  console.log(`network errors:        ${errors}`);
  console.log(`wall-clock time:       ${wallClockSec}s`);
  console.log(`effective rate:        ${(latencies.length / parseFloat(wallClockSec)).toFixed(1)} req/s`);
  console.log(`status distribution:   ${JSON.stringify(statusDist)}`);
  console.log(`latency min/p50/p95/p99/max: ${min} / ${p50} / ${p95} / ${p99} / ${max} ms`);

  const no5xx = !Object.keys(statusDist).some((s) => Number(s) >= 500);
  const slo = p99 < 3000 && no5xx;
  console.log(slo ? "✓ within SLO" : "✗ SLO violated");
  process.exit(slo ? 0 : 1);
})();
