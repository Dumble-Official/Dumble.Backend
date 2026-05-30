// Cross-user isolation probes for Auth.
//
//  - User A's /me endpoint reflects A's data only — never B's.
//  - User A's token cannot be used to log out B (logout body's refresh token
//    must belong to the authenticated principal).
//  - User A's change-password updates A's row only — B's password unchanged.
//  - User A patching /me only changes A's row.
//
// Run: node tests/postman/multi_tenant_isolation_auth.js

const { spawnSync } = require("child_process");

const AUTH_URL = process.env.AUTH_HOST_URL || "http://localhost:18081";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];
const stamp = Date.now();

let ok = 0, fail = 0;
function check(label, cond, detail) {
  if (cond) { ok++; console.log(`  ✓ ${label}`); }
  else      { fail++; console.log(`  ✗ ${label}${detail ? " — " + detail : ""}`); }
}

async function req(method, path, body, headers = {}) {
  const opts = { method, headers };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(`${AUTH_URL}${path}`, opts);
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "db", "psql",
    "-U", "postgres", "-d", "dumble_project", "-tA", "-c", sql], { encoding: "utf8" });
  return (r.stdout || "").trim();
}
async function register(suffix) {
  const email = `mt-${suffix}-${stamp}@dumble.test`;
  const res = await req("POST", "/api/auth/register", {
    firstName: "MT", lastName: suffix, email, password: "validpass123",
  });
  if (res.status !== 201) throw new Error(`register failed for ${email}: ${res.status}`);
  return { email, token: res.body.accessToken, refresh: res.body.refreshToken, body: res.body };
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);
  const A = await register("alice");
  const B = await register("bob");
  console.log(`Alice: ${A.email}`);
  console.log(`Bob:   ${B.email}`);

  console.log("\n=== /me reflects caller identity ===");
  const meA = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${A.token}` });
  const meB = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${B.token}` });
  check("A.token → /me returns A", meA.body?.email === A.email);
  check("B.token → /me returns B", meB.body?.email === B.email);
  check("/me responses differ across users", JSON.stringify(meA.body) !== JSON.stringify(meB.body));

  console.log("\n=== A's token cannot logout B's refresh token ===");
  const wrongLogout = await req("POST", "/api/auth/logout",
    { refreshToken: B.refresh },
    { Authorization: `Bearer ${A.token}` });
  check("A.token + B.refresh on /logout → 4xx",
    wrongLogout.status >= 400 && wrongLogout.status < 500,
    `got ${wrongLogout.status} — if 200, A just invalidated B's session`);

  // B's refresh token should still work since A's attempt was rejected
  const bRefreshOk = await req("POST", "/api/auth/refresh", { refreshToken: B.refresh });
  check("B's refresh still works after the attempted hijack",
    bRefreshOk.status === 200, `got ${bRefreshOk.status}`);
  const bAccessNew = bRefreshOk.body.accessToken;
  const bRefreshNew = bRefreshOk.body.refreshToken;

  console.log("\n=== A's change-password only affects A ===");
  const cpA = await req("POST", "/api/auth/change-password",
    { currentPassword: "validpass123", newPassword: "alice-new-pw-789" },
    { Authorization: `Bearer ${A.token}` });
  check("A change-password → 200", cpA.status === 200, `got ${cpA.status}`);

  // B can still log in with original password
  const loginB = await req("POST", "/api/auth/login",
    { email: B.email, password: "validpass123" });
  check("B can still log in with original password", loginB.status === 200);

  // A's old password is now rejected
  const loginAOld = await req("POST", "/api/auth/login",
    { email: A.email, password: "validpass123" });
  check("A's old password rejected", loginAOld.status === 401);

  console.log("\n=== A's profile patch only affects A's row ===");
  // re-login A
  const loginAfresh = await req("POST", "/api/auth/login",
    { email: A.email, password: "alice-new-pw-789" });
  const aFreshToken = loginAfresh.body.accessToken;

  const patchA = await req("PATCH", "/api/users/me",
    { displayName: "Alice-Display", bio: "Hello from Alice" },
    { Authorization: `Bearer ${aFreshToken}` });
  check("A patch /me → 200", patchA.status === 200);

  // B's profile is unchanged
  const meB2 = await req("GET", "/api/users/me", undefined,
    { Authorization: `Bearer ${bAccessNew}` });
  check("B's displayName is NOT 'Alice-Display'", meB2.body?.displayName !== "Alice-Display");
  check("B's bio is NOT 'Hello from Alice'", meB2.body?.bio !== "Hello from Alice");

  console.log("\n=== privilege escalation via PATCH body is blocked ===");
  // Try to set userType=ADMIN via PATCH body. UpdateProfileRequest doesn't
  // expose that field, so the value should be ignored by Jackson.
  const patchEscalate = await req("PATCH", "/api/users/me",
    { displayName: "Hax", userType: "ADMIN", isActive: true, roles: ["ROLE_ADMIN"] },
    { Authorization: `Bearer ${aFreshToken}` });
  // A's userType in the DB
  const aType = psql(`SELECT user_type FROM app_user WHERE email = '${A.email}';`);
  check("A's DB userType still PARTICIPANT after attempted mass-assignment",
    aType === "PARTICIPANT", `got '${aType}'`);

  // And ADMIN endpoints still reject A
  const banAttempt = await req("POST", `/api/users/${meB2.body.id}/ban`, null,
    { Authorization: `Bearer ${aFreshToken}` });
  check("A still rejected from /ban after the escalation attempt",
    banAttempt.status === 403, `got ${banAttempt.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
