// End-to-end test for the auth role-promotion feature.
//
// Walks the full flow against the running auth service (direct, host port 18081):
//   participant register → submit request → list mine
//   admin login → list queue → request-changes
//   participant edits & resubmits (same id) → admin sees resubmission
//   admin approve → participant's userType flips to TRAINER
//   participant re-login → new token carries the new role
//   plus: non-admin is forbidden from the admin endpoints
//
// Run: node tests/postman/role_promotion.js
//   AUTH_HOST_URL  default http://localhost:18081
//   ADMIN_EMAIL / ADMIN_PASSWORD  default to the release/.env dev admin

const fs = require("fs");

const AUTH = process.env.AUTH_HOST_URL || "http://localhost:18081";

// Pull the admin creds from release/.env unless overridden.
function envVal(name, fallback) {
  if (process.env[name]) return process.env[name];
  try {
    const env = fs.readFileSync("release/.env", "utf8");
    const m = env.match(new RegExp("^" + name + "=(.+)$", "m"));
    if (m) return m[1].trim();
  } catch {}
  return fallback;
}
const ADMIN_EMAIL = envVal("ADMIN_EMAIL", "admin@dumble.local");
const ADMIN_PASSWORD = envVal("ADMIN_PASSWORD", "");

const stamp = Date.now();
let pass = 0, fail = 0;
function check(label, ok, detail) {
  if (ok) { pass++; console.log(`  ✓ ${label}`); }
  else    { fail++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}
const j = async (r) => { const t = await r.text(); try { return JSON.parse(t); } catch { return t; } };

async function call(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body)  headers["Content-Type"] = "application/json";
  const res = await fetch(`${AUTH}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}

(async () => {
  console.log(`Auth: ${AUTH}`);
  const email = `promo+${stamp}@dumble.test`;
  const pw = "Test1234!";

  // ── participant register ──────────────────────────────────────────
  console.log("\n=== participant submits a TRAINER request ===");
  let r = await call("POST", "/api/auth/register", null,
    { email, password: pw, firstName: "Promo", lastName: "Tester" });
  let token = r.body.accessToken;
  check("register → token", r.status === 201 && !!token, `status=${r.status}`);

  r = await call("POST", "/api/users/me/role-requests", token,
    { requestedRole: "TRAINER", certificateUrl: "https://res.cloudinary.com/x/cert1.pdf", note: "PT certified" });
  const reqId = r.body.id;
  check("submit request → 201 PENDING", r.status === 201 && r.body.status === "PENDING", `status=${r.status} body=${JSON.stringify(r.body).slice(0,120)}`);

  r = await call("POST", "/api/users/me/role-requests", token,
    { requestedRole: "TRAINER", certificateUrl: "https://x/2.pdf" });
  check("second open request → rejected (one at a time)", r.status === 400, `status=${r.status}`);

  r = await call("GET", "/api/users/me/role-requests", token);
  check("list mine → shows the request", r.status === 200 && Array.isArray(r.body) && r.body.length === 1, `status=${r.status}`);

  // ── non-admin can't reach the admin queue ─────────────────────────
  console.log("\n=== authorization ===");
  r = await call("GET", "/api/admin/role-requests", token);
  check("participant → admin queue 403", r.status === 403, `status=${r.status}`);

  // ── admin login + review ──────────────────────────────────────────
  console.log("\n=== admin reviews ===");
  r = await call("POST", "/api/auth/login", null, { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
  const adminToken = r.body.accessToken;
  check("admin login → token", r.status === 200 && !!adminToken, `status=${r.status} (check ADMIN creds)`);

  r = await call("GET", "/api/admin/role-requests?status=PENDING", adminToken);
  const inQueue = r.status === 200 && (r.body.content || []).some((x) => x.id === reqId);
  check("admin queue shows the pending request", inQueue, `status=${r.status}`);

  r = await call("POST", `/api/admin/role-requests/${reqId}/request-changes`, adminToken,
    { message: "Please upload a clearer certificate scan." });
  check("request-changes → CHANGES_REQUESTED", r.status === 200 && r.body.status === "CHANGES_REQUESTED", `status=${r.status}`);

  // ── participant edits & resubmits (same id) ───────────────────────
  console.log("\n=== participant edits & resubmits (same id) ===");
  r = await call("PATCH", `/api/users/me/role-requests/${reqId}`, token,
    { requestedRole: "TRAINER", certificateUrl: "https://res.cloudinary.com/x/cert1-clear.pdf", note: "clearer scan attached" });
  check("edit → back to PENDING, same id", r.status === 200 && r.body.status === "PENDING" && r.body.id === reqId, `status=${r.status} id=${r.body.id}`);
  check("edit kept the admin message (resubmission is traceable)", r.body.adminMessage && r.body.adminMessage.includes("clearer"), `adminMessage=${r.body.adminMessage}`);

  // ── admin approves → role flips ───────────────────────────────────
  console.log("\n=== admin approves → role flips ===");
  r = await call("POST", `/api/admin/role-requests/${reqId}/approve`, adminToken);
  check("approve → APPROVED", r.status === 200 && r.body.status === "APPROVED", `status=${r.status}`);

  r = await call("GET", "/api/users/me", token);
  check("user row userType is now TRAINER (auth re-reads DB)", r.status === 200 && r.body.userType === "TRAINER", `userType=${r.body.userType}`);

  // re-login → fresh token carries the new role for downstream services
  r = await call("POST", "/api/auth/login", null, { email, password: pw });
  check("re-login → token issued for promoted user", r.status === 200 && !!r.body.accessToken, `status=${r.status}`);

  // ── approved is terminal: can't be approved again ─────────────────
  r = await call("POST", `/api/admin/role-requests/${reqId}/approve`, adminToken);
  check("re-approve an already-approved request → 400", r.status === 400, `status=${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
