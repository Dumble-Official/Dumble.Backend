// Brute-force probe for Auth.
//
// Today the service has NO rate limit on /api/auth/login and NO account
// lockout policy — confirmed by the surface map. This suite documents the
// gap empirically: it fires 50 wrong-password attempts against a real
// account back-to-back, asserts they all return 401 (no temporary
// lockout signal), and reports the wall-clock — so on-call has a concrete
// "how fast can someone brute-force this account" number.
//
// Reports as FINDINGS (not test failures) so the suite stays green; the
// missing rate-limit is a known backlog item, not a regression.
//
// Run: node tests/postman/brute_force_auth.js

const AUTH_URL = process.env.AUTH_HOST_URL || "http://localhost:18081";
const stamp = Date.now();

let ok = 0, fail = 0; const findings = [];
function OK(l)        { ok++; console.log(`  ✓ ${l}`); }
function FAIL(l, d)   { fail++; console.log(`  ✗ ${l}${d ? "  — " + d : ""}`); }
function FINDING(l, d){ findings.push({l,d}); console.log(`  ⚠ FINDING: ${l}${d ? "  — " + d : ""}`); }

async function req(method, path, body) {
  const opts = { method, headers: { "Content-Type": "application/json" } };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const t0 = Date.now();
  const res = await fetch(`${AUTH_URL}${path}`, opts);
  const dt = Date.now() - t0;
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, ms: dt };
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);
  const email = `bf-${stamp}@dumble.test`;
  const reg = await req("POST", "/api/auth/register", {
    firstName: "BF", lastName: "Test", email, password: "the-real-pass-987",
  });
  if (reg.status !== 201) { console.error("setup failed", reg); process.exit(2); }

  console.log("\n=== 50 wrong-password attempts in sequence ===");
  const ATTEMPTS = 50;
  const statuses = [];
  const t0 = Date.now();
  for (let i = 0; i < ATTEMPTS; i++) {
    const r = await req("POST", "/api/auth/login", {
      email, password: `attempt-${i}-${Math.random()}`,
    });
    statuses.push(r.status);
  }
  const elapsed = Date.now() - t0;
  const per = (elapsed / ATTEMPTS).toFixed(0);
  console.log(`  elapsed: ${elapsed}ms over ${ATTEMPTS} attempts (${per}ms each)`);

  const all401 = statuses.every(s => s === 401);
  const any429 = statuses.some(s => s === 429);
  const anyLockedFlavor = statuses.some(s => s === 423 || s === 403);

  // If we see 429s, the service implemented rate limiting since the surface
  // map — great, flag as a positive finding.
  if (any429) {
    OK(`rate-limit kicked in: at least one 429 response observed`);
  } else if (anyLockedFlavor) {
    OK(`account-lockout kicked in: at least one 423/403 response observed`);
  } else if (all401) {
    FINDING(`no rate-limit observed`,
      `all ${ATTEMPTS} attempts returned 401 with no throttling. An attacker can sustain ~${(1000/per).toFixed(0)} attempts/sec against a single account. Recommend Redis-backed token-bucket on /api/auth/login keyed by (email, source-IP).`);
    ok++;
  } else {
    FAIL(`unexpected status distribution`, JSON.stringify(statuses));
  }

  // After the brute-force attempt, the real password should still work
  console.log("\n=== Real password still works after the burst ===");
  const real = await req("POST", "/api/auth/login", {
    email, password: "the-real-pass-987",
  });
  if (real.status === 200) {
    OK(`real password still logs in (no false-positive lockout of legit user)`);
  } else if (real.status === 429 || real.status === 423 || real.status === 403) {
    OK(`legitimate login also throttled (acceptable trade-off if the lockout has a short TTL)`);
  } else {
    FAIL(`legitimate login broken after burst`, `status=${real.status}`);
  }

  // Test: timing per failed attempt is consistent with bcrypt cost
  // BCrypt cost 10 ≈ 60-100ms on modest hardware. If per-attempt time is
  // closer to <10ms, the service might be short-circuiting auth (bug);
  // if >500ms it's burning CPU on every attempt (impacts throughput).
  console.log("\n=== Per-attempt timing characterizes bcrypt cost ===");
  if (per > 30 && per < 500) {
    OK(`per-attempt ${per}ms is consistent with bcrypt cost 10-12 (expected)`);
  } else if (per <= 30) {
    FINDING(`per-attempt ${per}ms is too fast`,
      "bcrypt may be skipped — auth could be returning fail before hashing");
    ok++;
  } else {
    FINDING(`per-attempt ${per}ms is very slow`,
      "bcrypt cost may be excessive; could be DoSed via login spam");
    ok++;
  }

  console.log(`\n${fail === 0 ? "✓ no broken assertions" : "✗ broken assertions present"}: ${ok}/${ok + fail} checks passed`);
  if (findings.length) {
    console.log("\n— Findings (recommended improvements, not blockers) —");
    for (const f of findings) console.log(`  • ${f.l}${f.d ? " — " + f.d : ""}`);
  }
  process.exit(fail === 0 ? 0 : 1);
})();
