// Sustained-load harness for Payment.
//
// Sends `rate` charges per second for `duration` seconds, one fresh
// Idempotency-Key per request (so the idempotency dedup layer doesn't
// short-circuit the work), measures end-to-end latency, and reports a
// histogram of HTTP statuses plus p50/p95/p99/max latency. Useful for
// catching HikariCP exhaustion, GC stalls, and slow downstream behavior
// that the burst-of-12 race test can't surface.
//
// Run: node tests/postman/load_soak.js [rate] [duration_seconds]
//   defaults: rate=50, duration=30
//
// Knobs (env):
//   PAYMENT_HOST_URL  default http://localhost:18183
//   SIGNING_KEY       defaults to the dev key from release/.env
//   LOAD_RATE         overrides rate
//   LOAD_DURATION_S   overrides duration
const crypto = require("crypto");

const PAYMENT_URL = process.env.PAYMENT_HOST_URL || "http://localhost:18183";
const SIGNING_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const RATE = parseInt(process.argv[2] || process.env.LOAD_RATE || "50", 10);
const DURATION_S = parseInt(process.argv[3] || process.env.LOAD_DURATION_S || "30", 10);

const key = Buffer.from(SIGNING_KEY_B64, "base64");
const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

function mintSystemJwt() {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const payload = b64u(
    JSON.stringify({ iss: "payment-service", aud: "payment", iat: now, exp: now + 3600 })
  );
  const sig = b64u(crypto.createHmac("sha256", key).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return null;
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

async function main() {
  const jwt = mintSystemJwt();
  const intervalMs = 1000 / RATE;
  const startedAt = Date.now();
  const promises = [];
  const latencies = [];
  const byStatus = {};
  let errors = 0;
  const userId = "00000099-0000-0000-0000-000000000901";

  console.log(`Load test: ${RATE} req/s × ${DURATION_S} s → ~${RATE * DURATION_S} requests`);
  console.log(`Payment URL: ${PAYMENT_URL}`);
  console.log(`Spacing: ${intervalMs.toFixed(2)} ms between request kickoffs\n`);

  let seq = 0;
  const interval = setInterval(() => {
    const i = seq++;
    const sentAt = process.hrtime.bigint();
    const idemKey = `k-load-${startedAt}-${i}`;
    const p = fetch(`${PAYMENT_URL}/api/payment/charges`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${jwt}`,
        "Idempotency-Key": idemKey,
      },
      body: JSON.stringify({
        userId,
        amountCents: 100,
        currency: "EGP",
        description: "load",
        callerReference: idemKey,
      }),
    })
      .then(async (res) => {
        const elapsedMs = Number(process.hrtime.bigint() - sentAt) / 1e6;
        latencies.push(elapsedMs);
        byStatus[res.status] = (byStatus[res.status] || 0) + 1;
        // Drain body so the socket can be reused.
        try { await res.text(); } catch {}
      })
      .catch((ex) => {
        errors += 1;
        const elapsedMs = Number(process.hrtime.bigint() - sentAt) / 1e6;
        latencies.push(elapsedMs);
        if (errors <= 3) console.log(`  (error ${errors}) ${ex.message}`);
      });
    promises.push(p);
  }, intervalMs);

  await new Promise((r) => setTimeout(r, DURATION_S * 1000));
  clearInterval(interval);
  console.log(`Kickoffs complete (${seq}). Awaiting in-flight responses ...`);
  await Promise.allSettled(promises);
  const wallMs = Date.now() - startedAt;

  const sorted = [...latencies].sort((a, b) => a - b);
  const p50 = percentile(sorted, 50);
  const p95 = percentile(sorted, 95);
  const p99 = percentile(sorted, 99);
  const max = sorted[sorted.length - 1];
  const min = sorted[0];

  console.log(`\n── results ─────────────────────────────────────────────`);
  console.log(`requests kicked off:   ${seq}`);
  console.log(`responses received:    ${latencies.length}`);
  console.log(`network errors:        ${errors}`);
  console.log(`wall-clock time:       ${(wallMs / 1000).toFixed(2)}s`);
  console.log(`effective rate:        ${(latencies.length / (wallMs / 1000)).toFixed(1)} req/s`);
  console.log(`status distribution:   ${JSON.stringify(byStatus)}`);
  console.log(`latency min/p50/p95/p99/max: ${min?.toFixed(1)} / ${p50?.toFixed(1)} / ${p95?.toFixed(1)} / ${p99?.toFixed(1)} / ${max?.toFixed(1)} ms`);

  // Pass criteria (informational — the suite logs failures rather than
  // crashing the run, so CI can capture the data even when SLOs slip).
  const tooMany5xx = Object.entries(byStatus).some(([s, n]) => Number(s) >= 500);
  const tooManyErrors = errors > seq * 0.01; // >1% network error rate
  const tooSlow = p99 && p99 > 2000; // p99 > 2s
  let ok = true;
  if (tooMany5xx) { console.log(`✗ saw 5xx responses`); ok = false; }
  if (tooManyErrors) { console.log(`✗ >1% network errors (${errors}/${seq})`); ok = false; }
  if (tooSlow) { console.log(`✗ p99 latency above 2000 ms (${p99?.toFixed(1)} ms)`); ok = false; }
  if (ok) console.log(`✓ within SLO`);
  process.exit(ok ? 0 : 1);
}

main();
