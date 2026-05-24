// Chaos harness for Subscription.
//
// Three scenarios:
//   1. RabbitMQ outage while outbox is in flight — Subscription's HTTP
//      surface must keep accepting writes (the outbox row decouples the
//      HTTP path from broker availability) and the row must drain after
//      RabbitMQ recovers.
//   2. subscription-db outage — Subscription must return a 5xx within the
//      configured Hikari connection-timeout (5 s after this branch's fix,
//      30 s on default), not hang the caller, and recover after the DB
//      comes back.
//   3. Burst above Hikari pool size — with pool=20 (this branch) a burst
//      of 25 parallel writes must mostly succeed; under default pool=10
//      it produced 24 × 500.
//
// Run: node tests/postman/chaos_subscription.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
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
const STAMP_HEX = stamp.toString(16).padStart(16, "0").slice(-4);
const now = Math.floor(stamp / 1000);
const exp = now + 3600;

// suffix is a small integer; we convert to a hex-only 12-char tail so the
// final string is a valid RFC 4122 UUID (the auth filter parses userId as
// a UUID and 401s on garbage like "rb01" / "bu00" which contain non-hex chars).
function userJwt(suffixInt) {
  const tail = suffixInt.toString(16).padStart(12, "0").slice(-12);
  const userId = `00000099-0000-0000-${STAMP_HEX}-${tail}`;
  return {
    userId,
    token: jwt(
      { sub: `chaos-${suffixInt}@dumble.test`, userId, displayName: "Chaos",
        userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
      userKey
    ),
  };
}

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) console.log(`  ✓ ${label}`);
  else { failedChecks += 1; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function postJson(path, body, headers, timeoutMs = 35000) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const t0 = Date.now();
    const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const elapsed = Date.now() - t0;
    let parsed = null;
    try { parsed = await res.json(); } catch {}
    return { status: res.status, body: parsed, elapsed };
  } catch (ex) {
    return { status: 0, err: ex.message };
  } finally {
    clearTimeout(t);
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function compose(args) {
  return spawnSync("docker", ["compose", ...COMPOSE, ...args], { encoding: "utf8" });
}
function psql(sql) {
  return spawnSync(
    "docker",
    ["compose", ...COMPOSE, "exec", "-T", "subscription-db", "psql",
      "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql],
    { encoding: "utf8" }
  );
}

async function s1_rabbitOutage() {
  console.log("\n=== Scenario 1 — RabbitMQ outage while outbox is active ===");
  const { token } = userJwt(0xa001);
  console.log("  stopping rabbitmq ...");
  compose(["stop", "rabbitmq"]);
  await sleep(2000);

  // HTTP path should still accept the upgrade (outbox row written in tx;
  // publisher will retry later).
  const up = await postJson(
    "/api/me/plan/upgrade",
    { paymentMethodToken: `tok-chaos-rb-${stamp}`, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${token}`, "Idempotency-Key": `k-chaos-rb-${stamp}` }
  );
  assert(`upgrade accepted while rabbit down (2xx/4xx, no 5xx)`,
    up.status >= 200 && up.status < 500,
    `got ${up.status} body=${JSON.stringify(up.body).slice(0,150)}`);

  // Outbox row should exist (PENDING or IN_FLIGHT).
  const before = psql(`SELECT count(*) FROM outbox_events WHERE status IN ('PENDING','IN_FLIGHT') AND created_at > now() - interval '30 seconds';`);
  const pendingBefore = parseInt(before.stdout.trim() || "0", 10);
  assert(`at least one outbox row still pending (broker can't accept yet)`, pendingBefore >= 1, `count=${pendingBefore}`);

  console.log("  starting rabbitmq ...");
  compose(["start", "rabbitmq"]);
  // Wait up to ~45 s for broker healthy + publisher to drain.
  const drained = await (async () => {
    for (let i = 0; i < 45; i++) {
      await sleep(1000);
      const r = psql(`SELECT count(*) FROM outbox_events WHERE status IN ('PENDING','IN_FLIGHT') AND created_at > now() - interval '90 seconds';`);
      const stillPending = parseInt(r.stdout.trim() || "0", 10);
      if (stillPending === 0) return true;
    }
    return false;
  })();
  assert(`outbox drained to PUBLISHED within 45 s after rabbit recovery`, drained);
}

async function s2_dbOutage() {
  console.log("\n=== Scenario 2 — subscription-db outage, fast-fail + recovery ===");
  const { token } = userJwt(0xb001);
  console.log("  stopping subscription-db ...");
  compose(["stop", "subscription-db"]);
  await sleep(3000);

  const t0 = Date.now();
  const r = await postJson(
    "/api/me/plan/upgrade",
    { paymentMethodToken: `tok-chaos-db-${stamp}`, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${token}`, "Idempotency-Key": `k-chaos-db-${stamp}` }
  );
  const elapsed = Date.now() - t0;
  console.log(`  request returned status=${r.status} after ${elapsed} ms`);
  assert(`returns within 10 s (didn't hang for 30 s)`, elapsed < 10000, `${elapsed}ms`);
  assert(`returns 5xx under DB outage`, r.status >= 500, `got ${r.status}`);

  console.log("  starting subscription-db ...");
  compose(["start", "subscription-db"]);
  // Wait for healthy + Hikari reconnects.
  const recovered = await (async () => {
    for (let i = 0; i < 30; i++) {
      await sleep(1500);
      const res = await fetch(`${SUBSCRIPTION_URL}/api/actuator/health`);
      if (res.status === 200) return true;
    }
    return false;
  })();
  assert(`actuator/health returns 200 after DB recovery`, recovered);

  const after = await postJson(
    "/api/me/plan/upgrade",
    { paymentMethodToken: `tok-chaos-after-${stamp}`, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${token}`, "Idempotency-Key": `k-chaos-after-${stamp}` }
  );
  assert(`serves a fresh upgrade after DB recovery`,
    after.status >= 200 && after.status < 500,
    `got ${after.status}`);
}

async function s3_burst() {
  console.log("\n=== Scenario 3 — 25-parallel burst (Hikari pool=20) ===");
  // Each call uses a unique user + key so they're independent (no IdempotencyConflict).
  // Hex-only suffix so the generated userId stays a valid UUID.
  const callers = Array.from({ length: 25 }, (_, i) => userJwt(0xc000 + i));
  const t0 = Date.now();
  const results = await Promise.all(callers.map((c, i) =>
    postJson(
      "/api/me/plan/upgrade",
      { paymentMethodToken: `tok-chaos-bu-${stamp}-${i}`, paymentMethodType: "CARD" },
      { Authorization: `Bearer ${c.token}`, "Idempotency-Key": `k-chaos-bu-${stamp}-${i}` }
    )
  ));
  const elapsed = Date.now() - t0;
  const byStatus = {};
  for (const r of results) byStatus[r.status] = (byStatus[r.status] || 0) + 1;
  console.log(`  25 parallel requests in ${elapsed} ms — status dist ${JSON.stringify(byStatus)}`);
  const success = (byStatus[200] || 0) + (byStatus[201] || 0);
  assert(`burst > pool size does not produce 5xx (queued by Hikari)`,
    !Object.keys(byStatus).some((s) => Number(s) >= 500), JSON.stringify(byStatus));
  assert(`at least 20/25 succeed with 2xx`, success >= 20, `success=${success}`);
}

(async () => {
  console.log(`Subscription URL: ${SUBSCRIPTION_URL}`);
  try {
    await s3_burst();
    await s1_rabbitOutage();
    await s2_dbOutage();
  } catch (ex) {
    console.error("Chaos threw:", ex);
    failedChecks += 1; totalChecks += 1;
  }
  console.log(`\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`);
  process.exit(failedChecks === 0 ? 0 : 1);
})();
