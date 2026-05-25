// Concurrency probes for Auth.
//
//  I1: K concurrent registrations of the same email — exactly 1 succeeds,
//      all others get 4xx with the same generic message (not a 500 from
//      the unique-constraint violation surfacing through). Verifies the
//      DataIntegrityViolation→409 fix.
//  I2: M concurrent refresh calls for the same user — race serialized by
//      the FOR-UPDATE lock on the user row; exactly 1 row left in
//      refresh_token table at the end.
//  I3: Sequential refresh of the SAME (rotated) refresh token twice in a
//      row → second call fails because the first rotated the value away.
//      Verifies the rotation fix.
//
// Run: node tests/postman/concurrency_auth.js

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
  return { code: r.status || 0, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);

  // ─── I1: register race ───
  console.log("\n=== I1: 8 concurrent registrations of the same email ===");
  const raceEmail = `race-${stamp}@dumble.test`;
  const racers = Array.from({ length: 8 }, () =>
    req("POST", "/api/auth/register", {
      firstName: "Race", lastName: "Test", email: raceEmail, password: "validpass123",
    }));
  const raceResults = await Promise.all(racers);
  const status2xx = raceResults.filter(r => r.status >= 200 && r.status < 300).length;
  const status4xx = raceResults.filter(r => r.status >= 400 && r.status < 500).length;
  const status5xx = raceResults.filter(r => r.status >= 500).length;
  console.log(`  status counts: 2xx=${status2xx} 4xx=${status4xx} 5xx=${status5xx}`);
  check(`exactly 1 winner`, status2xx === 1, `got ${status2xx}`);
  check(`no 5xx (DataIntegrityViolation translated to 4xx)`, status5xx === 0, `got ${status5xx} 5xx`);
  check(`losers got 4xx`, status4xx === raceResults.length - 1);

  // DB cross-check: exactly one user row
  const userCount = psql(`SELECT count(*) FROM app_user WHERE email = '${raceEmail}';`);
  check(`exactly 1 app_user row materialized`, userCount.stdout === "1", `count=${userCount.stdout}`);

  // ─── I2: refresh race ───
  console.log("\n=== I2: 6 concurrent refresh calls for the same user ===");
  const u2 = `race-refresh-${stamp}@dumble.test`;
  const reg = await req("POST", "/api/auth/register", {
    firstName: "Race", lastName: "Refresh", email: u2, password: "validpass123",
  });
  if (reg.status !== 201) {
    console.error("setup register failed:", reg.status); process.exit(2);
  }
  const initialRefresh = reg.body.refreshToken;

  // Fire 6 concurrent refreshes. Some will succeed (with rotation), some
  // will lose the race and get 4xx "Invalid refresh token" because the
  // winner already deleted the row they're trying to use.
  const refreshers = Array.from({ length: 6 }, () =>
    req("POST", "/api/auth/refresh", { refreshToken: initialRefresh }));
  const refreshResults = await Promise.all(refreshers);
  const okR = refreshResults.filter(r => r.status === 200).length;
  const failR = refreshResults.filter(r => r.status >= 400).length;
  const fiveR = refreshResults.filter(r => r.status >= 500).length;
  console.log(`  refresh status counts: 2xx=${okR} 4xx=${failR} 5xx=${fiveR}`);
  check(`no 5xx on contended refresh`, fiveR === 0);
  // Acceptable shapes: 1 winner + 5 losers, OR all 6 see the original token
  // briefly then 5 see the rotated one as invalid. Either way: no 5xx.
  check(`at least one refresh succeeded`, okR >= 1);

  // DB check: exactly one refresh token row left for this user
  const tokenCount = psql(`SELECT count(*) FROM refresh_token rt JOIN app_user u ON rt.user_id = u.id WHERE u.email = '${u2}';`);
  check(`exactly 1 refresh_token row left (single-per-user lock holds)`,
    tokenCount.stdout === "1", `count=${tokenCount.stdout}`);

  // ─── I3: refresh-token rotation (sequential reuse) ───
  console.log("\n=== I3: sequential reuse of a rotated refresh token ===");
  const u3 = `rotate-${stamp}@dumble.test`;
  const reg3 = await req("POST", "/api/auth/register", {
    firstName: "Rot", lastName: "Ate", email: u3, password: "validpass123",
  });
  const initRt = reg3.body.refreshToken;
  const first = await req("POST", "/api/auth/refresh", { refreshToken: initRt });
  check(`first refresh → 200 + new token issued`,
    first.status === 200 && first.body?.refreshToken && first.body.refreshToken !== initRt,
    `status=${first.status} same=${first.body?.refreshToken === initRt}`);

  const second = await req("POST", "/api/auth/refresh", { refreshToken: initRt });
  check(`reuse of the OLD refresh token → 4xx (rotation invalidated it)`,
    second.status >= 400 && second.status < 500, `got ${second.status} — if 200, rotation is broken`);

  // The new (rotated) token should work fresh
  const third = await req("POST", "/api/auth/refresh", { refreshToken: first.body.refreshToken });
  check(`fresh rotated token works once`, third.status === 200, `got ${third.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${ok}/${ok + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
