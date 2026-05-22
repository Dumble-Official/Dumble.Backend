// Concurrency harness for Payment that the Postman runner can't express: fires
// N parallel HTTP requests against the live service and asserts the resulting
// distribution. Two scenarios:
//
//   1. Idempotency race — N parallel POST /charges with the SAME Idempotency-Key
//      and SAME body. Expect exactly 1 row in the DB (one distinct chargeId on
//      the wire), and every response either 201 (the winner), 200 (cached
//      replay), or 409 (concurrent in-flight).
//
//   2. Over-refund race — fund a SUCCEEDED charge of N cents, then fire M
//      parallel refunds whose sum greatly exceeds N. The pessimistic
//      FOR-UPDATE lock + RefundPersister's sum check must prevent the total
//      refunded from exceeding the parent amount: count(201) * partAmount must
//      be ≤ N, and the rest must be 422.
//
// Run: node tests/postman/concurrency_payment.js
// Env knobs:
//   PAYMENT_HOST_URL  default http://localhost:18183
//   SIGNING_KEY       defaults to the dev key from release/.env

const crypto = require("crypto");

const PAYMENT_URL = process.env.PAYMENT_HOST_URL || "http://localhost:18183";
const SIGNING_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";

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

// ── runtime helpers ─────────────────────────────────────────────────────────
async function postJson(path, body, headers) {
  const res = await fetch(`${PAYMENT_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try {
    parsed = await res.json();
  } catch {
    /* non-JSON error body — leave as null */
  }
  return { status: res.status, body: parsed };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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

// ── scenario 1: idempotency race ────────────────────────────────────────────
async function idempotencyRace(jwt, N = 12) {
  console.log(`\n=== Scenario 1 — ${N} parallel charges, SAME Idempotency-Key ===`);
  const idemKey = `k-race-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const body = {
    userId: "00000099-0000-0000-0000-0000000000a0",
    amountCents: 5000,
    currency: "EGP",
    description: "concurrency race",
    callerReference: idemKey,
  };
  const headers = { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey };

  const t0 = Date.now();
  const results = await Promise.all(
    Array.from({ length: N }, () => postJson("/api/payment/charges", body, headers))
  );
  const elapsed = Date.now() - t0;
  console.log(`  ${N} requests landed in ${elapsed}ms`);

  const byStatus = {};
  const chargeIds = new Set();
  for (const r of results) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.body && r.body.chargeId) chargeIds.add(r.body.chargeId);
  }
  console.log("  status distribution:", JSON.stringify(byStatus));
  console.log("  distinct chargeIds:  ", chargeIds.size);

  const ok201 = (byStatus[201] || 0) >= 1;
  const okNo500 = !Object.keys(byStatus).some((s) => Number(s) >= 500);
  const okOneRow = chargeIds.size === 1;
  const okAllAccountedFor = Object.entries(byStatus).every(
    ([s, n]) => ["200", "201", "409"].includes(s)
  );

  assert(`at least one 201 winner`, ok201, `status map: ${JSON.stringify(byStatus)}`);
  assert(`no 5xx`, okNo500, `status map: ${JSON.stringify(byStatus)}`);
  assert(`exactly one chargeId materialized (no double-spend)`, okOneRow,
    `distinct=${chargeIds.size}, ids=${[...chargeIds].slice(0, 3).join(",")}`);
  assert(`all responses are 201/200/409 (no surprise codes)`, okAllAccountedFor,
    `status map: ${JSON.stringify(byStatus)}`);

  return [...chargeIds][0];
}

// ── scenario 2: over-refund race ────────────────────────────────────────────
async function overRefundRace(jwt) {
  console.log("\n=== Scenario 2 — parallel refunds against one Succeeded charge ===");

  // Step 2a — create a charge and drive it to SUCCEEDED via webhook.
  const idemKey = `k-orr-${Date.now()}`;
  const callerRef = `orr-${Date.now()}`;
  const chargeAmount = 10000;
  const createRes = await postJson(
    "/api/payment/charges",
    {
      userId: "00000099-0000-0000-0000-0000000000a2",
      amountCents: chargeAmount,
      currency: "EGP",
      description: "over-refund race",
      callerReference: callerRef,
    },
    { Authorization: `Bearer ${jwt}`, "Idempotency-Key": idemKey }
  );
  assert(`setup charge 201`, createRes.status === 201, `got ${createRes.status}`);
  if (createRes.status !== 201) return;
  const chargeId = createRes.body.chargeId;

  const providerRef = String((Date.now() % 9000000) + 8000000);
  const ackRes = await postJson(
    "/api/payment/webhooks/paymob",
    {
      type: "transaction",
      obj: {
        id: Number(providerRef),
        amount_cents: chargeAmount,
        currency: "EGP",
        success: true,
        merchant_order_id: chargeId,
      },
    },
    { "X-Paymob-Signature": "dev-stub-ok" }
  );
  assert(`webhook 200 ACK`, ackRes.status === 200, `got ${ackRes.status}`);

  // Wait for the async processor to flip to SUCCEEDED.
  await sleep(2200);
  const verifyRes = await fetch(`${PAYMENT_URL}/api/payment/charges/${chargeId}`, {
    headers: { Authorization: `Bearer ${jwt}` },
  });
  const verifyBody = await verifyRes.json();
  assert(`charge transitioned to Succeeded`, verifyBody.status === "Succeeded",
    `got ${verifyBody.status}`);
  if (verifyBody.status !== "Succeeded") return;

  // Step 2b — fire 8 parallel refunds, each 2500 cents → total 20000 cents on a
  // 10000-cent charge. At most 4 may succeed (4 × 2500 = 10000).
  const refundAmount = 2500;
  const N = 8;
  const refundReqs = Array.from({ length: N }, (_, i) =>
    postJson(
      "/api/payment/refunds",
      {
        chargeId,
        amountCents: refundAmount,
        destination: "WALLET",
        reason: `parallel refund #${i}`,
      },
      {
        Authorization: `Bearer ${jwt}`,
        "Idempotency-Key": `k-orr-refund-${Date.now()}-${i}`,
      }
    )
  );

  const t0 = Date.now();
  const refundResults = await Promise.all(refundReqs);
  const elapsed = Date.now() - t0;
  console.log(`  ${N} parallel refunds landed in ${elapsed}ms`);

  const byStatus = {};
  let successfulRefundCents = 0;
  for (const r of refundResults) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.status === 201 && r.body && r.body.status === "Succeeded") {
      successfulRefundCents += r.body.amountCents;
    }
  }
  console.log("  status distribution:", JSON.stringify(byStatus));
  console.log(`  total refunded: ${successfulRefundCents} of ${chargeAmount} cents`);

  const maxAllowed = chargeAmount;
  const okNoOverRefund = successfulRefundCents <= maxAllowed;
  const okNo5xx = !Object.keys(byStatus).some((s) => Number(s) >= 500);
  const successCount = byStatus[201] || 0;
  const okExpectedSplit = successCount * refundAmount <= chargeAmount;
  const failureCount = (byStatus[422] || 0) + (byStatus[409] || 0);
  const okEveryoneAccounted = successCount + failureCount === N;

  assert(`total refunded ≤ charge amount (FOR UPDATE lock holds)`,
    okNoOverRefund, `refunded=${successfulRefundCents} > charge=${chargeAmount}`);
  assert(`successful refunds × amount ≤ charge`,
    okExpectedSplit, `${successCount}*${refundAmount} > ${chargeAmount}`);
  assert(`no 5xx`, okNo5xx, `status map: ${JSON.stringify(byStatus)}`);
  assert(`every response either 201 or 422/409`,
    okEveryoneAccounted, `success=${successCount} fail=${failureCount} of N=${N}`);
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Payment URL: ${PAYMENT_URL}`);
  const jwt = mintSystemJwt();

  try {
    await idempotencyRace(jwt, 12);
    await overRefundRace(jwt);
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
