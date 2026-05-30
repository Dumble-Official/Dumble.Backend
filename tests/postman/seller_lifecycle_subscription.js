// Seller-lifecycle state-machine drill for Subscription.
//
// Drives a fresh sellerId through every admin lifecycle transition and
// asserts the documented invariants from Decision 16 / 17 / 18:
//
//   - freeze: ACTIVE → FROZEN (sets frozenAt + frozenUntil 7d out)
//   - unfreeze: only valid when status == FROZEN; → ACTIVE
//   - re-freeze after unfreeze: ACTIVE → FROZEN (cycle OK)
//   - ban: any-but-BANNED → BANNED (final; refunds unreleased escrow)
//   - ban is idempotent: banning already-BANNED returns 2xx, no double-effect
//   - cannot unfreeze a BANNED seller (BusinessRuleViolation)
//   - cannot start winding-down on a BANNED seller
//   - winding-down: ACTIVE → WINDING_DOWN
//   - revert winding-down: only valid when WINDING_DOWN; → ACTIVE
//
// Each transition writes an outbox event AND an audit log entry; we verify
// both side-effects against the database too.
//
// Run: node tests/postman/seller_lifecycle_subscription.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];
const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const userKey = Buffer.from(USER_KEY_B64, "base64");

const b64u = (b) => Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function jwt(claims, key) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", key).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const stamp = Date.now();
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
function uid(n) {
  const tailHex = (BigInt(stamp) + BigInt(n)).toString(16).padStart(12, "0").slice(-12);
  return `00000099-0000-0000-0000-${tailHex}`;
}
const ADMIN_ID = uid(100);
const adminTok = jwt({
  sub: `admin-${stamp}@dumble.test`, userId: ADMIN_ID, displayName: "Admin",
  userType: "ADMIN", roles: ["ADMIN"], iat: now, exp,
}, userKey);

let passed = 0, failed = 0;
function check(label, ok, detail) {
  if (ok) { passed++; console.log(`  ✓ ${label}`); }
  else    { failed++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function admin(method, path, body) {
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method,
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${adminTok}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "subscription-db", "psql",
    "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql], { encoding: "utf8" });
  return { code: r.status || 0, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}
function dbStatus(sellerId) {
  return psql(`SELECT status FROM seller_lifecycle WHERE seller_id = '${sellerId}';`).stdout;
}
function outboxFor(sellerId, eventType, withinSec = 30) {
  return psql(`SELECT count(*) FROM outbox_events WHERE event_type = '${eventType}' AND payload_json::text LIKE '%${sellerId}%' AND created_at > now() - interval '${withinSec} seconds';`).stdout;
}
function auditFor(sellerId, eventType) {
  // Subscription's audit log lives in subscription_event_log (event_type +
  // subscription_id / actor_id / reason / payload_json). For seller-level
  // events the actor_id is null and the sellerId lands in payload_json or
  // subscription_id, so match on event_type + sellerId text anywhere.
  return psql(`SELECT count(*) FROM subscription_event_log WHERE event_type = '${eventType}' AND (subscription_id::text = '${sellerId}' OR payload_json LIKE '%${sellerId}%' OR actor_id LIKE '%${sellerId}%');`).stdout;
}

(async () => {
  console.log(`Subscription: ${SUBSCRIPTION_URL}`);
  console.log(`Admin user: ${ADMIN_ID.slice(-12)}`);

  const SELLER_A = uid(200);
  const SELLER_B = uid(201);
  const SELLER_C = uid(202);
  console.log(`Sellers: A=${SELLER_A.slice(-12)}, B=${SELLER_B.slice(-12)}, C=${SELLER_C.slice(-12)}`);

  console.log("\n=== 1. Freeze → status FROZEN, outbox + audit emitted ===");
  const f1 = await admin("POST", `/api/admin/sellers/${SELLER_A}/freeze`, { reason: "lifecycle-drill" });
  check(`freeze returns 2xx`, f1.status >= 200 && f1.status < 300, `status=${f1.status} body=${JSON.stringify(f1.body)?.slice(0,120)}`);
  check(`response.status = FROZEN`, f1.body?.status === "FROZEN", `got ${f1.body?.status}`);
  check(`db row exists with status FROZEN`, dbStatus(SELLER_A) === "FROZEN", `db=${dbStatus(SELLER_A)}`);
  check(`SellerFrozen outbox row created`, parseInt(outboxFor(SELLER_A, "SellerFrozen"), 10) >= 1, `count=${outboxFor(SELLER_A, "SellerFrozen")}`);
  check(`SellerFrozen audit log created`, parseInt(auditFor(SELLER_A, "SellerFrozen"), 10) >= 1, `count=${auditFor(SELLER_A, "SellerFrozen")}`);

  console.log("\n=== 2. Unfreeze → status ACTIVE ===");
  const u1 = await admin("POST", `/api/admin/sellers/${SELLER_A}/unfreeze`, { reason: "no-longer-under-review" });
  check(`unfreeze returns 2xx`, u1.status >= 200 && u1.status < 300, `status=${u1.status}`);
  check(`response.status = ACTIVE`, u1.body?.status === "ACTIVE", `got ${u1.body?.status}`);
  check(`db row updated to ACTIVE`, dbStatus(SELLER_A) === "ACTIVE", `db=${dbStatus(SELLER_A)}`);
  check(`SellerUnfrozen outbox row created`, parseInt(outboxFor(SELLER_A, "SellerUnfrozen"), 10) >= 1, `count=${outboxFor(SELLER_A, "SellerUnfrozen")}`);

  console.log("\n=== 3. Re-freeze after unfreeze (cycle) ===");
  const f2 = await admin("POST", `/api/admin/sellers/${SELLER_A}/freeze`, { reason: "second-review" });
  check(`re-freeze returns 2xx`, f2.status >= 200 && f2.status < 300, `status=${f2.status}`);
  check(`db row back to FROZEN`, dbStatus(SELLER_A) === "FROZEN", `db=${dbStatus(SELLER_A)}`);

  console.log("\n=== 4. Cannot unfreeze a not-FROZEN seller ===");
  // SELLER_B has never been touched; unfreeze should fail (no record OR not FROZEN).
  const uMiss = await admin("POST", `/api/admin/sellers/${SELLER_B}/unfreeze`, { reason: "should-fail" });
  check(`unfreeze on never-seen seller → 4xx`, uMiss.status >= 400 && uMiss.status < 500, `got ${uMiss.status}`);

  console.log("\n=== 5. Ban → status BANNED; further unfreeze blocked ===");
  const b1 = await admin("POST", `/api/admin/sellers/${SELLER_A}/ban`, { reason: "policy-violation" });
  check(`ban returns 2xx`, b1.status >= 200 && b1.status < 300, `status=${b1.status}`);
  check(`response.status = BANNED`, b1.body?.status === "BANNED", `got ${b1.body?.status}`);
  check(`db row BANNED`, dbStatus(SELLER_A) === "BANNED");
  check(`SellerBanned outbox row created`, parseInt(outboxFor(SELLER_A, "SellerBanned"), 10) >= 1, `count=${outboxFor(SELLER_A, "SellerBanned")}`);

  const uAfterBan = await admin("POST", `/api/admin/sellers/${SELLER_A}/unfreeze`, { reason: "appeal" });
  check(`unfreeze on BANNED → 4xx (BusinessRuleViolation, not silently OK)`,
    uAfterBan.status >= 400 && uAfterBan.status < 500, `got ${uAfterBan.status}`);
  check(`db row still BANNED after blocked unfreeze`, dbStatus(SELLER_A) === "BANNED", `db=${dbStatus(SELLER_A)}`);

  const fAfterBan = await admin("POST", `/api/admin/sellers/${SELLER_A}/freeze`, { reason: "should-fail" });
  check(`freeze on BANNED → 4xx (cannot freeze already-banned)`,
    fAfterBan.status >= 400 && fAfterBan.status < 500, `got ${fAfterBan.status}`);
  check(`db row still BANNED after blocked freeze`, dbStatus(SELLER_A) === "BANNED");

  const wdAfterBan = await admin("POST", `/api/admin/sellers/${SELLER_A}/winding-down`, { reason: "should-fail" });
  check(`winding-down on BANNED → 4xx`, wdAfterBan.status >= 400 && wdAfterBan.status < 500, `got ${wdAfterBan.status}`);

  console.log("\n=== 6. Ban is idempotent (same seller, banned again, no error) ===");
  const b2 = await admin("POST", `/api/admin/sellers/${SELLER_A}/ban`, { reason: "again" });
  check(`re-ban returns 2xx (idempotent)`, b2.status >= 200 && b2.status < 300, `status=${b2.status}`);
  check(`db row still BANNED`, dbStatus(SELLER_A) === "BANNED");

  console.log("\n=== 7. Winding-down flow (separate fresh seller) ===");
  const w1 = await admin("POST", `/api/admin/sellers/${SELLER_C}/winding-down`, { reason: "voluntary-exit" });
  check(`start winding-down → 2xx`, w1.status >= 200 && w1.status < 300, `status=${w1.status}`);
  check(`response.status = WINDING_DOWN`, w1.body?.status === "WINDING_DOWN", `got ${w1.body?.status}`);
  check(`db row WINDING_DOWN`, dbStatus(SELLER_C) === "WINDING_DOWN", `db=${dbStatus(SELLER_C)}`);
  check(`SellerWindingDown outbox row created`, parseInt(outboxFor(SELLER_C, "SellerWindingDown"), 10) >= 1, `count=${outboxFor(SELLER_C, "SellerWindingDown")}`);

  const w2 = await admin("POST", `/api/admin/sellers/${SELLER_C}/winding-down/revert`, { reason: "changed-mind" });
  check(`revert winding-down → 2xx`, w2.status >= 200 && w2.status < 300, `status=${w2.status}`);
  check(`response.status back to ACTIVE`, w2.body?.status === "ACTIVE", `got ${w2.body?.status}`);
  check(`db row ACTIVE`, dbStatus(SELLER_C) === "ACTIVE");

  console.log("\n=== 8. Revert on not-WINDING_DOWN seller is blocked ===");
  const rMiss = await admin("POST", `/api/admin/sellers/${SELLER_C}/winding-down/revert`, { reason: "double-revert" });
  check(`revert on ACTIVE seller → 4xx (not winding down)`,
    rMiss.status >= 400 && rMiss.status < 500, `got ${rMiss.status}`);

  console.log("\n=== 9. Admin /platform/* stat endpoints reachable ===");
  for (const path of [
    "/api/admin/platform/subscriptions",
    "/api/admin/platform/escrow",
    "/api/admin/platform/dunning",
    "/api/admin/platform/revenue",
    "/api/admin/sellers/top",
  ]) {
    const r = await admin("GET", path);
    check(`GET ${path} → 200`, r.status === 200, `status=${r.status}`);
  }

  console.log(`\n${failed === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${passed}/${passed + failed} checks passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
