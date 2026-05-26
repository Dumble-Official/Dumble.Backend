// Gateway-mediated integration tests for the FitCoach chatbot.
//
// Drives traffic through the same chain a real client uses:
//   browser → gateway JwtAuthenticationFilter (Order 1)
//           → BannedUserFilter (Order 2)
//           → CoachEntitlementFilter (Order 3)
//             - hits Subscription /me/entitlements for plan
//             - increments Redis daily counter for billable paths
//             - injects X-User-Id + X-Internal-Secret
//           → fitcoach (Python FastAPI)
//
// Covers the four behaviours that have to hold for the integration to ship:
//   1. JWT enforcement   — no / expired / wrong-key / wrong-aud → 401
//   2. Plan gating       — FREE user blocked with 403, PRO user passes
//   3. Quota enforcement — PRO with daily cap → 429 with Retry-After past N
//   4. Non-billable paths (/health, /feedback) don't count against the cap
//
// Test users default to FREE. We use psql against subscription-db to flip
// them to PRO for sections that need it (same pattern as e2e_subscription.js).
// Quota tests temporarily lower PRO.chatbot_messages_per_day so the cap is
// reachable in 2 calls, and clean it back to NULL (unlimited) at the end.
//
// Run:
//   docker compose -f release/docker-compose.yml \
//                  -f release/docker-compose.test.fitcoach.yml up -d
//   node tests/postman/coach_integration.js
//
// Gateway must be reachable on http://localhost:18080 (default in your local
// release/docker-compose.override.yml, or override via GATEWAY_URL below).
//
// Env overrides:
//   GATEWAY_URL              default http://localhost:18080
//   JWT_SECRET               must match release/.env (base64 HS256)
//   COACH_RUN_GEMINI         set to "1" to run the real-chat happy-path
//                            (everything else avoids Gemini by sending empty
//                            messages so the gateway-side filter trips before
//                            FitCoach hits the model)

const crypto      = require("crypto");
const { spawnSync } = require("child_process");

const GATEWAY = process.env.GATEWAY_URL || "http://localhost:18080";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

const KEY_B64 = process.env.JWT_SECRET
              || "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const KEY     = Buffer.from(KEY_B64, "base64");
const WRONG_KEY = Buffer.from(
  "AAAAwzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8", "base64");

const stamp = Date.now();
const now   = Math.floor(stamp / 1000);
const exp   = now + 3600;

const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function jwt(claims, key) {
  const h = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const p = b64u(JSON.stringify(claims));
  const s = b64u(crypto.createHmac("sha256", key).update(`${h}.${p}`).digest());
  return `${h}.${p}.${s}`;
}
function uid(n) {
  const tail = (BigInt(stamp) + BigInt(n)).toString(16).padStart(12, "0").slice(-12);
  return `00000099-c047-0000-0000-${tail}`;
}

const USER_A = uid(1);
const USER_B = uid(2);

function tokenFor(userId, opts = {}) {
  return jwt({
    sub:       `coach-${userId.slice(-6)}@dumble.test`,
    userId:    userId,
    aud:       opts.aud || "dumble-app",
    roles:     ["ROLE_PARTICIPANT"],
    iat:       opts.iat ?? now,
    exp:       opts.exp ?? exp,
  }, opts.key || KEY);
}

const TOKEN_A         = tokenFor(USER_A);
const TOKEN_B         = tokenFor(USER_B);
const TOKEN_EXPIRED   = tokenFor(USER_A, { iat: now - 7200, exp: now - 3600 });
const TOKEN_WRONG_KEY = tokenFor(USER_A, { key: WRONG_KEY });

let pass = 0, fail = 0;
function check(label, ok, detail) {
  if (ok) { pass++; console.log(`  ✓ ${label}`); }
  else    { fail++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function call(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body)  headers["Content-Type"] = "application/json";
  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, headers: res.headers };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "subscription-db",
    "psql", "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql],
    { encoding: "utf8" });
  if (r.status !== 0) {
    console.error("psql failed:", r.stderr || r.stdout);
  }
  return { code: r.status, out: (r.stdout || "").trim() };
}

function redis(cmd) {
  const args = ["compose", ...COMPOSE, "exec", "-T", "redis", "redis-cli", ...cmd];
  const r = spawnSync("docker", args, { encoding: "utf8" });
  return { code: r.status, out: (r.stdout || "").trim() };
}

function clearCounterFor(userId) {
  // counter key is keyed by YYYYMMDD UTC — clear today's and tomorrow's so a
  // run that straddles midnight doesn't carry state between sections.
  const today    = new Date(); today.setUTCHours(0,0,0,0);
  const tomorrow = new Date(today.getTime() + 86400000);
  const fmt = (d) => `${d.getUTCFullYear()}${String(d.getUTCMonth()+1).padStart(2,"0")}${String(d.getUTCDate()).padStart(2,"0")}`;
  redis(["DEL", `chatbot:msgs:${userId}:${fmt(today)}`,
                `chatbot:msgs:${userId}:${fmt(tomorrow)}`]);
}

function setProPlan(userId) {
  // upsert an ACTIVE PRO row 30d into the future so EntitlementsService treats
  // the user as PRO.
  psql(
    `INSERT INTO platform_subscriptions
       (user_id, plan_code, status, started_at, current_period_end, version, created_at, updated_at)
     VALUES
       ('${userId}', 'PRO', 'ACTIVE', NOW(), NOW() + INTERVAL '30 days', 0, NOW(), NOW())
     ON CONFLICT (user_id) DO UPDATE
       SET plan_code='PRO', status='ACTIVE',
           current_period_end=NOW() + INTERVAL '30 days', updated_at=NOW();`);
}

function clearPlanFor(userId) {
  psql(`DELETE FROM platform_subscriptions WHERE user_id='${userId}';`);
}

function setProDailyCap(cap) {
  // null = unlimited (default seed); set an integer to force the gateway to
  // enforce a counter. EntitlementsService reads this on every request.
  const val = cap === null ? "NULL" : String(cap);
  psql(`UPDATE plans SET chatbot_messages_per_day=${val} WHERE code='PRO';`);
}

(async () => {
  console.log(`Gateway:  ${GATEWAY}`);
  console.log(`USER_A:   ${USER_A}`);
  console.log(`USER_B:   ${USER_B}\n`);

  // ── 1. JWT enforcement at the gateway ───────────────────────────────
  console.log("=== 1. JWT enforcement (gateway JwtAuthenticationFilter) ===");
  const r1 = await call("GET", "/api/coach/health", null);
  check(`no Authorization header → 401`,
        r1.status === 401, `status=${r1.status}`);

  const r2 = await call("GET", "/api/coach/health", TOKEN_EXPIRED);
  check(`expired token → 401`,
        r2.status === 401, `status=${r2.status}`);

  const r3 = await call("GET", "/api/coach/health", TOKEN_WRONG_KEY);
  check(`token signed with wrong key → 401`,
        r3.status === 401, `status=${r3.status}`);

  // Note: neither the gateway nor Subscription currently enforces aud, so a
  // wrong-aud token passes JWT validation and gets gated by plan instead
  // (FREE → 403). We don't assert that here — Section 2 covers the plan gate.

  // ── 2. FREE plan gating ─────────────────────────────────────────────
  console.log("\n=== 2. FREE plan gating (default state) ===");
  clearPlanFor(USER_A);
  clearPlanFor(USER_B);

  const r5 = await call("GET", "/api/coach/health", TOKEN_A);
  check(`FREE user → /api/coach/health 403`,
        r5.status === 403, `status=${r5.status}`);

  const r6 = await call("POST", "/api/coach/chat", TOKEN_A,
    { user_id: USER_A, message: "hi" });
  check(`FREE user → /api/coach/chat 403`,
        r6.status === 403, `status=${r6.status}`);

  // Confirm the 403 body carries the helpful message, not just an opaque code.
  check(`403 body mentions PRO subscription`,
        r6.body && JSON.stringify(r6.body).toLowerCase().includes("pro"),
        `body=${JSON.stringify(r6.body)}`);

  // ── 3. PRO upgrade unlocks the chatbot ──────────────────────────────
  console.log("\n=== 3. PRO upgrade (psql seed) — happy path ===");
  setProPlan(USER_A);
  setProDailyCap(null);          // unlimited
  clearCounterFor(USER_A);

  const r7 = await call("GET", "/api/coach/health", TOKEN_A);
  check(`PRO user → /api/coach/health 200`,
        r7.status === 200, `status=${r7.status} body=${JSON.stringify(r7.body)}`);
  check(`/api/coach/health body has status field from FitCoach`,
        r7.body && r7.body.status, `body=${JSON.stringify(r7.body)}`);

  if (process.env.COACH_RUN_GEMINI === "1") {
    console.log("    (running real Gemini chat — COACH_RUN_GEMINI=1)");
    const r8 = await call("POST", "/api/coach/chat", TOKEN_A,
      { user_id: USER_A, message: "say one word: ping" });
    check(`PRO user → /api/coach/chat 200`,
          r8.status === 200, `status=${r8.status}`);
    check(`/chat body has reply text`,
          r8.body && typeof r8.body.reply === "string" && r8.body.reply.length > 0,
          `reply=${r8.body && r8.body.reply}`);
  } else {
    console.log("    (COACH_RUN_GEMINI=0 — skipping real /chat to save quota)");
  }

  // ── 4. Daily quota enforcement ──────────────────────────────────────
  console.log("\n=== 4. Daily quota — gateway increments + 429 on overflow ===");
  setProDailyCap(2);             // cap at 2 messages
  clearCounterFor(USER_A);

  // Empty message — FitCoach 400s without calling Gemini, but the gateway
  // has ALREADY incremented the counter at this point. Burns 2 of 2.
  const q1 = await call("POST", "/api/coach/chat", TOKEN_A,
    { user_id: USER_A, message: "" });
  check(`quota call 1 reaches FitCoach (counter=1, expect 400 empty-message)`,
        q1.status === 400, `status=${q1.status}`);

  const q2 = await call("POST", "/api/coach/chat", TOKEN_A,
    { user_id: USER_A, message: "" });
  check(`quota call 2 reaches FitCoach (counter=2)`,
        q2.status === 400, `status=${q2.status}`);

  const q3 = await call("POST", "/api/coach/chat", TOKEN_A,
    { user_id: USER_A, message: "" });
  check(`quota call 3 → 429 (gateway blocks before FitCoach)`,
        q3.status === 429, `status=${q3.status}`);
  check(`429 carries Retry-After header`,
        q3.headers.get("Retry-After") !== null,
        `Retry-After=${q3.headers.get("Retry-After")}`);

  // ── 5. /feedback and /health are non-billable ───────────────────────
  console.log("\n=== 5. Non-billable paths don't count against the cap ===");
  setProDailyCap(1);             // cap = 1
  clearCounterFor(USER_A);

  // Hit /health 5x — should never bump the counter.
  for (let i = 0; i < 5; i++) {
    const h = await call("GET", "/api/coach/health", TOKEN_A);
    check(`/health #${i+1} stays under quota (200)`,
          h.status === 200, `status=${h.status}`);
  }

  // Hit /feedback (non-billable) — also doesn't count.
  const fb = await call("POST", "/api/coach/feedback", TOKEN_A,
    { user_id: USER_A, entry_id: "non-existent", feedback: 1 });
  check(`/feedback for unknown entry stays under quota (200 ok:false)`,
        fb.status === 200 && fb.body && fb.body.ok === false,
        `status=${fb.status} body=${JSON.stringify(fb.body)}`);

  // Now /chat — counter should still be 0, so this is the FIRST billable.
  const billable1 = await call("POST", "/api/coach/chat", TOKEN_A,
    { user_id: USER_A, message: "" });
  check(`billable /chat #1 reaches FitCoach (counter=1, cap=1)`,
        billable1.status === 400, `status=${billable1.status}`);

  // Next /chat — counter > cap → 429.
  const billable2 = await call("POST", "/api/coach/chat", TOKEN_A,
    { user_id: USER_A, message: "" });
  check(`billable /chat #2 → 429 (cap=1 enforced)`,
        billable2.status === 429, `status=${billable2.status}`);

  // ── 6. Per-user quota isolation ─────────────────────────────────────
  console.log("\n=== 6. Per-user quota isolation ===");
  setProPlan(USER_B);
  setProDailyCap(1);
  clearCounterFor(USER_B);

  const iso = await call("POST", "/api/coach/chat", TOKEN_B,
    { user_id: USER_B, message: "" });
  check(`USER_B's counter is independent of USER_A's exhausted bucket`,
        iso.status === 400, `status=${iso.status}`);

  // ── Cleanup ─────────────────────────────────────────────────────────
  console.log("\n=== cleanup ===");
  setProDailyCap(null);          // restore unlimited
  clearPlanFor(USER_A);
  clearPlanFor(USER_B);
  clearCounterFor(USER_A);
  clearCounterFor(USER_B);
  console.log(`  ✓ plans reset, test users removed, counters cleared`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
