// Security probes for Auth — JWT manipulation, signature, expiry, alg
// confusion, role-claim escalation. The Auth service issues its OWN access
// tokens, so the security boundary worth probing is: does it correctly
// REJECT externally-minted tokens that don't satisfy the signature + expiry
// contract.
//
// Run: node tests/postman/security_auth.js

const mint = require("./mint_auth_jwts");

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

  // First — register a real user so we have an account to target.
  const email = `sec-${stamp}@dumble.test`;
  const reg = await req("POST", "/api/auth/register", {
    firstName: "Sec", lastName: "Test", email, password: "validpass123",
  });
  if (reg.status !== 201) {
    console.error("setup register failed:", reg.status, reg.body);
    process.exit(2);
  }
  const realToken = reg.body.accessToken;

  console.log("\n=== Token signature / authenticity ===");
  // Real token works:
  let r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${realToken}` });
  check("real access token → 200", r.status === 200, `got ${r.status}`);

  // Wrong-key token (well-formed shape, wrong HMAC):
  r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${mint.wrongKeyToken(email)}` });
  check("wrong-signing-key token → 401/403", r.status === 401 || r.status === 403, `got ${r.status}`);

  // Truncated/garbage signature:
  const tampered = realToken.slice(0, -10) + "AAAAAAAAAA";
  r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${tampered}` });
  check("tampered signature → 401/403", r.status === 401 || r.status === 403, `got ${r.status}`);

  // Garbage between header.payload.sig
  r = await req("GET", "/api/users/me", undefined, { Authorization: "Bearer aaaa.bbbb.cccc" });
  check("garbage JWT → 401/403", r.status === 401 || r.status === 403, `got ${r.status}`);

  console.log("\n=== Expiry ===");
  r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${mint.expired(email)}` });
  check("expired token → 401/403", r.status === 401 || r.status === 403, `got ${r.status}`);

  console.log("\n=== Algorithm confusion (alg=none) ===");
  // RFC 7519 explicitly says verifiers MUST reject `alg: none` when they
  // require signed JWTs. jjwt rejects it by default; this asserts the
  // version in use isn't accidentally permissive.
  r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${mint.algNone(email)}` });
  check("alg=none token rejected → 401/403", r.status === 401 || r.status === 403, `got ${r.status}`);

  console.log("\n=== Role-claim escalation ===");
  // A legitimately-signed token (HS256 + same JWT_SECRET) but with the
  // attacker setting roles=[ROLE_ADMIN] in the payload. JwtAuthenticationFilter
  // calls userDetailsService.loadUserByUsername(email) → authorities derived
  // from DB userType, NOT from the token's roles claim. So this token should
  // NOT grant ADMIN access to /api/users/banned.
  const escalated = mint.escalated(email);
  r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${escalated}` });
  // /me works since the user IS authenticated as themselves; what shouldn't
  // work is ADMIN-gated endpoints.
  check("escalated token can hit /me (auth still works)", r.status === 200, `got ${r.status}`);

  r = await req("GET", "/api/users/banned", undefined, { Authorization: `Bearer ${escalated}` });
  check("escalated token CANNOT reach /api/users/banned (DB-derived role gate)",
    r.status === 403, `got ${r.status} — if 200, role gate is reading token claims, not DB`);

  console.log("\n=== Missing / malformed Authorization header ===");
  r = await req("GET", "/api/users/me");
  check("no Authorization → 401/403", r.status === 401 || r.status === 403);

  r = await req("GET", "/api/users/me", undefined, { Authorization: "NotBearer xxx" });
  check("non-Bearer auth scheme → 401/403", r.status === 401 || r.status === 403);

  r = await req("GET", "/api/users/me", undefined, { Authorization: "Bearer " });
  check("empty Bearer token → 401/403", r.status === 401 || r.status === 403);

  console.log("\n=== Token-for-different-user ===");
  // A token for someone who doesn't exist in the DB
  const ghost = mint.mintAccess(`ghost-${stamp}@dumble.test`);
  r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${ghost}` });
  check("token for non-existent user → 401/403", r.status === 401 || r.status === 403, `got ${r.status}`);

  console.log("\n=== Public endpoints still public ===");
  r = await req("POST", "/api/auth/register", {
    firstName: "Pub", lastName: "Lic", email: `pub-${stamp}@dumble.test`,
    password: "validpass123",
  });
  check("register works without any auth header → 201", r.status === 201, `got ${r.status}`);

  r = await req("POST", "/api/auth/login", { email, password: "validpass123" });
  check("login works without any auth header → 200", r.status === 200, `got ${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
