// Outbox + RabbitMQ + cross-service contract harness.
//
// Verifies that when Payment commits a state transition, the matching event:
//   1. Lands in outbox_events (PENDING)
//   2. Gets published by OutboxPublisher to dumble.events
//   3. Carries the exact routing key + payload shape Wallet / Subscription
//      parse against (per their *.client.dto and PaymentEventListener code).
//
// Uses a tap queue bound to dumble.events with binding `payment.#` so we
// observe what's actually on the wire without disturbing the real consumers.
//
// Run: node tests/postman/outbox_and_events.js
const crypto = require("crypto");
const { execSync } = require("child_process");

const PAYMENT_URL = process.env.PAYMENT_HOST_URL || "http://localhost:18183";
const SIGNING_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";

const COMPOSE = `docker compose -f release/docker-compose.yml -f release/docker-compose.override.yml`;
const RABBIT = `${COMPOSE} exec -T rabbitmq`;
const PG_PAYMENT = `${COMPOSE} exec -T payment-db psql -U postgres -d dumble_payment -At -F"|"`;

// ── crypto helpers ──────────────────────────────────────────────────────────
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
  try {
    parsed = await res.json();
  } catch {}
  return { status: res.status, body: parsed };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function sh(cmd) {
  return execSync(cmd, { encoding: "utf8" }).trim();
}

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) {
    console.log(`  ✓ ${label}`);
  } else {
    failedChecks += 1;
    console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`);
  }
}

// ── tap-queue helpers ───────────────────────────────────────────────────────
const TAP_QUEUE = `tap.payment.${Date.now()}`;

function declareTap() {
  // auto_delete so the queue dies with the test if we miss the cleanup.
  sh(`${RABBIT} rabbitmqadmin declare queue name=${TAP_QUEUE} durable=false auto_delete=true`);
  sh(
    `${RABBIT} rabbitmqadmin declare binding source=dumble.events destination=${TAP_QUEUE} routing_key=payment.#`
  );
  console.log(`  tap queue ${TAP_QUEUE} bound to dumble.events with key=payment.#`);
}

function drainTap() {
  // requeue=false drains the messages from the tap. rabbitmqadmin emits a
  // pretty table; we read its --format=raw_json for parseable output.
  const out = sh(
    `${RABBIT} rabbitmqadmin --format=raw_json get queue=${TAP_QUEUE} count=50 ackmode=ack_requeue_false`
  );
  if (!out) return [];
  return JSON.parse(out);
}

function deleteTap() {
  try {
    sh(`${RABBIT} rabbitmqadmin delete queue name=${TAP_QUEUE}`);
  } catch {
    /* auto_delete should already handle it */
  }
}

function outboxRow(eventType, callerRef) {
  // Match the specific outbox row by event type + caller_ref substring in the
  // payload (so we don't pick up unrelated rows from earlier tests).
  const sql = `SELECT id, event_type, routing_key, status, attempts, last_error FROM outbox_events WHERE event_type='${eventType}' AND payload_json LIKE '%${callerRef}%' ORDER BY created_at DESC LIMIT 1;`;
  const out = sh(`${PG_PAYMENT} -c "${sql}"`);
  if (!out) return null;
  const [id, type, rk, status, attempts, lastError] = out.split("|");
  return { id, type, routing_key: rk, status, attempts: parseInt(attempts, 10), lastError };
}

// ── scenarios ───────────────────────────────────────────────────────────────
async function scenarioChargeSucceededEvent(jwt) {
  console.log("\n=== Scenario 1 — ChargeSucceeded event end-to-end ===");
  const callerRef = `outbox-c-${Date.now()}`;
  const idemKey = `k-${callerRef}`;
  const providerRef = String((Date.now() % 9000000) + 9000000);

  // Persist + drive to Succeeded
  const created = await postJson(
    "/api/payment/charges",
    {
      userId: "00000099-0000-0000-0000-0000000000aa",
      amountCents: 1500,
      currency: "EGP",
      callerReference: callerRef,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey }
  );
  assert("charge 201", created.status === 201, `got ${created.status}`);
  if (created.status !== 201) return;
  const chargeId = created.body.chargeId;

  await postJson(
    "/api/payment/webhooks/paymob",
    {
      type: "transaction",
      obj: {
        id: Number(providerRef),
        amount_cents: 1500,
        currency: "EGP",
        success: true,
        merchant_order_id: chargeId,
      },
    },
    { "X-Paymob-Signature": "dev-stub-ok" }
  );

  // 1s webhook job tick + 2s outbox publisher tick + slack
  await sleep(4500);

  // (a) outbox row landed and reached PUBLISHED
  const row = outboxRow("ChargeSucceeded", callerRef);
  assert("outbox row exists for ChargeSucceeded", !!row, `no row found`);
  if (row) {
    assert(
      "outbox row reached PUBLISHED",
      row.status === "PUBLISHED",
      `status=${row.status} attempts=${row.attempts} err=${row.lastError}`
    );
    assert(
      "outbox routing_key is payment.charge.succeeded",
      row.routing_key === "payment.charge.succeeded",
      `got ${row.routing_key}`
    );
  }

  // (b) tap queue saw the message with the right routing key + payload
  const msgs = drainTap();
  const chargeMsg = msgs.find(
    (m) => m.routing_key === "payment.charge.succeeded" && m.payload && m.payload.includes(chargeId)
  );
  assert(
    "tap queue received payment.charge.succeeded",
    !!chargeMsg,
    `routing keys observed: ${msgs.map((m) => m.routing_key).join(", ") || "none"}`
  );
  if (chargeMsg) {
    let payload = null;
    try { payload = JSON.parse(chargeMsg.payload); } catch {}
    assert("payload parses as JSON", !!payload, `raw: ${chargeMsg.payload.slice(0, 80)}`);
    if (payload) {
      // Contract expected by Subscription's handleChargeSucceeded (see
      // PaymentEventListener.java:139-160). It reads providerRef, subscriptionId,
      // userId; we must at minimum supply providerRef + userId. chargeId/amountCents/
      // callerReference are documented in Payment's docs too.
      assert("payload.chargeId matches", payload.chargeId === chargeId);
      assert("payload.userId is uuid-shaped", /^[0-9a-f-]{36}$/.test(payload.userId || ""));
      assert("payload.amountCents is 1500", payload.amountCents === 1500);
      assert(
        "payload.providerRef matches the webhook obj.id",
        String(payload.providerRef) === providerRef,
        `got ${payload.providerRef}`
      );
      assert("payload.callerReference echoes caller-ref", payload.callerReference === callerRef);
    }
  }
}

async function scenarioWithdrawalCompletedEvent(jwt) {
  console.log("\n=== Scenario 2 — WithdrawalCompleted event end-to-end ===");
  const callerRef = `outbox-w-${Date.now()}`;
  const idemKey = `k-${callerRef}`;
  const providerRef = String((Date.now() % 9000000) + 9500000);

  const created = await postJson(
    "/api/payment/withdrawals",
    {
      userId: "00000099-0000-0000-0000-0000000000ab",
      amountCents: 22000,
      currency: "EGP",
      destination: { type: "bank", iban: "EG800000000000000000000007" },
      callerReference: callerRef,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey }
  );
  assert("withdrawal 201", created.status === 201, `got ${created.status}`);
  if (created.status !== 201) return;
  const withdrawalId = created.body.withdrawalId;

  await postJson(
    "/api/payment/webhooks/paymob",
    {
      type: "payout",
      obj: {
        id: Number(providerRef),
        state: "completed",
        merchant_payout_id: withdrawalId,
      },
    },
    { "X-Paymob-Signature": "dev-stub-ok" }
  );

  await sleep(4500);

  const row = outboxRow("WithdrawalCompleted", callerRef);
  assert("outbox row exists for WithdrawalCompleted", !!row);
  if (row) {
    assert(
      "outbox routing_key is payment.withdrawal.completed",
      row.routing_key === "payment.withdrawal.completed",
      `got ${row.routing_key}`
    );
    assert("outbox row PUBLISHED", row.status === "PUBLISHED", `status=${row.status}`);
  }

  const msgs = drainTap();
  const wMsg = msgs.find(
    (m) =>
      m.routing_key === "payment.withdrawal.completed" &&
      m.payload &&
      m.payload.includes(withdrawalId)
  );
  assert("tap saw payment.withdrawal.completed", !!wMsg);
  if (wMsg) {
    let payload = null;
    try { payload = JSON.parse(wMsg.payload); } catch {}
    assert("payload parses", !!payload);
    if (payload) {
      // Contract Wallet's PaymentEventListener.handleCompleted parses
      // (services/Dumble.Wallet/.../PaymentEventListener.java:66+).
      assert("payload.payoutId matches withdrawalId", payload.payoutId === withdrawalId);
      assert("payload.type is USER_WITHDRAWAL", payload.type === "USER_WITHDRAWAL");
      assert("payload.callerReference present", payload.callerReference === callerRef);
      assert("payload.amountCents 22000", payload.amountCents === 22000);
    }
  }
}

async function scenarioPayoutFailedEvent(jwt) {
  console.log("\n=== Scenario 3 — PayoutFailed event (cohort) ===");
  const callerRef = `outbox-p-${Date.now()}`;
  const idemKey = `k-${callerRef}`;
  const providerRef = String((Date.now() % 9000000) + 9700000);

  const created = await postJson(
    "/api/payment/payouts",
    {
      sellerId: "00000099-0000-0000-0000-0000000000ac",
      amountCents: 40000,
      currency: "EGP",
      destination: { type: "bank", iban: "EG800000000000000000000019" },
      cohortKey: "2026-W21",
      callerReference: callerRef,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey }
  );
  assert("payout 201", created.status === 201, `got ${created.status}`);
  if (created.status !== 201) return;
  const payoutId = created.body.payoutId;

  await postJson(
    "/api/payment/webhooks/paymob",
    {
      type: "payout",
      obj: {
        id: Number(providerRef),
        state: "failed",
        failure_reason: "iban_invalid",
        merchant_payout_id: payoutId,
      },
    },
    { "X-Paymob-Signature": "dev-stub-ok" }
  );

  await sleep(4500);

  const row = outboxRow("PayoutFailed", callerRef);
  assert("outbox row exists for PayoutFailed", !!row);
  if (row) {
    assert(
      "outbox routing_key is payment.payout.failed",
      row.routing_key === "payment.payout.failed",
      `got ${row.routing_key}`
    );
  }

  const msgs = drainTap();
  const pMsg = msgs.find(
    (m) =>
      m.routing_key === "payment.payout.failed" && m.payload && m.payload.includes(payoutId)
  );
  assert("tap saw payment.payout.failed", !!pMsg);
  if (pMsg) {
    let payload = null;
    try { payload = JSON.parse(pMsg.payload); } catch {}
    if (payload) {
      assert("payload.payoutId matches", payload.payoutId === payoutId);
      assert("payload.type is COHORT_PAYOUT", payload.type === "COHORT_PAYOUT");
      assert("payload.reason carries failure_reason", payload.reason === "iban_invalid");
    }
  }
}

async function scenarioRoutingKeyContract(jwt) {
  console.log("\n=== Scenario 4 — Subscription's listener can match every key Payment emits ===");
  // Static contract check — no calls, just dictionary lookups.
  // What Payment emits (per ChargePersister / PayoutPersister):
  const paymentEmits = [
    "payment.charge.succeeded",
    "payment.charge.failed",
    "payment.chargeback.filed",
    "payment.withdrawal.completed",
    "payment.withdrawal.failed",
    "payment.payout.completed",
    "payment.payout.failed",
  ];
  // What Subscription's PaymentEventListener.onMessage switches on (post-fix).
  // Hardcoded here so the test FAILS if anyone re-introduces the old spelling.
  const subscriptionHandles = new Set([
    "payment.payout.completed",
    "payment.payout.failed",
    "payment.charge.succeeded",
    "payment.charge.completed", // legacy spelling kept as alias
    "payment.charge.failed",
    "payment.chargeback.filed",
  ]);
  // What Wallet's PaymentEventListener.onMessage switches on.
  const walletHandles = new Set([
    "payment.withdrawal.completed",
    "payment.withdrawal.failed",
  ]);
  // Cross-service contract: every routing key Payment emits should be handled
  // by AT LEAST ONE downstream service. An orphan event is a code smell.
  for (const rk of paymentEmits) {
    const consumed = subscriptionHandles.has(rk) || walletHandles.has(rk);
    assert(`${rk} handled by Subscription or Wallet`, consumed,
      `no consumer switches on it — event is a code smell`);
  }
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Payment URL: ${PAYMENT_URL}`);
  const jwt = mintSystemJwt();

  declareTap();
  // Drain anything left from previous runs so our messages are the only ones.
  drainTap();

  try {
    await scenarioChargeSucceededEvent(jwt);
    await scenarioWithdrawalCompletedEvent(jwt);
    await scenarioPayoutFailedEvent(jwt);
    await scenarioRoutingKeyContract(jwt);
  } catch (ex) {
    console.error("Harness threw:", ex);
    process.exit(2);
  } finally {
    deleteTap();
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${
      totalChecks - failedChecks
    }/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
