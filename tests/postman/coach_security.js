// Direct-hit security probes for FitCoach.
//
// Bypasses the gateway and talks straight to the Python container to verify
// FitCoach's OWN auth layer holds — the gateway's filters are not the only
// thing standing between an attacker on the compose network and the chatbot.
//
// Covers:
//   1. X-Internal-Secret HMAC enforcement (anti-forgery)
//      - missing header → 401
//      - wrong value → 401 (must use hmac.compare_digest, not ==)
//      - fail-closed when INTERNAL_API_SECRET is unset — covered by manual env
//        flip, not in this script
//   2. X-User-Id required (anti-IDOR foundation)
//      - missing → 401
//      - empty → 401
//   3. body user_id is OVERRIDDEN by X-User-Id (anti-IDOR core)
//      - send /chat with X-User-Id=A but body.user_id=B
//      - capture assistant_entry_id (entry stored in A's memory if override works)
//      - /feedback with X-User-Id=A finds the entry
//      - /feedback with X-User-Id=B does NOT find it (cross-bucket leak would
//        let it through if the body field had been trusted)
//
// Step 3 makes a real Gemini call (~1 short message). To skip it set
// FITCOACH_SKIP_GEMINI=1 — the headers-only checks still run.
//
// Run:
//   docker compose -f release/docker-compose.yml \
//                  -f release/docker-compose.test.fitcoach.yml up -d fitcoach
//   node tests/postman/coach_security.js
//
// The test compose file is what publishes fitcoach on host 18000. The main
// compose only exposes the port internally on the compose network, so any
// browser/test runner outside the network needs this override.
//
// Env overrides:
//   FITCOACH_HOST_URL    default http://localhost:18000
//   INTERNAL_API_SECRET  must match the value compose passes to the container
//   FITCOACH_SKIP_GEMINI set to "1" to skip the IDOR proof that costs a chat

const FITCOACH = process.env.FITCOACH_HOST_URL || "http://localhost:18000";
// Read from env only — never hardcode a fallback for INTERNAL_API_SECRET,
// or a fresh checkout would carry a working forged credential against any
// dev FitCoach instance someone leaves reachable.
const SECRET   = process.env.INTERNAL_API_SECRET || "";
if (!SECRET) {
  console.error("ERROR: INTERNAL_API_SECRET env var is required.");
  console.error("Source release/.env first:  set -a; . release/.env; set +a");
  process.exit(2);
}

const stamp = Date.now();
function uid(n) {
  const tail = (BigInt(stamp) + BigInt(n)).toString(16).padStart(12, "0").slice(-12);
  return `00000099-c0ac-0000-0000-${tail}`;
}
const USER_A = uid(1);
const USER_B = uid(2);

let pass = 0, fail = 0;
function check(label, ok, detail) {
  if (ok) { pass++; console.log(`  ✓ ${label}`); }
  else    { fail++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function call(method, path, headers, body) {
  const init = {
    method,
    headers: { "Content-Type": "application/json", ...headers },
  };
  if (body) init.body = JSON.stringify(body);
  const res = await fetch(`${FITCOACH}${path}`, init);
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

(async () => {
  console.log(`FitCoach (direct): ${FITCOACH}`);
  console.log(`USER_A: ${USER_A}`);
  console.log(`USER_B: ${USER_B}\n`);

  // ── 1. X-Internal-Secret enforcement ─────────────────────────────────
  console.log("=== 1. X-Internal-Secret enforcement ===");
  const r1 = await call("POST", "/chat",
    { "X-User-Id": USER_A },
    { user_id: USER_A, message: "hi" });
  check(`no X-Internal-Secret → 401`,
        r1.status === 401, `status=${r1.status}`);

  const r2 = await call("POST", "/chat",
    { "X-User-Id": USER_A, "X-Internal-Secret": "WRONG-SECRET-VALUE" },
    { user_id: USER_A, message: "hi" });
  check(`wrong X-Internal-Secret → 401`,
        r2.status === 401, `status=${r2.status}`);

  // ── 2. X-User-Id enforcement ─────────────────────────────────────────
  console.log("\n=== 2. X-User-Id enforcement (gateway identity required) ===");
  const r3 = await call("POST", "/chat",
    { "X-Internal-Secret": SECRET },
    { user_id: USER_A, message: "hi" });
  check(`correct secret + no X-User-Id → 401`,
        r3.status === 401, `status=${r3.status}`);

  const r4 = await call("POST", "/chat",
    { "X-Internal-Secret": SECRET, "X-User-Id": "   " },
    { user_id: USER_A, message: "hi" });
  check(`correct secret + whitespace-only X-User-Id → 401`,
        r4.status === 401, `status=${r4.status}`);

  const r5 = await call("POST", "/feedback",
    { "X-Internal-Secret": SECRET },
    { user_id: USER_A, entry_id: "x", feedback: 1 });
  check(`/feedback also requires X-User-Id`,
        r5.status === 401, `status=${r5.status}`);

  // ── 3. /health is the liveness probe — unauthenticated by design ─────
  console.log("\n=== 3. /health (liveness, no auth) ===");
  const h = await fetch(`${FITCOACH}/health`);
  check(`unauthenticated /health → 200`,
        h.status === 200, `status=${h.status}`);

  // ── 4. IDOR proof: body user_id is overridden by X-User-Id ───────────
  if (process.env.FITCOACH_SKIP_GEMINI === "1") {
    console.log("\n=== 4. IDOR override proof — SKIPPED (FITCOACH_SKIP_GEMINI=1) ===");
  } else {
    console.log("\n=== 4. IDOR override — body user_id IS IGNORED ===");
    // Make a /chat call as USER_A but pass USER_B in the body. If the
    // override is wired, the entry is stored in A's memory file. If the
    // override were missing, it would land in B's bucket.
    const chat = await call("POST", "/chat",
      { "X-Internal-Secret": SECRET, "X-User-Id": USER_A },
      {
        user_id: USER_B,                                  // hostile spoof
        message: `idor-probe-${stamp} please reply with one short word`,
      });

    if (chat.status !== 200) {
      check(`/chat call succeeded (needs valid Gemini keys to assert IDOR)`,
            false,
            `status=${chat.status} body=${JSON.stringify(chat.body).slice(0,120)}`);
    } else {
      const entryId = chat.body && chat.body.assistant_entry_id;
      check(`/chat returns assistant_entry_id`,
            typeof entryId === "string" && entryId.length > 0,
            `entry_id=${entryId}`);

      if (entryId) {
        // The entry must resolve under USER_A's bucket (the override target).
        const fA = await call("POST", "/feedback",
          { "X-Internal-Secret": SECRET, "X-User-Id": USER_A },
          { user_id: USER_B, entry_id: entryId, feedback: 1 });
        check(`/feedback finds entry under USER_A (override won)`,
              fA.status === 200 && fA.body && fA.body.ok === true,
              `status=${fA.status} body=${JSON.stringify(fA.body)}`);

        // …and must NOT resolve under USER_B (would prove the body field
        // had been trusted instead of the header — i.e. the IDOR is live).
        const fB = await call("POST", "/feedback",
          { "X-Internal-Secret": SECRET, "X-User-Id": USER_B },
          { user_id: USER_A, entry_id: entryId, feedback: 1 });
        check(`/feedback does NOT find entry under USER_B (no cross-bucket leak)`,
              fB.status === 200 && fB.body && fB.body.ok === false,
              `status=${fB.status} body=${JSON.stringify(fB.body)}`);
      }
    }
  }

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
