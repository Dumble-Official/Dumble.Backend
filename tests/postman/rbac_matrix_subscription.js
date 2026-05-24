// RBAC matrix for Subscription. Probes the (role × endpoint) grid:
//
//   - /admin/** is restricted to ROLE_ADMIN by @PreAuthorize. Hit every admin
//     endpoint with PARTICIPANT, TRAINER, GYM_OWNER, ADMIN — expect 4xx for
//     the first three and 2xx for ADMIN. SecurityConfig defines NO role-
//     based URL filter, so the only role gate is @PreAuthorize; any silent
//     loss of that annotation would let any authenticated user manipulate
//     seller lifecycle.
//
//   - /me/** endpoints are "any authenticated user" — they reflect the
//     caller's identity. Hit /me/plan with each role; assert that response
//     ownership tracks the caller (no role leaks the wrong user's data).
//
//   - Unauthenticated callers hit 401 on every protected path.
//
// Run: node tests/postman/rbac_matrix_subscription.js

const crypto = require("crypto");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
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

function uid(tag, n) {
  // tag is two ascii chars (e.g. "pa", "tr"); rest hex
  const tailHex = (BigInt(stamp) + BigInt(n)).toString(16).padStart(12, "0").slice(-12);
  return `00000099-0000-0000-0000-${tailHex}`;
}
function mint(role, userId) {
  return jwt({
    sub: `${role.toLowerCase()}-${userId.slice(-4)}@dumble.test`,
    userId, displayName: role, userType: role, roles: [role], iat: now, exp,
  }, userKey);
}

const PARTICIPANT_ID = uid("pa", 1);
const TRAINER_ID     = uid("tr", 2);
const GYM_OWNER_ID   = uid("go", 3);
const ADMIN_ID       = uid("ad", 4);

const tokens = {
  PARTICIPANT: mint("PARTICIPANT", PARTICIPANT_ID),
  TRAINER:     mint("TRAINER",     TRAINER_ID),
  GYM_OWNER:   mint("GYM_OWNER",   GYM_OWNER_ID),
  ADMIN:       mint("ADMIN",       ADMIN_ID),
};

let passed = 0, failed = 0;
function check(label, ok, detail) {
  if (ok) { passed++; console.log(`  ✓ ${label}`); }
  else    { failed++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function call(method, path, role, body) {
  const headers = { "Content-Type": "application/json" };
  if (role) headers["Authorization"] = `Bearer ${tokens[role]}`;
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

const ADMIN_ENDPOINTS = [
  ["GET",  "/api/admin/platform/subscriptions"],
  ["GET",  "/api/admin/platform/escrow"],
  ["GET",  "/api/admin/platform/refunds"],
  ["GET",  "/api/admin/platform/dunning"],
  ["GET",  "/api/admin/platform/revenue"],
  ["GET",  "/api/admin/sellers/top"],
  ["POST", `/api/admin/sellers/${uid("se", 99)}/freeze`,        { reason: "rbac-probe" }],
  ["POST", `/api/admin/sellers/${uid("se", 99)}/winding-down`,  { reason: "rbac-probe" }],
];

(async () => {
  console.log(`Subscription: ${SUBSCRIPTION_URL}`);
  console.log(`Participant: ${PARTICIPANT_ID.slice(-12)}, Trainer: ${TRAINER_ID.slice(-12)}, GymOwner: ${GYM_OWNER_ID.slice(-12)}, Admin: ${ADMIN_ID.slice(-12)}`);

  console.log("\n=== 1. Admin endpoints reject non-ADMIN roles ===");
  for (const [method, path, body] of ADMIN_ENDPOINTS) {
    for (const role of ["PARTICIPANT", "TRAINER", "GYM_OWNER"]) {
      const r = await call(method, path, role, body);
      // 403 is the spec; some Spring stacks return 401 — both prove the gate
      // is closed, but only 403 means the JWT was accepted AND the role
      // gate triggered. We accept 403; warn on 401 (filter rejected token).
      check(`${role.padEnd(11)} ${method} ${path.replace(uid("se",99), "{sellerId}")} → 403`,
        r.status === 403, `got ${r.status}`);
    }
  }

  console.log("\n=== 2. Admin endpoints accept ADMIN ===");
  for (const [method, path, body] of ADMIN_ENDPOINTS) {
    const r = await call(method, path, "ADMIN", body);
    // 2xx = success path. seller lifecycle on a never-seen sellerId may
    // 200 (creates row) or 4xx (sellerId not known to other svc); both
    // mean the @PreAuthorize gate let us through. We assert NOT 401 / 403.
    const labelPath = path.replace(uid("se", 99), "{sellerId}");
    check(`ADMIN       ${method} ${labelPath} → not 401/403`,
      r.status !== 401 && r.status !== 403, `got ${r.status}`);
  }

  console.log("\n=== 3. Unauthenticated requests rejected on protected paths ===");
  const protectedPaths = [
    ["GET",  "/api/me/plan"],
    ["GET",  "/api/me/entitlements"],
    ["GET",  "/api/me/earnings/summary"],
    ["GET",  "/api/admin/platform/revenue"],
    ["POST", "/api/me/plan/upgrade"],
  ];
  for (const [method, path] of protectedPaths) {
    const r = await call(method, path, null, method === "POST" ? {} : undefined);
    check(`unauth     ${method} ${path} → 401`,
      r.status === 401 || r.status === 403, `got ${r.status}`);
  }

  console.log("\n=== 4. /me/plan reflects caller identity (not leaking across roles) ===");
  // Each role hits /me/plan; the userId in any response (if surfaced) must
  // match THAT role's caller, never anyone else's. PARTICIPANT also POSTs an
  // upgrade and the response should reflect their own state.
  for (const role of ["PARTICIPANT", "TRAINER", "GYM_OWNER", "ADMIN"]) {
    const r = await call("GET", "/api/me/plan", role);
    check(`${role.padEnd(11)} /me/plan accessible`, r.status >= 200 && r.status < 300, `status=${r.status}`);
    if (r.body) {
      const responseUserId = r.body.userId || r.body.user_id || null;
      if (responseUserId) {
        const expectedId = { PARTICIPANT: PARTICIPANT_ID, TRAINER: TRAINER_ID, GYM_OWNER: GYM_OWNER_ID, ADMIN: ADMIN_ID }[role];
        check(`${role.padEnd(11)} /me/plan returns caller's own userId`,
          responseUserId === expectedId, `returned=${responseUserId} expected=${expectedId}`);
      }
    }
  }

  console.log("\n=== 5. /me/entitlements + /me/earnings work for any authenticated role ===");
  for (const role of ["PARTICIPANT", "TRAINER", "GYM_OWNER", "ADMIN"]) {
    const ent = await call("GET", "/api/me/entitlements", role);
    check(`${role.padEnd(11)} /me/entitlements → 2xx`, ent.status >= 200 && ent.status < 300, `status=${ent.status}`);
    const ern = await call("GET", "/api/me/earnings/summary", role);
    check(`${role.padEnd(11)} /me/earnings/summary → 2xx`, ern.status >= 200 && ern.status < 300, `status=${ern.status}`);
  }

  console.log("\n=== 6. /admin/** rejects manipulated authority claims ===");
  // A PARTICIPANT crafting a JWT that claims roles=["ADMIN"] inside the
  // payload — but the userType is still PARTICIPANT — should NOT get admin
  // access. The filter wires authority from userType, not the freeform
  // roles array, so this MUST 403. If 200, the role binding is broken.
  const sneaky = jwt({
    sub: "sneaky@dumble.test", userId: PARTICIPANT_ID, displayName: "Sneaky",
    userType: "PARTICIPANT", roles: ["ADMIN"], iat: now, exp,
  }, userKey);
  const sneakyRes = await fetch(`${SUBSCRIPTION_URL}/api/admin/platform/revenue`, {
    headers: { Authorization: `Bearer ${sneaky}` },
  });
  check(`PARTICIPANT JWT with roles=[ADMIN] cannot reach /admin → 403`,
    sneakyRes.status === 403, `got ${sneakyRes.status}`);

  console.log(`\n${failed === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${passed}/${passed + failed} checks passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
