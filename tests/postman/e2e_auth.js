// End-to-end happy-path through Auth.
//
//   register → /me → onboarding → patch profile → refresh → hub-token →
//   change-password → re-login with new password → logout → refresh fails
//
// Run: node tests/postman/e2e_auth.js

const AUTH_URL = process.env.AUTH_HOST_URL || "http://localhost:18081";
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

(async () => {
  console.log(`Auth: ${AUTH_URL}`);
  const email = `e2e-${stamp}@dumble.test`;
  const pw1 = "first-password-123";
  const pw2 = "second-password-456";

  console.log("\n=== 1. Register ===");
  const reg = await req("POST", "/api/auth/register", {
    firstName: "E2E", lastName: "User", email, password: pw1,
  });
  check(`register → 201`, reg.status === 201, `got ${reg.status}`);
  let access = reg.body.accessToken;
  let refresh = reg.body.refreshToken;
  const userId = reg.body.user?.id;
  check(`user id assigned`, !!userId);

  console.log("\n=== 2. GET /me reflects the new user ===");
  const me1 = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${access}` });
  check(`/me → 200`, me1.status === 200);
  check(`/me.email matches`, me1.body?.email === email);
  check(`/me.userType = PARTICIPANT`, me1.body?.userType === "PARTICIPANT");

  console.log("\n=== 3. Complete onboarding ===");
  const onb = await req("PATCH", "/api/users/me/onboarding",
    { dateOfBirth: "1995-06-15", gender: "MALE", weight: 80.0, height: 178.0, fitnessGoals: "STRENGTH" },
    { Authorization: `Bearer ${access}` });
  check(`onboarding → 200`, onb.status === 200, `got ${onb.status}`);
  check(`weight stored`, onb.body?.weight === 80.0);

  console.log("\n=== 4. Update profile ===");
  const upd = await req("PATCH", "/api/users/me",
    { displayName: "E2E Tester", bio: "I run end-to-end suites for a living" },
    { Authorization: `Bearer ${access}` });
  check(`profile update → 200`, upd.status === 200);
  check(`displayName persisted`, upd.body?.displayName === "E2E Tester");

  console.log("\n=== 5. Refresh tokens (rotation) ===");
  const r1 = await req("POST", "/api/auth/refresh", { refreshToken: refresh });
  check(`refresh → 200`, r1.status === 200);
  check(`new accessToken differs`, r1.body?.accessToken && r1.body.accessToken !== access);
  check(`new refreshToken differs (rotated)`, r1.body?.refreshToken && r1.body.refreshToken !== refresh);
  access = r1.body.accessToken;
  refresh = r1.body.refreshToken;

  console.log("\n=== 6. Hub token ===");
  const hub = await req("POST", "/api/auth/hub-token", null,
    { Authorization: `Bearer ${access}` });
  check(`hub-token → 200`, hub.status === 200);
  check(`hub token present`, !!hub.body?.token);

  console.log("\n=== 7. Change password ===");
  const cp = await req("POST", "/api/auth/change-password",
    { currentPassword: pw1, newPassword: pw2 },
    { Authorization: `Bearer ${access}` });
  check(`change-password → 200`, cp.status === 200);

  // Old refresh token invalidated
  const oldRtFail = await req("POST", "/api/auth/refresh", { refreshToken: refresh });
  check(`old refresh after change-password → 4xx`,
    oldRtFail.status >= 400 && oldRtFail.status < 500);

  console.log("\n=== 8. Login with new password ===");
  const reLogin = await req("POST", "/api/auth/login", { email, password: pw2 });
  check(`login with new password → 200`, reLogin.status === 200);

  const oldPwFail = await req("POST", "/api/auth/login", { email, password: pw1 });
  check(`login with old password → 401`, oldPwFail.status === 401);

  console.log("\n=== 9. Logout ===");
  const logout = await req("POST", "/api/auth/logout",
    { refreshToken: reLogin.body.refreshToken },
    { Authorization: `Bearer ${reLogin.body.accessToken}` });
  check(`logout → 200`, logout.status === 200);

  const postLogoutRefresh = await req("POST", "/api/auth/refresh",
    { refreshToken: reLogin.body.refreshToken });
  check(`refresh after logout → 4xx (token deleted)`,
    postLogoutRefresh.status >= 400 && postLogoutRefresh.status < 500);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
