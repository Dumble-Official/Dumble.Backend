// Security probes + observability checks for Payment.
//
// Combines two related concerns that share infra: adversarial JWT / HMAC
// / header probes, and a sanity check on actuator + audit-log surfaces.
//
// Run: node tests/postman/security_and_observability.js

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

function mintJwt(claims, signingKey = key) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const now = () => Math.floor(Date.now() / 1000);

async function req(path, opts = {}) {
  const res = await fetch(`${PAYMENT_URL}${path}`, opts);
  let body = null;
  try { body = await res.json(); } catch {}
  return { status: res.status, body, headers: res.headers };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function sh(cmd) { return execSync(cmd, { encoding: "utf8" }).trim(); }

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

// ── Security probes ─────────────────────────────────────────────────────────
async function securityJwtMissingExp() {
  console.log("\n=== Security 1 — JWT without exp claim ===");
  // jjwt allows tokens without exp by default — Payment relies on the
  // ServiceTokenVerifier to enforce expiry. If a no-exp token is accepted,
  // a leaked token never goes stale.
  const token = mintJwt({ iss: "payment-service", aud: "payment", iat: now() });
  const r = await req("/api/payment/charges/00000000-0000-0000-0000-000000000000", {
    headers: { Authorization: `Bearer ${token}` },
  });
  // Either 401 (refused) or 404 (accepted, no such charge). 404 = token was
  // valid in the verifier's eyes, which is a soft finding (no exp enforcement).
  if (r.status === 401) {
    assert("JWT without exp is refused (401)", true);
  } else {
    assert(`JWT without exp gets ${r.status} (verifier doesn't enforce exp presence — finding)`,
      false, `expected 401, got ${r.status}`);
  }
}

async function securityJwtFutureIat() {
  console.log("\n=== Security 2 — JWT with iat far in the future ===");
  // No clock-skew tolerance in jjwt by default → a token with iat=now+1h should
  // be allowed because exp validation is what matters; iat is informational.
  // Payment doesn't enforce iat-not-in-future. Documented behavior; verify.
  const token = mintJwt({
    iss: "payment-service", aud: "payment",
    iat: now() + 3600, exp: now() + 7200,
  });
  const r = await req("/api/payment/charges/00000000-0000-0000-0000-000000000000", {
    headers: { Authorization: `Bearer ${token}` },
  });
  // Expectation: either Payment doesn't care about iat (404 = "ok, charge not
  // found") or it explicitly rejects (401). Both are sane; record actual.
  assert(`response for future-iat token: ${r.status}`,
    r.status === 401 || r.status === 404,
    `got ${r.status}`);
}

async function securityJwtWrongAudience() {
  console.log("\n=== Security 3 — JWT with wrong audience ===");
  const token = mintJwt({
    iss: "payment-service", aud: "wallet",  // wrong service
    iat: now(), exp: now() + 3600,
  });
  const r = await req("/api/payment/charges/00000000-0000-0000-0000-000000000000", {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert("aud != payment is rejected with 401", r.status === 401, `got ${r.status}`);
}

async function securityIdemKeyCrlf() {
  console.log("\n=== Security 4 — CRLF / header injection in Idempotency-Key ===");
  const token = mintJwt({
    iss: "payment-service", aud: "payment", iat: now(), exp: now() + 3600,
  });
  // fetch() rejects \r\n in header values per spec — verify the client
  // can't even send the request, OR if it can, Payment rejects it.
  let networkError = false;
  let status = null;
  try {
    const r = await req("/api/payment/charges", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": "k-crlf\r\nX-Injected: evil",
      },
      body: JSON.stringify({
        userId: "00000099-0000-0000-0000-000000000099",
        amountCents: 100,
        currency: "EGP",
        callerReference: "crlf-probe",
      }),
    });
    status = r.status;
  } catch (ex) {
    networkError = true;
  }
  assert(
    "CRLF in Idempotency-Key blocked (client refused or server 4xx)",
    networkError || (status && status >= 400 && status < 500),
    `networkError=${networkError} status=${status}`
  );
}

async function securityIdemKeyOverlong() {
  console.log("\n=== Security 5 — Overlong Idempotency-Key past 128 chars ===");
  const token = mintJwt({
    iss: "payment-service", aud: "payment", iat: now(), exp: now() + 3600,
  });
  const longKey = "k-" + "x".repeat(200);
  const r = await req("/api/payment/charges", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "Idempotency-Key": longKey,
    },
    body: JSON.stringify({
      userId: "00000099-0000-0000-0000-000000000099",
      amountCents: 100,
      currency: "EGP",
      callerReference: "overlong-probe",
    }),
  });
  assert("Overlong key rejected with 400", r.status === 400, `got ${r.status}`);
}

async function securityHmacAlternateHeaderCase() {
  console.log("\n=== Security 6 — HMAC header case sensitivity ===");
  // Webhook controller accepts both X-Paymob-Signature and hmac (lowercase).
  // HTTP headers are case-insensitive — verify Tomcat normalizes.
  const r = await req("/api/payment/webhooks/paymob", {
    method: "POST",
    headers: {
      "x-paymob-signature": "dev-stub-ok",   // intentionally lowercase
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      type: "transaction",
      obj: {
        id: (Date.now() % 9000000) + 12000000,
        success: true,
        merchant_order_id: "00000000-0000-0000-0000-000000000000",  // unknown charge
      },
    }),
  });
  assert("lowercase x-paymob-signature accepted (200)", r.status === 200, `got ${r.status}`);
}

async function securityErrorBodyShape() {
  console.log("\n=== Security 7 — Error responses don't echo attacker input ===");
  const token = mintJwt({
    iss: "payment-service", aud: "payment", iat: now(), exp: now() + 3600,
  });
  // Send a payload with a script-like description; assert the error response
  // doesn't reflect that string verbatim (e.g., via a server-side echo).
  const attacker = "<script>alert(1)</script>'\"--";
  const r = await req("/api/payment/refunds", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "Idempotency-Key": `k-xss-${Date.now()}`,
    },
    body: JSON.stringify({
      chargeId: "00000000-0000-0000-0000-000000000000",
      amountCents: 100,
      destination: "WALLET",
      reason: attacker,
    }),
  });
  assert("status is 4xx (not 500)", r.status >= 400 && r.status < 500, `got ${r.status}`);
  const bodyStr = JSON.stringify(r.body || {});
  assert("response body does not echo <script>", !bodyStr.includes("<script>"),
    `response: ${bodyStr.slice(0, 120)}`);
}

// ── Observability ───────────────────────────────────────────────────────────
async function observabilityActuatorHealth() {
  console.log("\n=== Observability 1 — actuator/health probe ===");
  const r = await req("/api/actuator/health");
  assert("actuator/health returns 200", r.status === 200, `got ${r.status}`);
  assert("actuator/health body.status == UP", r.body && r.body.status === "UP",
    `got ${JSON.stringify(r.body).slice(0, 100)}`);
  assert("actuator/health does NOT leak component details",
    !r.body || !r.body.components, "show-details should hide downstream dep status");
}

async function observabilityActuatorInfo() {
  console.log("\n=== Observability 2 — actuator/info reachable ===");
  const r = await req("/api/actuator/info");
  assert("actuator/info returns 200 (whitelisted, no auth)",
    r.status === 200, `got ${r.status}`);
}

async function observabilityAuditLogAfterCharge(jwt) {
  console.log("\n=== Observability 3 — audit log records every state transition ===");
  const callerRef = `obs-audit-${Date.now()}`;
  const created = await req("/api/payment/charges", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${jwt}`,
      "Content-Type": "application/json",
      "Idempotency-Key": `k-${callerRef}`,
    },
    body: JSON.stringify({
      userId: "00000099-0000-0000-0000-0000000000ad",
      amountCents: 750,
      currency: "EGP",
      callerReference: callerRef,
    }),
  });
  if (created.status !== 201) {
    assert("setup charge", false, `status ${created.status}`);
    return;
  }
  const chargeId = created.body.chargeId;
  const providerRef = String((Date.now() % 9000000) + 13000000);
  await req("/api/payment/webhooks/paymob", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Paymob-Signature": "dev-stub-ok",
    },
    body: JSON.stringify({
      type: "transaction",
      obj: { id: Number(providerRef), success: true, merchant_order_id: chargeId },
    }),
  });
  await sleep(2500);

  // payment_event_log: written by AuditLogger on every state transition.
  // Expect at minimum: ChargeAccepted, ChargeSucceeded.
  const sql = `SELECT event_type, actor, actor_id FROM payment_event_log WHERE subject_id='${chargeId}' ORDER BY timestamp ASC;`;
  const out = sh(`${PG_PAYMENT} -c "${sql}"`);
  const rows = out
    ? out.split("\n").map((l) => {
        const [eventType, actor, actorId] = l.split("|");
        return { eventType, actor, actorId };
      })
    : [];
  console.log(`  payment_event_log rows for charge: ${rows.map((r) => r.eventType).join(", ")}`);
  assert("audit log has ChargeAccepted entry",
    rows.some((r) => r.eventType === "ChargeAccepted"));
  assert("audit log has ChargeSucceeded entry",
    rows.some((r) => r.eventType === "ChargeSucceeded"));
  assert("ChargeAccepted was actor=SYSTEM",
    rows.find((r) => r.eventType === "ChargeAccepted")?.actor === "SYSTEM");
  assert("ChargeSucceeded was actor=PROVIDER",
    rows.find((r) => r.eventType === "ChargeSucceeded")?.actor === "PROVIDER");
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Payment URL: ${PAYMENT_URL}`);
  const goodJwt = mintJwt({
    iss: "payment-service", aud: "payment", iat: now(), exp: now() + 3600,
  });

  try {
    await securityJwtMissingExp();
    await securityJwtFutureIat();
    await securityJwtWrongAudience();
    await securityIdemKeyCrlf();
    await securityIdemKeyOverlong();
    await securityHmacAlternateHeaderCase();
    await securityErrorBodyShape();
    await observabilityActuatorHealth();
    await observabilityActuatorInfo();
    await observabilityAuditLogAfterCharge(goodJwt);
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
