// Contract suite for the Auth service. Hits every endpoint with the
// expected happy / sad inputs and validates response shape + status.
//
// Run: node tests/postman/contract_auth.js

const crypto = require("crypto");

const AUTH_URL = process.env.AUTH_HOST_URL || "http://localhost:18081";
const stamp = Date.now();

let ok = 0, fail = 0;
function check(label, cond, detail) {
  if (cond) { ok++; console.log(`  ✓ ${label}`); }
  else      { fail++; console.log(`  ✗ ${label}${detail ? " — " + detail : ""}`); }
}

async function req(method, path, body, headers = {}) {
  const opts = { method, headers: { ...headers } };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = typeof body === "string" ? body : JSON.stringify(body);
  }
  const res = await fetch(`${AUTH_URL}${path}`, opts);
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

async function register(suffix = "") {
  const email = `contract-${stamp}-${suffix}@dumble.test`;
  return {
    email,
    res: await req("POST", "/api/auth/register", {
      firstName: "Con", lastName: "Tract",
      email, password: "validpass123",
    }),
  };
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);

  // ─── /api/auth/register ───
  console.log("\n=== register ===");
  const r1 = await register("a");
  check("happy register → 201", r1.res.status === 201, `got ${r1.res.status}`);
  check("response carries accessToken", !!r1.res.body?.accessToken, "no token");
  check("response carries refreshToken", !!r1.res.body?.refreshToken);
  check("response.user has email + userType", r1.res.body?.user?.email === r1.email && r1.res.body?.user?.userType === "PARTICIPANT");

  const dup = await req("POST", "/api/auth/register", {
    firstName: "X", lastName: "Y", email: r1.email, password: "validpass123",
  });
  check("duplicate email → 4xx (not 500)", dup.status >= 400 && dup.status < 500, `got ${dup.status}`);
  check("duplicate response doesn't leak which email was taken", /already registered|in use/i.test(JSON.stringify(dup.body)) === false);

  const weak = await req("POST", "/api/auth/register", {
    firstName: "X", lastName: "Y", email: `weak-${stamp}@dumble.test`, password: "short",
  });
  check("weak password (< 8 chars) → 400", weak.status === 400, `got ${weak.status}`);

  const badEmail = await req("POST", "/api/auth/register", {
    firstName: "X", lastName: "Y", email: "not-an-email", password: "validpass123",
  });
  check("invalid email format → 400", badEmail.status === 400, `got ${badEmail.status}`);

  const missingFields = await req("POST", "/api/auth/register", {});
  check("missing required fields → 400", missingFields.status === 400, `got ${missingFields.status}`);

  // ─── /api/auth/login ───
  console.log("\n=== login ===");
  const loginOk = await req("POST", "/api/auth/login", {
    email: r1.email, password: "validpass123",
  });
  check("happy login → 200", loginOk.status === 200, `got ${loginOk.status}`);
  check("login response carries accessToken + refreshToken",
    !!loginOk.body?.accessToken && !!loginOk.body?.refreshToken);

  const wrongPass = await req("POST", "/api/auth/login", {
    email: r1.email, password: "wrong-password",
  });
  check("wrong password → 401", wrongPass.status === 401, `got ${wrongPass.status}`);

  const noUser = await req("POST", "/api/auth/login", {
    email: `ghost-${stamp}@dumble.test`, password: "validpass123",
  });
  check("unknown email → 401 (no enumeration via 404)", noUser.status === 401, `got ${noUser.status}`);

  // ─── /api/auth/refresh ───
  console.log("\n=== refresh ===");
  // JWT iat is at second precision — sleep 1.1s so the refreshed token has a
  // distinct iat from the login token (otherwise both serialize identically
  // and the equality check below is a false-positive).
  await new Promise(r => setTimeout(r, 1100));
  const refreshOk = await req("POST", "/api/auth/refresh", {
    refreshToken: loginOk.body.refreshToken,
  });
  check("happy refresh → 200", refreshOk.status === 200, `got ${refreshOk.status}`);
  check("refresh issues a NEW access token (distinct iat)",
    refreshOk.body?.accessToken && refreshOk.body.accessToken !== loginOk.body.accessToken);
  check("refresh ROTATES the refresh token (new value)",
    refreshOk.body?.refreshToken && refreshOk.body.refreshToken !== loginOk.body.refreshToken);

  const badRefresh = await req("POST", "/api/auth/refresh", {
    refreshToken: "not-a-real-token",
  });
  check("invalid refresh token → 4xx", badRefresh.status >= 400 && badRefresh.status < 500);

  // ─── /api/auth/google with malformed token ───
  console.log("\n=== google ===");
  const badGoogle = await req("POST", "/api/auth/google", { idToken: "garbage" });
  check("malformed Google idToken → 400", badGoogle.status === 400, `got ${badGoogle.status}`);

  // ─── /api/users/me ───
  console.log("\n=== /users/me ===");
  // Use the rotated refresh-token's access token (refreshOk).
  const meOk = await req("GET", "/api/users/me", undefined, {
    Authorization: `Bearer ${refreshOk.body.accessToken}`,
  });
  check("happy /me → 200", meOk.status === 200, `got ${meOk.status}`);
  check("/me returns caller's email", meOk.body?.email === r1.email);

  const meNoAuth = await req("GET", "/api/users/me");
  check("/me with no Authorization → 401/403", meNoAuth.status === 401 || meNoAuth.status === 403);

  const meBad = await req("GET", "/api/users/me", undefined, {
    Authorization: "Bearer not-a-jwt",
  });
  check("/me with garbage token → 401/403", meBad.status === 401 || meBad.status === 403);

  // ─── /api/users/me/onboarding ───
  console.log("\n=== /users/me/onboarding ===");
  const onb = await req("PATCH", "/api/users/me/onboarding",
    { weight: 75.5, height: 180.0, gender: "MALE" },
    { Authorization: `Bearer ${refreshOk.body.accessToken}` });
  check("onboarding patch → 200", onb.status === 200, `got ${onb.status}`);
  check("onboarding response carries the new weight", onb.body?.weight === 75.5);

  const onbFutureDob = await req("PATCH", "/api/users/me/onboarding",
    { dateOfBirth: "2099-01-01" },
    { Authorization: `Bearer ${refreshOk.body.accessToken}` });
  check("future DOB rejected → 400", onbFutureDob.status === 400, `got ${onbFutureDob.status}`);

  // ─── /api/users/me (PATCH update profile) ───
  console.log("\n=== /users/me (PATCH) ===");
  const upd = await req("PATCH", "/api/users/me",
    { displayName: "Updated Display", bio: "hello world" },
    { Authorization: `Bearer ${refreshOk.body.accessToken}` });
  check("profile update → 200", upd.status === 200, `got ${upd.status}`);
  check("display name persisted", upd.body?.displayName === "Updated Display");

  // ─── /api/auth/change-password ───
  console.log("\n=== change-password ===");
  const cpOk = await req("POST", "/api/auth/change-password", {
    currentPassword: "validpass123", newPassword: "newvalidpass456",
  }, { Authorization: `Bearer ${refreshOk.body.accessToken}` });
  check("change-password → 200", cpOk.status === 200, `got ${cpOk.status}`);

  const cpWrong = await req("POST", "/api/auth/change-password", {
    currentPassword: "wrong-current", newPassword: "newvalidpass789",
  }, { Authorization: `Bearer ${refreshOk.body.accessToken}` });
  check("change-password with wrong current → 400/401", cpWrong.status === 400 || cpWrong.status === 401);

  // After change, old refresh tokens are wiped — fresh login required
  const loginNewPass = await req("POST", "/api/auth/login", {
    email: r1.email, password: "newvalidpass456",
  });
  check("login with new password works", loginNewPass.status === 200, `got ${loginNewPass.status}`);

  const loginOldPass = await req("POST", "/api/auth/login", {
    email: r1.email, password: "validpass123",
  });
  check("login with old password fails", loginOldPass.status === 401, `got ${loginOldPass.status}`);

  // ─── /api/auth/hub-token ───
  console.log("\n=== hub-token ===");
  const hubOk = await req("POST", "/api/auth/hub-token", null,
    { Authorization: `Bearer ${loginNewPass.body.accessToken}` });
  check("hub-token → 200", hubOk.status === 200, `got ${hubOk.status}`);
  check("hub-token response has 'token'", !!hubOk.body?.token);

  const hubNoAuth = await req("POST", "/api/auth/hub-token");
  check("hub-token unauthenticated → 401/403", hubNoAuth.status === 401 || hubNoAuth.status === 403);

  // ─── /api/auth/logout ───
  console.log("\n=== logout ===");
  const logoutOk = await req("POST", "/api/auth/logout",
    { refreshToken: loginNewPass.body.refreshToken },
    { Authorization: `Bearer ${loginNewPass.body.accessToken}` });
  check("logout → 200", logoutOk.status === 200, `got ${logoutOk.status}`);

  const refreshAfterLogout = await req("POST", "/api/auth/refresh", {
    refreshToken: loginNewPass.body.refreshToken,
  });
  check("refresh after logout → 4xx (token invalidated)",
    refreshAfterLogout.status >= 400 && refreshAfterLogout.status < 500);

  // ─── Framework-level errors (the GlobalExceptionHandler fix) ───
  console.log("\n=== framework error mapping (handler fix verified) ===");
  const malformed = await req("POST", "/api/auth/login", "{not json");
  check("malformed JSON → 400 (not 500)", malformed.status === 400, `got ${malformed.status}`);

  const wrongCT = await fetch(`${AUTH_URL}/api/auth/login`, {
    method: "POST", headers: { "Content-Type": "application/xml" }, body: "<x/>",
  });
  check("wrong Content-Type → 415", wrongCT.status === 415, `got ${wrongCT.status}`);

  const wrongMethod = await req("GET", "/api/auth/login");
  check("wrong method (GET on POST endpoint) → 405", wrongMethod.status === 405, `got ${wrongMethod.status}`);

  // Unknown endpoints — Spring Security's `.anyRequest().authenticated()`
  // intercepts BEFORE the dispatcher servlet, so unauthenticated callers
  // see 403 (auth gate) rather than 404. That's actually a small
  // information-disclosure win (attackers can't enumerate routes anonymously).
  // Authenticated callers should get 404 via the NoResourceFoundException
  // handler.
  const notFound = await req("GET", "/api/this-endpoint-does-not-exist");
  check("unknown endpoint unauthenticated → 401/403/404",
    notFound.status === 401 || notFound.status === 403 || notFound.status === 404,
    `got ${notFound.status}`);
  // With a valid token, unknown endpoint should reach the NoResourceFound handler
  const authedNotFound = await req("GET", "/api/this-endpoint-does-not-exist", undefined,
    { Authorization: `Bearer ${loginNewPass.body.accessToken}` });
  check("unknown endpoint (authenticated) → 404",
    authedNotFound.status === 404, `got ${authedNotFound.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
