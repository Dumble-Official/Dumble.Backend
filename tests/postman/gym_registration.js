// End-to-end test for the gym-owner registration feature (slices 1-3).
//
// Drives the real flow against running auth (tokens) + gym-service:
//   participant registers (auth) → submits a 2-branch gym registration → lists it
//   one-at-a-time guard → non-admin blocked from the admin queue
//   admin reviews → request-changes → participant edits & resubmits (same id)
//   admin approves → a verified ACTIVE Gym exists for each branch
//
// Run: node tests/postman/gym_registration.js
//   AUTH_HOST_URL  default http://localhost:18081
//   GYM_HOST_URL   default http://localhost:18181
//   ADMIN_EMAIL / ADMIN_PASSWORD  from release/.env

const fs = require("fs");
const AUTH = process.env.AUTH_HOST_URL || "http://localhost:18081";
const GYM = process.env.GYM_HOST_URL || "http://localhost:18181";

function envVal(name, fallback) {
  if (process.env[name]) return process.env[name];
  try {
    const m = fs.readFileSync("release/.env", "utf8").match(new RegExp("^" + name + "=(.+)$", "m"));
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
async function call(base, method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body)  headers["Content-Type"] = "application/json";
  const res = await fetch(`${base}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}

function branch(suffix) {
  return {
    name: `Iron Temple ${suffix} ${stamp}`,
    bio: "strength & conditioning",
    address: `${suffix} Tahrir St, Cairo`,
    lat: 30.0444, lng: 31.2357,
    genderType: "MIXED",
    email: "owner@iron.test", phone: "+201000000000",
    licenseId: `LIC-${suffix}-${stamp}`,
    openTime: "06:00:00", closeTime: "23:00:00",
    premisesProofUrl: "https://res.cloudinary.com/x/lease.pdf",
    operatingLicenseUrl: "https://res.cloudinary.com/x/license.pdf",
    civilDefenseUrl: "https://res.cloudinary.com/x/civil.pdf",
  };
}

(async () => {
  console.log(`Auth: ${AUTH}\nGym:  ${GYM}`);
  const email = `gymowner+${stamp}@dumble.test`;
  const pw = "Test1234!";

  // ── participant submits a 2-branch registration ───────────────────
  console.log("\n=== participant submits registration (2 branches) ===");
  let r = await call(AUTH, "POST", "/api/auth/register", null,
    { email, password: pw, firstName: "Gym", lastName: "Owner" });
  let token = r.body.accessToken;
  check("register → token", r.status === 201 && !!token, `status=${r.status}`);

  const regBody = {
    pageName: `Iron Temple ${stamp}`,
    nationalIdUrl: "https://res.cloudinary.com/x/nid.pdf",
    commercialRegisterUrl: "https://res.cloudinary.com/x/cr.pdf",
    taxCardUrl: "https://res.cloudinary.com/x/tax.pdf",
    note: "two branches in Cairo",
    branches: [branch("A"), branch("B")],
  };
  r = await call(GYM, "POST", "/api/gym-registrations", token, regBody);
  const regId = r.body.id;
  check("submit → 201 PENDING, 2 branches", r.status === 201 && r.body.status === "PENDING" && r.body.branches?.length === 2,
        `status=${r.status} body=${JSON.stringify(r.body).slice(0,160)}`);

  r = await call(GYM, "POST", "/api/gym-registrations", token, regBody);
  check("second registration → blocked (one at a time)", r.status === 400, `status=${r.status}`);

  r = await call(GYM, "GET", "/api/gym-registrations", token);
  check("list mine → shows it", r.status === 200 && Array.isArray(r.body) && r.body.length === 1, `status=${r.status}`);

  // ── authorization ─────────────────────────────────────────────────
  console.log("\n=== authorization ===");
  r = await call(GYM, "GET", "/api/admin/gym-registrations", token);
  check("participant → admin queue blocked", r.status >= 400 && r.status < 500, `status=${r.status}`);

  // ── admin review ──────────────────────────────────────────────────
  console.log("\n=== admin reviews ===");
  r = await call(AUTH, "POST", "/api/auth/login", null, { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
  const adminToken = r.body.accessToken;
  check("admin login → token", r.status === 200 && !!adminToken, `status=${r.status}`);

  r = await call(GYM, "GET", "/api/admin/gym-registrations?status=PENDING", adminToken);
  check("admin queue shows the registration", r.status === 200 && (r.body.content || []).some(x => x.id === regId), `status=${r.status}`);

  r = await call(GYM, "POST", `/api/admin/gym-registrations/${regId}/request-changes`, adminToken,
    { message: "Branch B premises proof is unclear — re-upload." });
  check("request-changes → CHANGES_REQUESTED", r.status === 200 && r.body.status === "CHANGES_REQUESTED", `status=${r.status}`);

  // ── participant edits & resubmits ─────────────────────────────────
  console.log("\n=== participant edits & resubmits (same id) ===");
  const edited = { ...regBody, branches: [branch("A"), branch("B2")] };
  r = await call(GYM, "PATCH", `/api/gym-registrations/${regId}`, token, edited);
  check("edit → back to PENDING, same id", r.status === 200 && r.body.status === "PENDING" && r.body.id === regId, `status=${r.status}`);
  check("admin message retained (resubmission traceable)", r.body.adminMessage && r.body.adminMessage.includes("premises"), `adminMessage=${r.body.adminMessage}`);

  // ── admin approves → gyms created ─────────────────────────────────
  console.log("\n=== admin approves → ACTIVE gyms created per branch ===");
  r = await call(GYM, "POST", `/api/admin/gym-registrations/${regId}/approve`, adminToken);
  check("approve → APPROVED", r.status === 200 && r.body.status === "APPROVED", `status=${r.status}`);

  // each branch should now be its own ACTIVE + verified Gym
  r = await call(GYM, "GET", `/api/gyms?name=${encodeURIComponent("Iron Temple A " + stamp)}`, token);
  const gymA = (r.body.content || [])[0];
  check("branch A → ACTIVE + verified Gym exists", !!gymA && gymA.status === "ACTIVE" && gymA.verified === true,
        `status=${r.status} gym=${JSON.stringify(gymA).slice(0,140)}`);

  r = await call(GYM, "GET", `/api/gyms?name=${encodeURIComponent("Iron Temple B2 " + stamp)}`, token);
  const gymB = (r.body.content || [])[0];
  check("branch B2 (edited) → ACTIVE + verified Gym exists", !!gymB && gymB.status === "ACTIVE" && gymB.verified === true,
        `gym=${JSON.stringify(gymB).slice(0,140)}`);

  // cross-service: the applicant is now GYM_OWNER in auth
  r = await call(AUTH, "GET", "/api/users/me", token);
  check("applicant promoted to GYM_OWNER in auth", r.status === 200 && r.body.userType === "GYM_OWNER",
        `userType=${r.body.userType}`);

  r = await call(GYM, "POST", `/api/admin/gym-registrations/${regId}/approve`, adminToken);
  check("re-approve an approved registration → 400", r.status === 400, `status=${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
