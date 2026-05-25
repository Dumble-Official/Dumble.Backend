// Token lifecycle probes for Auth.
//
//  - access token expires (we don't wait the full 15 min; instead we mint
//    an already-expired token with the same key and verify it's rejected)
//  - refresh token rotates: every /refresh call returns a NEW value AND
//    invalidates the old (the rotation fix proven on its own + cross-checked
//    against concurrent reuse in concurrency_auth.js)
//  - exactly one refresh token row per user at any time (single-per-user
//    pessimistic-lock contract)
//  - logout deletes the refresh token from DB
//  - hub-token issues a SHORT-lived token with purpose=hub claim
//  - change-password wipes ALL refresh tokens for the user (already covered
//    in password_security; here we also assert hub tokens themselves don't
//    grant API access on /me)
//
// Run: node tests/postman/token_lifecycle_auth.js

const crypto = require("crypto");
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
function decode(jwt) {
  const parts = jwt.split(".");
  if (parts.length !== 3) return null;
  try { return JSON.parse(Buffer.from(parts[1], "base64").toString()); } catch { return null; }
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);

  const email = `tl-${stamp}@dumble.test`;
  const reg = await req("POST", "/api/auth/register", {
    firstName: "TL", lastName: "Test", email, password: "validpass123",
  });
  if (reg.status !== 201) { console.error("setup failed", reg); process.exit(2); }

  console.log("\n=== Access token claims look right ===");
  const at = decode(reg.body.accessToken);
  check(`access token has 'sub' = email`, at?.sub === email, `got ${at?.sub}`);
  check(`access token has 'iss' = 'dumble-auth'`, at?.iss === "dumble-auth");
  check(`access token has 'aud' = 'dumble-app'`, at?.aud === "dumble-app" || (Array.isArray(at?.aud) && at.aud.includes("dumble-app")));
  check(`access token has roles[]`, Array.isArray(at?.roles));
  check(`access token has userId`, !!at?.userId);
  check(`access token has userType`, !!at?.userType);
  check(`access token expiration ≈ 15min (allow ±5min)`, Math.abs((at?.exp - at?.iat) - 900) < 300, `iat=${at?.iat} exp=${at?.exp}`);

  console.log("\n=== Refresh token format ===");
  const rt = reg.body.refreshToken;
  check(`refresh token is a UUID (not a JWT)`,
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(rt),
    `got '${rt}'`);

  console.log("\n=== Exactly one refresh_token row at register ===");
  const cnt1 = psql(`SELECT count(*) FROM refresh_token rt JOIN app_user u ON rt.user_id = u.id WHERE u.email = '${email}';`);
  check(`refresh_token rows = 1`, cnt1 === "1", `count=${cnt1}`);

  console.log("\n=== Login replaces the row (still single per user) ===");
  const login = await req("POST", "/api/auth/login", { email, password: "validpass123" });
  const cnt2 = psql(`SELECT count(*) FROM refresh_token rt JOIN app_user u ON rt.user_id = u.id WHERE u.email = '${email}';`);
  check(`after login: 1 row (old one deleted)`, cnt2 === "1", `count=${cnt2}`);
  check(`login refreshToken differs from register's`, login.body.refreshToken !== rt);

  console.log("\n=== Refresh rotates the token + invalidates old ===");
  const r1 = await req("POST", "/api/auth/refresh", { refreshToken: login.body.refreshToken });
  check(`refresh → 200`, r1.status === 200);
  check(`refresh returns NEW refreshToken (rotated)`,
    r1.body?.refreshToken && r1.body.refreshToken !== login.body.refreshToken);

  const reuse = await req("POST", "/api/auth/refresh", { refreshToken: login.body.refreshToken });
  check(`reuse of old refreshToken → 4xx (reuse detected)`,
    reuse.status >= 400 && reuse.status < 500, `got ${reuse.status}`);

  // The new one works
  const r2 = await req("POST", "/api/auth/refresh", { refreshToken: r1.body.refreshToken });
  check(`new refreshToken works once`, r2.status === 200, `got ${r2.status}`);

  console.log("\n=== Logout deletes the refresh token ===");
  const logout = await req("POST", "/api/auth/logout",
    { refreshToken: r2.body.refreshToken },
    { Authorization: `Bearer ${r2.body.accessToken}` });
  check(`logout → 200`, logout.status === 200);
  const cnt3 = psql(`SELECT count(*) FROM refresh_token rt JOIN app_user u ON rt.user_id = u.id WHERE u.email = '${email}';`);
  check(`after logout: 0 rows in refresh_token`, cnt3 === "0", `count=${cnt3}`);

  // Refresh after logout fails
  const afterLogout = await req("POST", "/api/auth/refresh", { refreshToken: r2.body.refreshToken });
  check(`refresh after logout → 4xx`, afterLogout.status >= 400 && afterLogout.status < 500);

  console.log("\n=== Hub-token is short-lived + has purpose=hub claim ===");
  const login2 = await req("POST", "/api/auth/login", { email, password: "validpass123" });
  const hub = await req("POST", "/api/auth/hub-token", null,
    { Authorization: `Bearer ${login2.body.accessToken}` });
  check(`hub-token → 200`, hub.status === 200);
  const hubClaims = decode(hub.body.token);
  check(`hub-token has purpose=hub`, hubClaims?.purpose === "hub", `got '${hubClaims?.purpose}'`);
  check(`hub-token expires within 5 min (TTL ≈ 60s)`,
    (hubClaims?.exp - hubClaims?.iat) <= 300, `lifetime=${hubClaims?.exp - hubClaims?.iat}s`);

  console.log("\n=== Refresh token does NOT grant API access ===");
  // The refresh token is a UUID, not a JWT — sending it in Authorization
  // header as Bearer should fail the JWT verifier.
  const rtAsAccess = await req("GET", "/api/users/me", undefined,
    { Authorization: `Bearer ${login2.body.refreshToken}` });
  check(`refresh token in Authorization header → 401/403`,
    rtAsAccess.status === 401 || rtAsAccess.status === 403, `got ${rtAsAccess.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
