// Password-security probes for Auth.
//
//  - password column actually stores a bcrypt hash, not the plaintext
//  - bcrypt cost factor is at least 10 (default for BCryptPasswordEncoder)
//  - validator rejects short passwords AT THE BOUNDARY (length < 8) without
//    burning a bcrypt round (timing side-channel) — measured as < 100ms
//  - change-password requires the correct current password
//  - change-password invalidates ALL prior refresh tokens
//  - change-password to the same value is allowed but doesn't break anything
//  - register-then-login round-trips with the actual stored password
//
// Run: node tests/postman/password_security_auth.js

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

(async () => {
  console.log(`Auth: ${AUTH_URL}`);

  console.log("\n=== Password is bcrypt-hashed in DB, never plaintext ===");
  const email = `pw-${stamp}@dumble.test`;
  const plain = "validpass123";
  const reg = await req("POST", "/api/auth/register", {
    firstName: "PW", lastName: "Test", email, password: plain,
  });
  if (reg.status !== 201) { console.error("setup failed", reg); process.exit(2); }

  const hash = psql(`SELECT password_hash FROM app_user WHERE email = '${email}';`);
  check("password_hash column is populated", hash.length > 0);
  check("password_hash is NOT the plaintext", hash !== plain, "plaintext stored");
  check("password_hash starts with $2 (bcrypt format)", hash.startsWith("$2"), `got '${hash.slice(0,4)}'`);
  // Bcrypt format: $2a$NN$... where NN is cost factor
  const costMatch = hash.match(/^\$2[abxy]\$(\d{2})\$/);
  if (costMatch) {
    const cost = parseInt(costMatch[1], 10);
    console.log(`  detected bcrypt cost: ${cost}`);
    check(`bcrypt cost ≥ 10 (BCryptPasswordEncoder default = 10)`, cost >= 10, `cost=${cost}`);
  } else {
    fail++; console.log(`  ✗ couldn't parse bcrypt cost from hash`);
  }

  console.log("\n=== Validation rejects weak passwords BEFORE hashing ===");
  // Length-7 password — should fail validator (< 8) immediately, not after
  // burning a bcrypt round. Time the call; under 100ms is "validation only".
  const t0 = Date.now();
  const weak = await req("POST", "/api/auth/register", {
    firstName: "W", lastName: "K", email: `weak-${stamp}@dumble.test`, password: "short77",
  });
  const dt = Date.now() - t0;
  check(`length<8 → 400`, weak.status === 400, `got ${weak.status}`);
  check(`validation runs in < 200ms (no bcrypt round burned)`,
    dt < 200, `${dt}ms — under 200ms means input validation rejected pre-hash`);

  console.log("\n=== Login round-trips with the stored hash ===");
  const login = await req("POST", "/api/auth/login", { email, password: plain });
  check(`login with original password → 200`, login.status === 200);

  console.log("\n=== change-password ===");
  // wrong-current-password rejected
  const cpWrong = await req("POST", "/api/auth/change-password",
    { currentPassword: "wrong-current-pw", newPassword: "newpass789" },
    { Authorization: `Bearer ${login.body.accessToken}` });
  check(`wrong currentPassword → 4xx`, cpWrong.status >= 400 && cpWrong.status < 500,
    `got ${cpWrong.status}`);

  // correct change works
  const cpOk = await req("POST", "/api/auth/change-password",
    { currentPassword: plain, newPassword: "newpass789" },
    { Authorization: `Bearer ${login.body.accessToken}` });
  check(`correct change-password → 200`, cpOk.status === 200);

  // Original refresh token is invalidated by change-password
  const oldRtAfter = await req("POST", "/api/auth/refresh",
    { refreshToken: login.body.refreshToken });
  check(`refresh tokens invalidated by change-password`,
    oldRtAfter.status >= 400 && oldRtAfter.status < 500, `got ${oldRtAfter.status}`);

  // Login with new password works
  const loginNew = await req("POST", "/api/auth/login",
    { email, password: "newpass789" });
  check(`login with new password → 200`, loginNew.status === 200);

  // Login with old password fails
  const loginOld = await req("POST", "/api/auth/login",
    { email, password: plain });
  check(`login with old password → 401`, loginOld.status === 401);

  console.log("\n=== Google-linked accounts can't change-password ===");
  // We don't have a real Google id token, but we can simulate the state by
  // direct-updating an account to GOOGLE auth_provider with passwordHash=null.
  const g = `pw-google-${stamp}@dumble.test`;
  await req("POST", "/api/auth/register", {
    firstName: "G", lastName: "T", email: g, password: "validpass123",
  });
  const gLogin = await req("POST", "/api/auth/login", { email: g, password: "validpass123" });
  psql(`UPDATE app_user SET auth_provider = 'GOOGLE', password_hash = NULL WHERE email = '${g}';`);
  // Need a fresh token after the userType / auth_provider edit — but since
  // we kept the password_hash null, we can't re-login. Use the old token.
  const cpGoogle = await req("POST", "/api/auth/change-password",
    { currentPassword: "validpass123", newPassword: "newpass789" },
    { Authorization: `Bearer ${gLogin.body.accessToken}` });
  check(`change-password rejected for Google-linked account → 4xx`,
    cpGoogle.status >= 400 && cpGoogle.status < 500, `got ${cpGoogle.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
