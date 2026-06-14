// End-to-end test for the Schedule service — Slice 1 (client's own schedule).
//
// Mints user JWTs with the shared secret (same HS256 + base64-key scheme the
// services use), so it doesn't depend on the auth service being up.
//
// Run: node tests/postman/schedule.js
//   SCHEDULE_HOST_URL  default http://localhost:8186
//   JWT_SECRET         must match the running schedule service

const crypto = require("crypto");
const SCHED = process.env.SCHEDULE_HOST_URL || "http://localhost:8186/api";
const SECRET = process.env.JWT_SECRET;
if (!SECRET) { console.error("ERROR: JWT_SECRET env var is required (source release/.env)."); process.exit(2); }
const KEY = Buffer.from(SECRET, "base64");

let pass = 0, fail = 0;
const ck = (l, ok, d) => { ok ? (pass++, console.log("  ✓ " + l)) : (fail++, console.log("  ✗ " + l + (d ? "  — " + d : ""))); };
const b64 = (b) => Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function mint(userId, userType) {
  const h = b64(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const p = b64(JSON.stringify({ sub: userId, userId, userType, iat: now, exp: now + 3600 }));
  const sig = b64(crypto.createHmac("sha256", KEY).update(h + "." + p).digest());
  return `${h}.${p}.${sig}`;
}
const j = async (r) => { const t = await r.text(); try { return JSON.parse(t); } catch { return t; } };
async function call(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${SCHED}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
const dayOf = (resp, table, wd) => (resp[table] || []).find(d => d.weekday === wd);

(async () => {
  console.log(`Schedule: ${SCHED}`);
  const A = mint(crypto.randomUUID(), "PARTICIPANT");
  const B = mint(crypto.randomUUID(), "PARTICIPANT");

  // ── empty schedule ────────────────────────────────────────────────
  console.log("\n=== fresh client schedule ===");
  let r = await call("GET", "/schedule/me", A);
  ck("GET /me → 200 with a scheduleId", r.status === 200 && !!r.body.scheduleId, `status=${r.status}`);
  ck("two 7-day tables, all empty", (r.body.exercises || []).length === 7 && (r.body.meals || []).length === 7
      && r.body.exercises.every(d => d.items.length === 0), `ex=${(r.body.exercises||[]).length}`);

  // ── add items (free text + YouTube) ───────────────────────────────
  console.log("\n=== client adds items ===");
  r = await call("POST", "/schedule/me/items", A,
      { tableType: "EXERCISE", weekday: "MON", content: "Bench 4x8, rest 90s", youtubeLink: "https://youtu.be/dQw4w9WgXcQ" });
  const exId = r.body.id;
  ck("add exercise → 201, YouTube link parsed to id, author=CLIENT",
      r.status === 201 && r.body.youtubeVideoId === "dQw4w9WgXcQ" && r.body.authorType === "CLIENT",
      `status=${r.status} body=${JSON.stringify(r.body).slice(0,140)}`);

  r = await call("POST", "/schedule/me/items", A,
      { tableType: "MEAL", weekday: "MON", content: "200g chicken + 80g rice" });
  ck("add meal item → 201", r.status === 201, `status=${r.status}`);

  r = await call("POST", "/schedule/me/items", A,
      { tableType: "EXERCISE", weekday: "MON", content: "Incline press 3x10" });
  ck("second MON exercise → 201 at position 1", r.status === 201 && r.body.position === 1, `pos=${r.body.position}`);

  // ── meal target (cal + macros) ────────────────────────────────────
  console.log("\n=== meal target (calories + macros) ===");
  r = await call("PUT", "/schedule/me/meal-targets/MON", A, { calories: 2000, proteinG: 150, carbsG: 200, fatG: 60 });
  ck("set MON meal target → 200, calories 2000, protein 150", r.status === 200 && r.body.calories === 2000 && r.body.proteinG === 150, `status=${r.status}`);

  // ── read back ─────────────────────────────────────────────────────
  console.log("\n=== read back ===");
  r = await call("GET", "/schedule/me", A);
  const monEx = dayOf(r.body, "exercises", "MON"), monMeal = dayOf(r.body, "meals", "MON");
  ck("MON has 2 exercises + 1 meal item", monEx.items.length === 2 && monMeal.items.length === 1,
      `ex=${monEx.items.length} meal=${monMeal.items.length}`);
  ck("MON meal target persisted (calories 2000)", monMeal.target && monMeal.target.calories === 2000, `target=${JSON.stringify(monMeal.target)}`);

  // ── completion (per-date) ─────────────────────────────────────────
  console.log("\n=== completion (mark done, per-date) ===");
  const today = new Date().toISOString().slice(0, 10);
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  const findItem = (resp, table, wd, id) => dayOf(resp, table, wd).items.find(i => i.id === id);

  r = await call("PUT", `/schedule/me/items/${exId}/completion`, A, { done: true });
  ck("mark done (today) → 200 done=true", r.status === 200 && r.body.done === true, `status=${r.status}`);
  r = await call("GET", "/schedule/me", A);
  ck("GET (today) shows item done", findItem(r.body, "exercises", "MON", exId)?.done === true);
  r = await call("GET", `/schedule/me?date=${tomorrow}`, A);
  ck("GET (tomorrow) shows item NOT done — completion is per-date", findItem(r.body, "exercises", "MON", exId)?.done === false);
  r = await call("PUT", `/schedule/me/items/${exId}/completion`, A, { date: today, done: false });
  ck("unmark → done=false", r.status === 200 && r.body.done === false);
  r = await call("GET", "/schedule/me", A);
  ck("GET (today) shows item not done after unmark", findItem(r.body, "exercises", "MON", exId)?.done === false);
  r = await call("PUT", `/schedule/me/items/${exId}/completion`, B, { done: true });
  ck("B marks A's item done → 404 (anti-IDOR)", r.status === 404, `status=${r.status}`);

  // ── author filter ─────────────────────────────────────────────────
  console.log("\n=== author filter ===");
  r = await call("GET", "/schedule/me?author=me", A);
  ck("author=me → the client's items", dayOf(r.body, "exercises", "MON").items.length === 2 && dayOf(r.body, "meals", "MON").items.length === 1);
  r = await call("GET", "/schedule/me?author=chatbot", A);
  ck("author=chatbot → empty (no chatbot items yet)", dayOf(r.body, "exercises", "MON").items.length === 0 && dayOf(r.body, "meals", "MON").items.length === 0);
  r = await call("GET", `/schedule/me?author=${crypto.randomUUID()}`, A);
  ck("author=<coach uuid> → empty (no trainer items yet)", dayOf(r.body, "exercises", "MON").items.length === 0);
  r = await call("GET", "/schedule/me?author=all", A);
  ck("author=all → all items", dayOf(r.body, "exercises", "MON").items.length === 2 && dayOf(r.body, "meals", "MON").items.length === 1);
  r = await call("GET", "/schedule/me?author=not-a-uuid", A);
  ck("author=garbage → 400", r.status === 400, `status=${r.status}`);

  // ── edit ──────────────────────────────────────────────────────────
  console.log("\n=== client edits an item ===");
  r = await call("PATCH", `/schedule/me/items/${exId}`, A, { content: "Bench 5x5", youtubeLink: null });
  ck("edit → content updated, video cleared", r.status === 200 && r.body.content === "Bench 5x5" && r.body.youtubeVideoId === null, `status=${r.status} body=${JSON.stringify(r.body).slice(0,120)}`);

  // ── validation: only YouTube links ────────────────────────────────
  console.log("\n=== only-YouTube link rule ===");
  r = await call("POST", "/schedule/me/items", A, { tableType: "EXERCISE", weekday: "TUE", content: "x", youtubeLink: "https://vimeo.com/12345" });
  ck("non-YouTube link → 400", r.status === 400, `status=${r.status}`);

  // ── auth required ─────────────────────────────────────────────────
  console.log("\n=== auth + privacy ===");
  r = await call("GET", "/schedule/me", null);
  ck("no token → 401/403", r.status === 401 || r.status === 403, `status=${r.status}`);

  // ── anti-IDOR: B can't see or touch A's schedule ──────────────────
  r = await call("GET", "/schedule/me", B);
  const bMon = dayOf(r.body, "exercises", "MON");
  ck("client B sees only their own (empty) schedule", r.status === 200 && bMon.items.length === 0, `B MON ex=${bMon ? bMon.items.length : "?"}`);
  r = await call("PATCH", `/schedule/me/items/${exId}`, B, { content: "hacked" });
  ck("B edits A's item → 404 (anti-IDOR)", r.status === 404, `status=${r.status}`);
  r = await call("DELETE", `/schedule/me/items/${exId}`, B);
  ck("B deletes A's item → 404 (anti-IDOR)", r.status === 404, `status=${r.status}`);

  // ── A still has its item (B couldn't touch it) ────────────────────
  r = await call("DELETE", `/schedule/me/items/${exId}`, A);
  ck("A deletes its own item → 204", r.status === 204, `status=${r.status}`);
  r = await call("GET", "/schedule/me", A);
  ck("after delete, MON has 1 exercise left; meal item + target still there (persisted)",
      dayOf(r.body, "exercises", "MON").items.length === 1 && dayOf(r.body, "meals", "MON").items.length === 1
      && dayOf(r.body, "meals", "MON").target.calories === 2000);

  // position uses max+1, not count — adding after a delete must not reuse a freed position
  console.log("\n=== add-after-delete keeps positions unique ===");
  const remaining = dayOf(r.body, "exercises", "MON").items[0].position; // the surviving item (position 1)
  r = await call("POST", "/schedule/me/items", A, { tableType: "EXERCISE", weekday: "MON", content: "added after delete" });
  ck("new item gets a fresh position (no collision with the survivor)",
      r.status === 201 && r.body.position > remaining, `new=${r.body.position} survivor=${remaining}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
