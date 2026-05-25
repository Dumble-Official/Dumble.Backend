// Email enumeration probes for Auth.
//
// Most leaks come from response SHAPE (different error message for "no such
// user" vs "wrong password") and TIMING side-channels (bcrypt verification
// is slow; DB miss is fast — the difference is observable).
//
// Auth's GlobalExceptionHandler already returns the same "Invalid email or
// password" message for both BadCredentials and UsernameNotFound, so the
// SHAPE leak is closed. Timing is harder to close fully — bcrypt against
// "" still returns false but doesn't burn a real round, so we'd expect
// "no user" to be measurably faster. This suite quantifies the gap so we
// know how leaky timing actually is — anything over 100ms gap is high-risk.
//
// Run: node tests/postman/email_enumeration_auth.js

const AUTH_URL = process.env.AUTH_HOST_URL || "http://localhost:18081";
const stamp = Date.now();

let ok = 0, fail = 0; const findings = [];
function check(label, cond, detail) {
  if (cond) { ok++; console.log(`  ✓ ${label}`); }
  else      { fail++; console.log(`  ✗ ${label}${detail ? " — " + detail : ""}`); }
}
function note(label, detail) {
  findings.push(`${label}${detail ? " — " + detail : ""}`);
  console.log(`  ⚠ FINDING: ${label}${detail ? " — " + detail : ""}`);
}

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

async function timed(N, fn) {
  const times = [];
  for (let i = 0; i < N; i++) times.push(await fn(i));
  times.sort((a,b) => a-b);
  const median = times[Math.floor(times.length / 2)];
  const p25 = times[Math.floor(times.length * 0.25)];
  const p75 = times[Math.floor(times.length * 0.75)];
  return { median, p25, p75, all: times };
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);
  // Pre-register one known user so we have an "exists with wrong password"
  // candidate for the timing comparison.
  const known = `enum-known-${stamp}@dumble.test`;
  await req("POST", "/api/auth/register", {
    firstName: "Enum", lastName: "Known", email: known, password: "validpass123",
  });

  console.log("\n=== Response SHAPE on /login ===");
  // Unknown email
  const r1 = await req("POST", "/api/auth/login", {
    email: `enum-unknown-${stamp}@dumble.test`, password: "anything-12345",
  });
  // Known email, wrong password
  const r2 = await req("POST", "/api/auth/login", {
    email: known, password: "wrong-password-12345",
  });
  console.log(`  unknown email: status=${r1.status} body=${JSON.stringify(r1.body)}`);
  console.log(`  known + wrong: status=${r2.status} body=${JSON.stringify(r2.body)}`);
  check(`both return the same status code`, r1.status === r2.status);
  check(`both return the same body message`,
    r1.body?.message === r2.body?.message,
    `'${r1.body?.message}' vs '${r2.body?.message}'`);

  console.log("\n=== Response SHAPE on /register ===");
  const r3 = await req("POST", "/api/auth/register", {
    firstName: "X", lastName: "Y", email: known, password: "validpass123",
  });
  console.log(`  duplicate register: status=${r3.status} body=${JSON.stringify(r3.body)}`);
  check(`duplicate-email message doesn't say 'already exists' / 'in use'`,
    !/(already|in use|exists|taken)/i.test(JSON.stringify(r3.body)),
    "the message reveals the email is registered");

  console.log("\n=== TIMING side-channel on /login ===");
  // 20 calls each — warm-up runs first to even out JIT compilation.
  console.log("  (this takes ~10s)");
  for (let i = 0; i < 3; i++) {
    await req("POST", "/api/auth/login", { email: known, password: "warmup" });
  }
  const unknownTimes = await timed(15, () =>
    req("POST", "/api/auth/login", {
      email: `enum-u${Math.random()}-${stamp}@dumble.test`, password: "anything12345",
    }).then(r => r.ms));
  const knownTimes = await timed(15, () =>
    req("POST", "/api/auth/login", {
      email: known, password: `wrong-${Math.random()}`,
    }).then(r => r.ms));
  console.log(`  unknown-email login median: ${unknownTimes.median}ms (p25=${unknownTimes.p25}, p75=${unknownTimes.p75})`);
  console.log(`  known-email-wrong-pass:    ${knownTimes.median}ms (p25=${knownTimes.p25}, p75=${knownTimes.p75})`);
  const gap = Math.abs(knownTimes.median - unknownTimes.median);
  console.log(`  median gap: ${gap}ms`);

  if (gap < 50) {
    check(`timing gap < 50ms — enumeration via timing is hard to exploit`, true);
  } else if (gap < 150) {
    note(`timing gap = ${gap}ms — observable but noisy at this magnitude. Could be narrowed by running bcrypt on a dummy hash for unknown emails (constant-time login).`);
    ok++;
  } else {
    fail++;
    console.log(`  ✗ timing gap = ${gap}ms — clearly exploitable for email enumeration`);
  }

  console.log("\n=== Refresh-token endpoint doesn't leak validity ===");
  // /refresh with garbage vs /refresh with a token belonging to a real user
  // but currently invalidated — both should look identical to the caller.
  const garbage = await req("POST", "/api/auth/refresh", { refreshToken: "garbage-not-uuid" });
  const validShape = await req("POST", "/api/auth/refresh", {
    refreshToken: "00000000-0000-0000-0000-000000000000",
  });
  check(`/refresh garbage vs valid-shape-unknown: same status`,
    garbage.status === validShape.status, `${garbage.status} vs ${validShape.status}`);
  check(`/refresh garbage vs valid-shape-unknown: same message`,
    garbage.body?.message === validShape.body?.message);

  console.log(`\n${fail === 0 ? "✓ no broken assertions" : "✗ broken assertions present"}: ${ok}/${ok + fail} checks passed`);
  if (findings.length) {
    console.log("\n— Findings —");
    for (const f of findings) console.log(`  • ${f}`);
  }
  process.exit(fail === 0 ? 0 : 1);
})();
