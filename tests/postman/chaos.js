// Chaos / fault-injection harness for Payment.
//
// Each scenario is a "kill a dependency at the wrong moment" test that
// answers: does Payment degrade gracefully? Does it recover when the dep
// comes back? Are state and outbox rows consistent after the dust settles?
//
// Knobs:
//   PAYMENT_HOST_URL  default http://localhost:18183
//   SIGNING_KEY       defaults to the dev key
//
// Tools used:
//   docker compose stop/start <service>  — simulate a hard outage
//   psql via docker exec                  — inspect post-recovery state
//
// Run: node tests/postman/chaos.js
const crypto = require("crypto");
const { execSync } = require("child_process");

const PAYMENT_URL = process.env.PAYMENT_HOST_URL || "http://localhost:18183";
const SIGNING_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";

const COMPOSE = `docker compose -f release/docker-compose.yml -f release/docker-compose.override.yml`;
const PG_PAYMENT = `${COMPOSE} exec -T payment-db psql -U postgres -d dumble_payment -At -F"|"`;

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

async function postJson(path, body, headers) {
  const res = await fetch(`${PAYMENT_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

async function getJson(path, headers) {
  const res = await fetch(`${PAYMENT_URL}${path}`, { headers: { ...headers } });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function sh(cmd) { return execSync(cmd, { encoding: "utf8" }).trim(); }
function shTry(cmd) {
  try { return execSync(cmd, { encoding: "utf8" }).trim(); }
  catch (ex) { return null; }
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

// Wait for the given URL to answer HTTP 200 within timeoutMs.
async function waitForHealthy(url, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (res.status === 200) return true;
    } catch {}
    await sleep(1000);
  }
  return false;
}

// ── 1. RabbitMQ killed during outbox publishing ─────────────────────────────
async function scenarioRabbitOutage(jwt) {
  console.log("\n=== Scenario 1 — RabbitMQ outage while outbox publisher is active ===");

  console.log("  stopping rabbitmq ...");
  sh(`${COMPOSE} stop rabbitmq`);
  await sleep(2000);

  // Fire a successful charge → webhook so the outbox writer records a row
  // that the publisher will FAIL to send (broker down).
  const callerRef = `chaos-rabbit-${Date.now()}`;
  const idemKey = `k-${callerRef}`;
  const providerRef = String((Date.now() % 9000000) + 11000000);

  const created = await postJson(
    "/api/payment/charges",
    {
      userId: "00000099-0000-0000-0000-0000000000c1",
      amountCents: 3000,
      currency: "EGP",
      callerReference: callerRef,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey }
  );
  assert("Payment still accepts charge while RabbitMQ is down", created.status === 201,
    `got ${created.status}`);
  if (created.status !== 201) {
    // re-start RabbitMQ before bailing
    sh(`${COMPOSE} start rabbitmq`);
    return;
  }
  const chargeId = created.body.chargeId;

  await postJson(
    "/api/payment/webhooks/paymob",
    {
      type: "transaction",
      obj: {
        id: Number(providerRef),
        amount_cents: 3000,
        currency: "EGP",
        success: true,
        merchant_order_id: chargeId,
      },
    },
    { "X-Paymob-Signature": "dev-stub-ok" }
  );
  await sleep(3500);

  // Outbox row should be PENDING (publisher can't reach broker).
  const sql = `SELECT status, attempts FROM outbox_events WHERE event_type='ChargeSucceeded' AND payload_json LIKE '%${callerRef}%' ORDER BY created_at DESC LIMIT 1;`;
  const out = sh(`${PG_PAYMENT} -c "${sql}"`);
  const [status, attempts] = (out || "|").split("|");
  assert("outbox row still exists (PENDING or IN_FLIGHT)",
    status === "PENDING" || status === "IN_FLIGHT",
    `status=${status} attempts=${attempts}`);

  console.log("  starting rabbitmq ...");
  sh(`${COMPOSE} start rabbitmq`);
  const rabbitBack = await waitForHealthy("http://localhost:15672/", 60000)
    || await waitForHealthy(`${PAYMENT_URL}/api/actuator/health`, 60000);
  assert("rabbitmq back online", rabbitBack);

  // Give the publisher enough ticks to drain.
  await sleep(8000);

  const out2 = sh(`${PG_PAYMENT} -c "${sql}"`);
  const [status2] = (out2 || "|").split("|");
  assert(`outbox row eventually drained to PUBLISHED (got ${status2})`,
    status2 === "PUBLISHED", `final status=${status2}`);
}

// ── 2. payment-db killed mid-flow ───────────────────────────────────────────
async function scenarioDbOutage(jwt) {
  console.log("\n=== Scenario 2 — payment-db outage and reconnect ===");

  console.log("  stopping payment-db ...");
  sh(`${COMPOSE} stop payment-db`);
  await sleep(2000);

  const callerRef = `chaos-db-${Date.now()}`;
  const idemKey = `k-${callerRef}`;

  // With the DB down, Payment can't persist Phase-1 row → expect 5xx, not a
  // hang.
  const t0 = Date.now();
  const created = await postJson(
    "/api/payment/charges",
    {
      userId: "00000099-0000-0000-0000-0000000000c2",
      amountCents: 1000,
      currency: "EGP",
      callerReference: callerRef,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey }
  );
  const elapsed = Date.now() - t0;
  // 5xx is the correct degraded response; the elapsed is dominated by Hikari's
  // connection-timeout (default 30s). Tuning that lower is a separate
  // operational decision — not a bug, just a knob worth tracking.
  assert(`Payment returns 5xx under DB outage (got ${created.status} in ${elapsed}ms — Hikari timeout = 30s default)`,
    created.status >= 500 && created.status < 600 && elapsed < 35000,
    `expected 5xx within 35s, got ${created.status} in ${elapsed}ms`);

  console.log("  starting payment-db ...");
  sh(`${COMPOSE} start payment-db`);

  // Wait for Payment to reconnect by hitting actuator/health until 200.
  const back = await waitForHealthy(`${PAYMENT_URL}/api/actuator/health`, 90000);
  assert("Payment health endpoint back to 200 after db restart", back);

  // Now a fresh charge should succeed — proves the connection pool recovered
  // rather than staying stuck on dead sockets.
  const callerRef2 = `chaos-db-recover-${Date.now()}`;
  const recovered = await postJson(
    "/api/payment/charges",
    {
      userId: "00000099-0000-0000-0000-0000000000c2",
      amountCents: 1000,
      currency: "EGP",
      callerReference: callerRef2,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": `k-${callerRef2}` }
  );
  assert(`Payment serves a fresh charge after DB recovery (got ${recovered.status})`,
    recovered.status === 201);
}

// ── 3. Connection pool warmup probe ─────────────────────────────────────────
async function scenarioPoolSaturation(jwt) {
  console.log("\n=== Scenario 3 — burst that approaches HikariCP pool size ===");
  // Default HikariCP max-pool-size is 10; we send 25 parallel charges with
  // distinct keys and expect every one to land OK (queue-then-borrow inside
  // pool, no 500s). Latency may spike but no requests should fail with a
  // pool-exhaustion 5xx.
  const t0 = Date.now();
  const reqs = Array.from({ length: 25 }, (_, i) => {
    const ref = `pool-${Date.now()}-${i}`;
    return postJson(
      "/api/payment/charges",
      {
        userId: "00000099-0000-0000-0000-0000000000c3",
        amountCents: 100,
        currency: "EGP",
        callerReference: ref,
      },
      { Authorization: `Bearer ${jwt}`, "Idempotency-Key": `k-${ref}` }
    );
  });
  const results = await Promise.all(reqs);
  const elapsed = Date.now() - t0;
  const byStatus = {};
  for (const r of results) byStatus[r.status] = (byStatus[r.status] || 0) + 1;
  const have5xx = Object.keys(byStatus).some((s) => Number(s) >= 500);
  console.log(`  25 parallel requests in ${elapsed}ms — status dist ${JSON.stringify(byStatus)}`);
  assert("burst > pool size does not produce 5xx (queued by Hikari)", !have5xx);
  assert("at least 23/25 succeed with 201", (byStatus["201"] || 0) >= 23);
}

(async () => {
  const jwt = mintSystemJwt();
  console.log(`Payment URL: ${PAYMENT_URL}`);

  try {
    await scenarioPoolSaturation(jwt);
    await scenarioRabbitOutage(jwt);
    await scenarioDbOutage(jwt);
  } catch (ex) {
    console.error("Harness threw:", ex);
    process.exit(2);
  } finally {
    // Ensure both services are running on exit
    shTry(`${COMPOSE} start rabbitmq`);
    shTry(`${COMPOSE} start payment-db`);
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${
      totalChecks - failedChecks
    }/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
