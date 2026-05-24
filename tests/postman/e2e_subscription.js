// Cross-service E2E for Subscription's PRO upgrade lifecycle.
//
// Drives the full chain through real Subscription + real Payment + real
// RabbitMQ — no event injection on Subscription's inbound side. Proves the
// contract drift across the boundary holds: the callerReference Subscription
// hands Payment is the same key Payment puts on the outbox event, and
// Subscription's PaymentEventListener resolves it to the original local row.
//
// Sequence:
//   1. POST /me/plan/upgrade (user → Subscription)
//        — Subscription writes PlatformSubscription PENDING + outbox event
//        — Subscription calls POST /api/payment/charges (system → Payment)
//        — Payment writes Charge PENDING, returns its id (= providerRef)
//        — Subscription stores providerRef + returns 200/201 to user
//   2. POST /api/payment/webhooks/paymob with HMAC=dev-stub-ok and obj.id =
//      Payment's charge id, success=true
//        — Payment marks Charge SUCCEEDED, publishes payment.charge.succeeded
//        — Subscription's PaymentEventListener consumes, flips PlatformSub
//          PENDING → ACTIVE
//   3. Assert Subscription's plan via GET /me/plan reports ACTIVE
//
// Run: node tests/postman/e2e_subscription.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const PAYMENT_URL      = process.env.PAYMENT_HOST_URL      || "http://localhost:18183";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

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
const PARTICIPANT = `00000099-0000-0000-${stamp.toString(16).padStart(16, "0").slice(-4)}-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const userJwt = jwt(
  { sub: `e2e-${stamp}@dumble.test`, userId: PARTICIPANT, displayName: "E2E",
    userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
  userKey
);

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) console.log(`  ✓ ${label}`);
  else { failedChecks += 1; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
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
function psql(db, sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", db, "psql",
    "-U", "postgres", "-d", db === "subscription-db" ? "dumble_subscription" : "dumble_payment",
    "-tA", "-c", sql], { encoding: "utf8" });
  return { code: r.status, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}
async function waitUntil(label, pred, timeoutMs = 20000, intervalMs = 500) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    if (await pred()) return true;
    await sleep(intervalMs);
  }
  return false;
}

(async () => {
  console.log(`Subscription: ${SUBSCRIPTION_URL}`);
  console.log(`Payment:      ${PAYMENT_URL}`);
  console.log(`Test user:    ${PARTICIPANT.slice(-12)}`);

  // ─── Step 1: PRO upgrade ────────────────────────────────────────────────
  console.log("\n=== Step 1 — POST /me/plan/upgrade (user → Subscription → Payment) ===");
  const upRes = await postJson(
    `${SUBSCRIPTION_URL}/api/me/plan/upgrade`,
    { paymentMethodToken: `tok-e2e-${stamp}`, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-e2e-up-${stamp}` }
  );
  assert(`upgrade 200/201`, upRes.status === 200 || upRes.status === 201,
    `status=${upRes.status} body=${JSON.stringify(upRes.body).slice(0,200)}`);
  const initialStatus = upRes.body?.status;
  console.log(`  status=${initialStatus}`);
  assert(`status PENDING`, initialStatus === "PENDING", `got ${initialStatus}`);

  // MyPlanResponse doesn't include the providerRef. To drive the webhook we
  // need Payment's charge.caller_reference (which Subscription set to its
  // own platform_subscriptions.id). Query Payment's DB directly to grab it.
  await sleep(500);
  const r = psql("payment-db",
    `SELECT caller_reference FROM charges WHERE user_id = '${PARTICIPANT}' ORDER BY created_at DESC LIMIT 1;`);
  const callerRef = r.stdout;
  assert(`Payment recorded a charge for the test user`,
    r.code === 0 && callerRef.length > 0,
    `psql code=${r.code} stdout='${callerRef}' stderr='${r.stderr.split('\\n')[0]}'`);

  // ─── Step 2: simulate Paymob webhook confirming the charge ─────────────
  console.log("\n=== Step 2 — POST /payment/webhooks/paymob (Paymob → Payment) ===");
  console.log(`  using merchant_order_id = ${callerRef} (Payment's charge.caller_reference)`);

  const webhookPayload = {
    type: "transaction",
    obj: {
      id: 990000 + (stamp % 1000),
      success: true,
      amount_cents: 1000,
      currency: "EGP",
      merchant_order_id: callerRef,
    },
  };
  const wh = await postJson(
    `${PAYMENT_URL}/api/payment/webhooks/paymob`,
    webhookPayload,
    { "X-Paymob-Signature": "dev-stub-ok" }
  );
  assert(`Payment ACKs webhook`, wh.status === 200, `got ${wh.status}`);

  // ─── Step 3: wait for Subscription to receive payment.charge.succeeded ──
  console.log("\n=== Step 3 — wait for Subscription to flip PENDING → ACTIVE ===");
  const activated = await waitUntil(
    "Subscription's plan becomes ACTIVE",
    async () => {
      const r = await getJson(`${SUBSCRIPTION_URL}/api/me/plan`, { Authorization: `Bearer ${userJwt}` });
      return r.status === 200 && r.body && r.body.status === "ACTIVE";
    },
    25000
  );
  assert(`PRO subscription flipped to ACTIVE within 25 s`, activated);

  // ─── Step 4: verify entitlements reflect ACTIVE PRO ─────────────────────
  console.log("\n=== Step 4 — entitlements reflect ACTIVE PRO ===");
  const ent = await getJson(`${SUBSCRIPTION_URL}/api/me/entitlements`,
    { Authorization: `Bearer ${userJwt}` });
  assert(`entitlements 200`, ent.status === 200);
  assert(`canUseChatbot true (PRO)`, ent.body?.canUseChatbot === true,
    `canUseChatbot=${ent.body?.canUseChatbot}`);
  // canDmAnyone depends on plan config; check it's a boolean either way
  assert(`canDmAnyone is boolean`, typeof ent.body?.canDmAnyone === "boolean",
    `canDmAnyone=${ent.body?.canDmAnyone}`);

  console.log(`\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`);
  process.exit(failedChecks === 0 ? 0 : 1);
})().catch((ex) => { console.error("E2E threw:", ex); process.exit(2); });
