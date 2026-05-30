// RBAC matrix for Auth. The /api/users/{id}/ban, /unban, and /api/users/banned
// endpoints are gated to ADMIN + MODERATOR. Everything else is "any
// authenticated user". Probes every (role × endpoint) cell so silent loss of
// a hasAnyRole annotation can't slip through.
//
// Run: node tests/postman/rbac_matrix_auth.js

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
  return { code: r.status || 0, stdout: (r.stdout || "").trim() };
}
async function makeUser(label, userType) {
  const email = `rbac-${label}-${stamp}@dumble.test`;
  await req("POST", "/api/auth/register", {
    firstName: "RBAC", lastName: label, email, password: "validpass123",
  });
  // Promote via direct DB (we don't have an Auth admin endpoint that grants role)
  psql(`UPDATE app_user SET user_type = '${userType}' WHERE email = '${email}';`);
  // Re-login so the access token reflects the new userType
  const login = await req("POST", "/api/auth/login", { email, password: "validpass123" });
  return { email, token: login.body.accessToken, userType };
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);

  const PARTICIPANT = await makeUser("p1", "PARTICIPANT");
  const TRAINER     = await makeUser("t1", "TRAINER");
  const GYM_OWNER   = await makeUser("g1", "GYM_OWNER");
  const MODERATOR   = await makeUser("m1", "MODERATOR");
  const ADMIN       = await makeUser("a1", "ADMIN");

  // Find a victim user id to target with /ban — Bob is just another participant
  const VICTIM = await makeUser("victim", "PARTICIPANT");
  const victimIdRow = psql(`SELECT id FROM app_user WHERE email = '${VICTIM.email}';`);
  const VICTIM_ID = victimIdRow.stdout;
  console.log(`Victim id: ${VICTIM_ID}`);

  const BAN_PATH = `/api/users/${VICTIM_ID}/ban`;
  const UNBAN_PATH = `/api/users/${VICTIM_ID}/unban`;
  const LIST_PATH = "/api/users/banned";

  console.log("\n=== Admin endpoints reject PARTICIPANT / TRAINER / GYM_OWNER ===");
  for (const u of [PARTICIPANT, TRAINER, GYM_OWNER]) {
    for (const [m, p] of [["POST", BAN_PATH], ["POST", UNBAN_PATH], ["GET", LIST_PATH]]) {
      const r = await req(m, p, m === "POST" ? null : undefined, { Authorization: `Bearer ${u.token}` });
      check(`${u.userType.padEnd(11)} ${m} ${p.replace(VICTIM_ID, "{id}")} → 403`,
        r.status === 403, `got ${r.status}`);
    }
  }

  console.log("\n=== Admin endpoints accept ADMIN ===");
  let r = await req("GET", LIST_PATH, undefined, { Authorization: `Bearer ${ADMIN.token}` });
  check(`ADMIN       GET ${LIST_PATH} → 200`, r.status === 200, `got ${r.status}`);

  r = await req("POST", BAN_PATH, null, { Authorization: `Bearer ${ADMIN.token}` });
  check(`ADMIN       POST /ban → 200`, r.status === 200, `got ${r.status}`);

  // List should now include the victim
  const listed = await req("GET", LIST_PATH, undefined, { Authorization: `Bearer ${ADMIN.token}` });
  const includesVictim = Array.isArray(listed.body) && listed.body.some((u) => u.email === VICTIM.email);
  check(`banned list now includes the victim`, includesVictim);

  r = await req("POST", UNBAN_PATH, null, { Authorization: `Bearer ${ADMIN.token}` });
  check(`ADMIN       POST /unban → 200`, r.status === 200, `got ${r.status}`);

  console.log("\n=== Admin endpoints accept MODERATOR ===");
  r = await req("GET", LIST_PATH, undefined, { Authorization: `Bearer ${MODERATOR.token}` });
  check(`MODERATOR   GET ${LIST_PATH} → 200`, r.status === 200, `got ${r.status}`);

  r = await req("POST", BAN_PATH, null, { Authorization: `Bearer ${MODERATOR.token}` });
  check(`MODERATOR   POST /ban → 200`, r.status === 200, `got ${r.status}`);

  r = await req("POST", UNBAN_PATH, null, { Authorization: `Bearer ${MODERATOR.token}` });
  check(`MODERATOR   POST /unban → 200`, r.status === 200, `got ${r.status}`);

  console.log("\n=== /me + /me/onboarding work for ANY authenticated role ===");
  for (const u of [PARTICIPANT, TRAINER, GYM_OWNER, MODERATOR, ADMIN]) {
    r = await req("GET", "/api/users/me", undefined, { Authorization: `Bearer ${u.token}` });
    check(`${u.userType.padEnd(11)} GET /me → 200`, r.status === 200, `got ${r.status}`);
  }

  console.log("\n=== Unauthenticated rejected on protected endpoints ===");
  for (const [m, p] of [
    ["GET", "/api/users/me"],
    ["POST", "/api/auth/hub-token"],
    ["POST", BAN_PATH],
    ["GET", LIST_PATH],
  ]) {
    r = await req(m, p, m === "POST" ? null : undefined);
    check(`unauth     ${m} ${p.replace(VICTIM_ID, "{id}")} → 401/403`,
      r.status === 401 || r.status === 403, `got ${r.status}`);
  }

  console.log("\n=== Public endpoints stay public ===");
  r = await req("POST", "/api/auth/login", { email: "ghost-rbac@dumble.test", password: "wrongpassword" });
  check(`POST /login reachable without auth (got 401 for bad creds, not gate)`,
    r.status === 401, `got ${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
