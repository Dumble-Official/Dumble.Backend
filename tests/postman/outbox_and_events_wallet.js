// Outbox + cross-service event verification for Wallet.
//
// Two things to prove:
//   1. Wallet emits events on the dumble.events topic exchange whose routing
//      keys + payload shapes match what downstream consumers (Subscription,
//      Notification) expect.
//   2. Wallet correctly consumes events from Payment — payment.withdrawal.
//      completed flips the local WithdrawalRequest row from SENT → COMPLETED
//      and decrements Pending; payment.withdrawal.failed reverses the wallet
//      movement.
//
// The harness binds a tap queue to dumble.events with binding key `wallet.#`
// via the RabbitMQ management HTTP API, drives Wallet via HTTP, then drains
// the tap queue to inspect the routing keys + payload shapes. For the inbound
// side we publish a payment.withdrawal.* event directly to dumble.events with
// the right shape and assert Wallet's state moves.
//
// Run: node tests/postman/outbox_and_events_wallet.js

const crypto = require("crypto");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const RMQ_BASE = process.env.RMQ_BASE || "http://localhost:15672";
const RMQ_AUTH = "Basic " + Buffer.from("guest:guest").toString("base64");
const EXCHANGE = "dumble.events";
const TAP_QUEUE = "wallet-qa-tap-" + Date.now();

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

const stamp = Date.now();
const TEST_USER = `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const systemJwt = jwt({ iss: "outbox-tap", aud: "wallet", iat: now, exp }, systemKey);
const userJwt = jwt(
  { sub: "tap@dumble.test", userId: TEST_USER, displayName: "Tap", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
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

async function rmq(path, init = {}) {
  const res = await fetch(`${RMQ_BASE}/api${path}`, {
    ...init,
    headers: { Authorization: RMQ_AUTH, "Content-Type": "application/json", ...(init.headers || {}) },
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`RMQ ${init.method || "GET"} ${path} → ${res.status}: ${await res.text()}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

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

async function getJson(path, headers) {
  const res = await fetch(`${WALLET_URL}${path}`, { headers });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

// ── tap-queue lifecycle ─────────────────────────────────────────────────────
async function setupTap() {
  // Declare durable queue and bind to dumble.events with wallet.# topic key.
  await rmq(`/queues/%2F/${TAP_QUEUE}`, {
    method: "PUT",
    body: JSON.stringify({ auto_delete: false, durable: true, arguments: {} }),
  });
  await rmq(`/bindings/%2F/e/${EXCHANGE}/q/${TAP_QUEUE}`, {
    method: "POST",
    body: JSON.stringify({ routing_key: "wallet.#", arguments: {} }),
  });
  console.log(`  tap queue: ${TAP_QUEUE} bound to ${EXCHANGE} with key wallet.#`);
}
async function drainTap() {
  // get_requeue=false consumes; ackmode=ack_requeue_false acks and removes.
  const msgs = await rmq(`/queues/%2F/${TAP_QUEUE}/get`, {
    method: "POST",
    body: JSON.stringify({ count: 100, ackmode: "ack_requeue_false", encoding: "auto", truncate: 50000 }),
  });
  return msgs || [];
}
async function teardownTap() {
  try { await rmq(`/queues/%2F/${TAP_QUEUE}`, { method: "DELETE" }); } catch {}
}

// ── waitUntil: poll a predicate up to N ms ──────────────────────────────────
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitUntil(label, pred, timeoutMs = 8000, intervalMs = 250) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await pred()) return true;
    await sleep(intervalMs);
  }
  return false;
}

// ── inbound: publish a payment.withdrawal.* event into dumble.events ────────
async function publishToExchange(routingKey, payload) {
  // NOTE: do NOT set content_type=application/json here. Wallet's @RabbitListener
  // signature is `onMessage(String body, ...)` and Spring AMQP's Jackson2
  // converter sees application/json and tries to deserialize the JSON object
  // into a String — which fails with "cannot deserialize JSON object to
  // String". Real Payment publishes from OutboxPublisher use a custom converter
  // path that round-trips correctly; for the test harness we just send the
  // bytes as text/plain so the converter passes them through verbatim.
  await rmq(`/exchanges/%2F/${EXCHANGE}/publish`, {
    method: "POST",
    body: JSON.stringify({
      properties: { content_type: "text/plain", correlation_id: crypto.randomUUID() },
      routing_key: routingKey,
      payload: JSON.stringify(payload),
      payload_encoding: "string",
    }),
  });
}

// ── scenario 1: outbox publishes the right routing keys + shapes ────────────
async function outboxEmissions() {
  console.log("\n=== Scenario 1 — Wallet emits wallet.credited / wallet.debited / wallet.withdrawal.requested ===");

  // Credit.
  const creditRes = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 25000, source: "BAN_REFUND", externalRef: `tap-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-tap-credit-${stamp}` }
  );
  assert(`credit 201`, creditRes.status === 201, `got ${creditRes.status}`);

  // Debit.
  const debitRes = await postJson(
    "/api/wallet/debit",
    { userId: TEST_USER, amountCents: 1000, source: "IN_APP_SPEND", externalRef: `tap-d-${stamp}` },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-tap-debit-${stamp}` }
  );
  assert(`debit 201`, debitRes.status === 201, `got ${debitRes.status}`);

  // Withdrawal request.
  const wRes = await postJson(
    "/api/wallet/me/withdrawals",
    { amountCents: 10000, destination: { type: "bank", iban: "EG800000000000000000000099" } },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-tap-w-${stamp}` }
  );
  assert(`withdrawal 201`, wRes.status === 201, `got ${wRes.status}`);
  const withdrawalId = wRes.body?.id;

  // Let the outbox publisher tick and broker route to the tap queue.
  await sleep(4000);

  const msgs = await drainTap();
  const byKey = {};
  for (const m of msgs) {
    byKey[m.routing_key] = (byKey[m.routing_key] || 0) + 1;
  }
  console.log(`  drained ${msgs.length} messages from tap queue`);
  console.log(`  routing keys observed: ${JSON.stringify(byKey)}`);

  assert(`saw wallet.credited`, (byKey["wallet.credited"] || 0) >= 1, JSON.stringify(byKey));
  assert(`saw wallet.debited`, (byKey["wallet.debited"] || 0) >= 1, JSON.stringify(byKey));
  assert(
    `saw wallet.withdrawal.requested`,
    (byKey["wallet.withdrawal.requested"] || 0) >= 1,
    JSON.stringify(byKey)
  );

  // Payload-shape spot checks on the wallet.credited message.
  const credited = msgs.find((m) => m.routing_key === "wallet.credited");
  if (credited) {
    let payload;
    try { payload = JSON.parse(credited.payload); } catch { payload = {}; }
    assert(`wallet.credited has walletEntryId`, typeof payload.walletEntryId === "string", JSON.stringify(payload).slice(0, 200));
    assert(`wallet.credited has userId`, typeof payload.userId === "string", JSON.stringify(payload).slice(0, 200));
    assert(`wallet.credited has amountCents (number)`, typeof payload.amountCents === "number", JSON.stringify(payload).slice(0, 200));
    assert(`wallet.credited has source`, typeof payload.source === "string", JSON.stringify(payload).slice(0, 200));
    assert(`wallet.credited has newBalanceCents`, typeof payload.newBalanceCents === "number", JSON.stringify(payload).slice(0, 200));
  } else {
    failedChecks += 5;
    totalChecks += 5;
    console.log("  ✗ wallet.credited payload checks (no message found)");
  }

  // Same for wallet.withdrawal.requested — must carry withdrawalId + userId + amountCents.
  const wreq = msgs.find((m) => m.routing_key === "wallet.withdrawal.requested");
  if (wreq) {
    let payload;
    try { payload = JSON.parse(wreq.payload); } catch { payload = {}; }
    assert(`wallet.withdrawal.requested has withdrawalId`, typeof payload.withdrawalId === "string", JSON.stringify(payload).slice(0, 200));
    assert(`wallet.withdrawal.requested has userId`, typeof payload.userId === "string");
    assert(`wallet.withdrawal.requested has amountCents (number)`, typeof payload.amountCents === "number");
    assert(`wallet.withdrawal.requested.withdrawalId == HTTP response id`, payload.withdrawalId === withdrawalId, `payload=${payload.withdrawalId} http=${withdrawalId}`);
  } else {
    failedChecks += 4;
    totalChecks += 4;
    console.log("  ✗ wallet.withdrawal.requested payload checks (no message found)");
  }

  return { withdrawalId };
}

// ── scenario 2: inbound event flips withdrawal status ───────────────────────
async function inboundWithdrawalCompleted(withdrawalId) {
  console.log("\n=== Scenario 2 — payment.withdrawal.completed flips Wallet's row to COMPLETED ===");
  if (!withdrawalId) {
    failedChecks += 1; totalChecks += 1;
    console.log("  ✗ no withdrawalId from scenario 1; skipping");
    return;
  }

  // Publish a payment.withdrawal.completed event into dumble.events with
  // the shape Wallet's PaymentEventListener expects (callerReference =
  // Wallet's withdrawal id, eventId required for dedup).
  const eventId = `qa-evt-${stamp}-completed`;
  await publishToExchange("payment.withdrawal.completed", {
    eventId,
    type: "USER_WITHDRAWAL",
    payoutId: `payment-payout-${stamp}`,
    callerReference: withdrawalId,
    subjectId: TEST_USER,
    amountCents: 10000,
    providerRef: `paymob-${stamp}`,
  });

  // Wait for Wallet to consume and persist.
  const ok = await waitUntil("withdrawal flips to COMPLETED", async () => {
    const r = await getJson("/api/wallet/me/withdrawals", { Authorization: `Bearer ${userJwt}` });
    if (r.status !== 200) return false;
    const row = (r.body || []).find((x) => x.id === withdrawalId);
    return row && row.status === "COMPLETED";
  });
  assert(`Wallet flipped withdrawal ${withdrawalId.slice(-12)} to COMPLETED within 8 s`, ok);

  // Re-publish the same event — dedup must short-circuit.
  await publishToExchange("payment.withdrawal.completed", {
    eventId,
    type: "USER_WITHDRAWAL",
    payoutId: `payment-payout-${stamp}`,
    callerReference: withdrawalId,
    subjectId: TEST_USER,
    amountCents: 10000,
    providerRef: `paymob-${stamp}`,
  });
  await sleep(1500);
  // Status should still be COMPLETED (terminal absorbing), no double-handling.
  const after = await getJson("/api/wallet/me/withdrawals", { Authorization: `Bearer ${userJwt}` });
  const stillCompleted = (after.body || []).find((x) => x.id === withdrawalId)?.status === "COMPLETED";
  assert(`re-delivered event did not perturb state (still COMPLETED)`, stillCompleted);
}

// ── scenario 3: inbound failed event reverses the debit ─────────────────────
async function inboundWithdrawalFailed() {
  console.log("\n=== Scenario 3 — payment.withdrawal.failed reverses the wallet debit ===");

  // Set up a fresh withdrawal we can fail.
  const setupRes = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 50000, source: "BAN_REFUND", externalRef: `setup-${stamp}-fail` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-setup-fail-${stamp}` }
  );
  assert(`setup credit 201`, setupRes.status === 201);

  const balanceBefore = (await getJson(`/api/wallet/${TEST_USER}/summary`, { Authorization: `Bearer ${systemJwt}` })).body?.availableCents ?? 0;

  const wRes = await postJson(
    "/api/wallet/me/withdrawals",
    { amountCents: 20000, destination: { type: "bank", iban: "EG800000000000000000000099" } },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-w-fail-${stamp}` }
  );
  assert(`withdrawal 201`, wRes.status === 201);
  const withdrawalId = wRes.body?.id;

  const balanceMid = (await getJson(`/api/wallet/${TEST_USER}/summary`, { Authorization: `Bearer ${systemJwt}` })).body?.availableCents ?? 0;
  assert(`balance dropped by 20000 after withdrawal`, balanceBefore - balanceMid === 20000, `before=${balanceBefore} mid=${balanceMid}`);

  // Now publish failure.
  await publishToExchange("payment.withdrawal.failed", {
    eventId: `qa-evt-${stamp}-failed`,
    type: "USER_WITHDRAWAL",
    payoutId: `payment-payout-fail-${stamp}`,
    callerReference: withdrawalId,
    subjectId: TEST_USER,
    amountCents: 20000,
    reason: "bank_declined",
  });

  const reversed = await waitUntil("withdrawal flips to FAILED and balance restored", async () => {
    const r = await getJson("/api/wallet/me/withdrawals", { Authorization: `Bearer ${userJwt}` });
    if (r.status !== 200) return false;
    const row = (r.body || []).find((x) => x.id === withdrawalId);
    if (!row || row.status !== "FAILED") return false;
    const bal = (await getJson(`/api/wallet/${TEST_USER}/summary`, { Authorization: `Bearer ${systemJwt}` })).body?.availableCents ?? 0;
    return bal === balanceBefore;
  });
  assert(`withdrawal flipped to FAILED + wallet balance restored within 8 s`, reversed);

  const balanceAfter = (await getJson(`/api/wallet/${TEST_USER}/summary`, { Authorization: `Bearer ${systemJwt}` })).body?.availableCents ?? 0;
  assert(`balance equals pre-withdrawal value`, balanceAfter === balanceBefore, `before=${balanceBefore} after=${balanceAfter}`);
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Wallet URL: ${WALLET_URL}`);
  console.log(`Rabbit API: ${RMQ_BASE}`);
  console.log(`Test user:  ${TEST_USER}`);

  await setupTap();
  try {
    const { withdrawalId } = await outboxEmissions();
    await inboundWithdrawalCompleted(withdrawalId);
    await inboundWithdrawalFailed();
  } catch (ex) {
    console.error("Harness threw:", ex);
    failedChecks += 1; totalChecks += 1;
  } finally {
    await teardownTap();
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${
      totalChecks - failedChecks
    }/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
