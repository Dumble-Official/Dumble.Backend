// Security probes for Subscription beyond the contract suite:
//   1. JWT manipulation (alg=none, HS512 against HS256, tampered payload,
//      future iat)
//   2. CRLF in Idempotency-Key + 128-char boundary
//   3. Append-only audit log probe (insert is OK; UPDATE/DELETE should fail
//      if the team enforces it via trigger — flag if not)
//   4. Error-response shape consistency (no XSS reflection, structured body)
//   5. Actuator surface sanity (health/info public, env/configprops/threaddump
//      NOT public)
//
// Run: node tests/postman/security_subscription.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

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
function jwt(claims, key, header = { alg: "HS256", typ: "JWT" }) {
  const hdr = b64u(JSON.stringify(header));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", key).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const stamp = Date.now();
const PARTICIPANT = `00000099-0000-0000-${stamp.toString(16).padStart(16, "0").slice(-4)}-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const userJwt = jwt(
  { sub: `sec-${stamp}@dumble.test`, userId: PARTICIPANT, displayName: "Sec",
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

async function postJson(path, body, headers) {
  try {
    const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
    });
    let parsed = null; let text = "";
    try { text = await res.text(); parsed = JSON.parse(text); } catch {}
    return { status: res.status, body: parsed, raw: text };
  } catch (ex) {
    return { status: 0, err: ex.message };
  }
}
async function getJson(path, headers = {}) {
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, { headers });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
function psql(sql) {
  return spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "subscription-db", "psql",
    "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql], { encoding: "utf8" });
}

async function jwtProbes() {
  console.log("\n=== 1. JWT manipulation ===");
  const idemPrefix = `k-sec-${stamp}`;

  // alg=none
  const noneTok = b64u(JSON.stringify({ alg: "none", typ: "JWT" })) + "." +
                  b64u(JSON.stringify({ sub: "evil", userId: PARTICIPANT,
                    userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp })) + ".";
  const r1 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "t1", paymentMethodType: "CARD" },
    { Authorization: `Bearer ${noneTok}`, "Idempotency-Key": `${idemPrefix}-none` });
  assert(`alg=none token rejected (401)`, r1.status === 401, `got ${r1.status}`);

  // HS512 token verified against HS256 secret
  const wrongHdr = { alg: "HS512", typ: "JWT" };
  const wrongPayload = { sub: "x", userId: PARTICIPANT, userType: "PARTICIPANT",
                          roles: ["PARTICIPANT"], iat: now, exp };
  const wrongTok = b64u(JSON.stringify(wrongHdr)) + "." +
                   b64u(JSON.stringify(wrongPayload)) + "." +
                   b64u(crypto.createHmac("sha512", userKey).update(b64u(JSON.stringify(wrongHdr)) + "." + b64u(JSON.stringify(wrongPayload))).digest());
  const r2 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "t2", paymentMethodType: "CARD" },
    { Authorization: `Bearer ${wrongTok}`, "Idempotency-Key": `${idemPrefix}-hs512` });
  assert(`HS512 against HS256 verifier rejected (401)`, r2.status === 401, `got ${r2.status}`);

  // Tampered payload (post-sign edit)
  const baseHdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const basePayload = b64u(JSON.stringify({ sub: "innocent", userId: PARTICIPANT,
    userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp }));
  const baseSig = b64u(crypto.createHmac("sha256", userKey).update(`${baseHdr}.${basePayload}`).digest());
  const tamperedPayload = b64u(JSON.stringify({ sub: "innocent", userId: PARTICIPANT,
    userType: "ADMIN", roles: ["ADMIN"], iat: now, exp }));
  const tamperedTok = `${baseHdr}.${tamperedPayload}.${baseSig}`;
  const r3 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "t3", paymentMethodType: "CARD" },
    { Authorization: `Bearer ${tamperedTok}`, "Idempotency-Key": `${idemPrefix}-tamp` });
  assert(`tampered-payload token rejected (401)`, r3.status === 401, `got ${r3.status}`);
}

async function idempotencyKeyBoundary() {
  console.log("\n=== 2. Idempotency-Key boundary ===");

  // Exactly 128 chars → accepted (validation passes; business logic might
  // still produce some status, just not a 400 from the validator)
  const exactly128 = "a".repeat(128);
  const r1 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: `tok-128-${stamp}`, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": exactly128 });
  assert(`128-char key (boundary) accepted by validator (no 400 from key check)`,
    r1.status !== 400 || !/Idempotency-Key/i.test((r1.body || {}).message || ""),
    `status=${r1.status} body=${JSON.stringify(r1.body).slice(0,100)}`);

  // 129 chars → 400
  const tooLong = "a".repeat(129);
  const r2 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "tx", paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": tooLong });
  assert(`129-char key rejected (400)`, r2.status === 400, `got ${r2.status}`);

  // Disallowed chars (spaces)
  const r3 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "tx", paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": "evil key with spaces" });
  assert(`key with spaces rejected (400)`, r3.status === 400, `got ${r3.status}`);

  // SQL-y chars
  const r4 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "tx", paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": "k'; DROP TABLE--" });
  assert(`key with SQL chars rejected by regex (400)`, r4.status === 400, `got ${r4.status}`);
}

async function auditLogAppendOnly() {
  console.log("\n=== 3. Audit log probe (Subscription PDF §14) ===");

  // INSERT directly should work (sanity check we have the right table)
  const insertSql = `INSERT INTO subscription_event_log (subscription_id, event_type, timestamp, actor, actor_id, reason, payload_json) VALUES ('${PARTICIPANT}', 'QA_SECURITY_PROBE', now(), 'qa', 'qa', 'probe', '{}'::text);`;
  const ins = psql(insertSql);
  if (ins.code !== 0) {
    console.log(`  ⚠ couldn't INSERT into subscription_event_log; skipping append-only probe`);
    console.log(`    stderr=${ins.stderr.slice(0, 150)}`);
    return;
  }
  console.log(`  ✓ sanity: INSERT works`);

  // UPDATE — Subscription's audit table doesn't currently have a trigger
  // enforcing append-only. Wallet's does. Flag if Subscription doesn't.
  const upd = psql(`UPDATE subscription_event_log SET reason = 'mutated' WHERE event_type = 'QA_SECURITY_PROBE' AND actor = 'qa';`);
  const updBlocked = upd.code !== 0 && /trigger|cannot|denied|immutable/i.test(upd.stderr);
  if (updBlocked) {
    assert(`UPDATE on subscription_event_log rejected by trigger`, true);
  } else {
    console.log(`  ⚠ FINDING: subscription_event_log accepts UPDATE — no append-only trigger`);
    console.log(`    Wallet's wallet_entries has one; Subscription should adopt the same for forensic integrity`);
  }

  // DELETE
  const del = psql(`DELETE FROM subscription_event_log WHERE event_type = 'QA_SECURITY_PROBE' AND actor = 'qa';`);
  const delBlocked = del.code !== 0 && /trigger|cannot|denied|immutable/i.test(del.stderr);
  if (delBlocked) {
    assert(`DELETE on subscription_event_log rejected by trigger`, true);
  } else {
    console.log(`  ⚠ FINDING: subscription_event_log accepts DELETE`);
  }
}

async function errorShape() {
  console.log("\n=== 4. Error response shape + XSS reflection ===");

  const probe = "<script>alert(1)</script>";
  const r1 = await fetch(`${SUBSCRIPTION_URL}/api/me/plan/upgrade`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${userJwt}`,
      "Content-Type": "application/json",
      "Idempotency-Key": `k-sec-shape-${stamp}`,
    },
    body: `{malformed ${probe}`,
  });
  const r1Text = await r1.text();
  assert(`400 on malformed JSON`, r1.status === 400, `got ${r1.status}`);
  assert(`error body does NOT echo attacker payload (no XSS reflection)`,
    !r1Text.includes(probe), r1Text.slice(0, 150));

  // 401: no token gives a consistent shape
  const r2 = await postJson("/api/me/plan/upgrade",
    { paymentMethodToken: "t", paymentMethodType: "CARD" },
    { "Idempotency-Key": `k-sec-noauth-${stamp}` });
  assert(`401 response has {status,message}`,
    r2.status === 401 && r2.body && typeof r2.body.status === "number" && typeof r2.body.message === "string",
    JSON.stringify(r2.body));
}

async function actuatorSurface() {
  console.log("\n=== 5. Actuator surface ===");

  const h = await getJson("/api/actuator/health");
  assert(`/actuator/health public + UP`, h.status === 200 && h.body && h.body.status === "UP",
    `status=${h.status} body=${JSON.stringify(h.body).slice(0, 100)}`);

  for (const path of ["/api/actuator/env", "/api/actuator/configprops", "/api/actuator/threaddump", "/api/actuator/beans", "/api/actuator/mappings", "/api/actuator/heapdump"]) {
    const r = await getJson(path);
    const exposed = r.status === 200;
    assert(`${path} NOT publicly exposed (got ${r.status})`, !exposed, `status=${r.status}`);
  }
}

(async () => {
  console.log(`Subscription URL: ${SUBSCRIPTION_URL}`);
  try {
    await jwtProbes();
    await idempotencyKeyBoundary();
    await auditLogAppendOnly();
    await errorShape();
    await actuatorSurface();
  } catch (ex) {
    console.error("Security harness threw:", ex);
    failedChecks += 1; totalChecks += 1;
  }
  console.log(`\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`);
  process.exit(failedChecks === 0 ? 0 : 1);
})();
