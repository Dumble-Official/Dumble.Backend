// End-to-end test for the Schedule service — Slice 5 (chatbot author path).
// FitCoach writes a pro client's schedule via an internal, secret-gated endpoint;
// items are stamped CHATBOT, not contact-filtered, invisible to coaches.
//
// Run: node tests/postman/schedule_chatbot.js   (JWT_SECRET / INTERNAL_API_SECRET)

const crypto = require("crypto");
const SCHED = process.env.SCHEDULE_HOST_URL || "http://localhost:8186/api";
const SECRET = process.env.JWT_SECRET || "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const INTERNAL = process.env.INTERNAL_API_SECRET || "AGXkcoO2HENx9W37bLRydr15QugIj8TfnFSpZqwV4Mts0JKe";
const KEY = Buffer.from(SECRET, "base64");

let pass = 0, fail = 0;
const ck = (l, ok, d) => { ok ? (pass++, console.log("  ✓ " + l)) : (fail++, console.log("  ✗ " + l + (d ? "  — " + d : ""))); };
const b64 = (b) => Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function mint(userId, userType) {
  const h = b64(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const p = b64(JSON.stringify({ sub: userId, userId, userType, iat: now, exp: now + 3600 }));
  return `${h}.${p}.${b64(crypto.createHmac("sha256", KEY).update(h + "." + p).digest())}`;
}
const j = async (r) => { const t = await r.text(); try { return JSON.parse(t); } catch { return t; } };
async function call(method, path, token, body, extra) {
  const headers = { ...(extra || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${SCHED}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
const monEx = (resp) => (resp.exercises || []).find(d => d.weekday === "MON").items;
const monMeal = (resp) => (resp.meals || []).find(d => d.weekday === "MON").items;
const has = (items, text) => items.some(i => i.content === text);
const apply = (clientId, body, secret) =>
  call("POST", `/internal/clients/${clientId}/chatbot/items`, null, body, secret ? { "X-Internal-Secret": secret } : {});

(async () => {
  console.log(`Schedule: ${SCHED}`);
  const cid = crypto.randomUUID(), tid = crypto.randomUUID();
  const C = mint(cid, "PARTICIPANT"), T = mint(tid, "TRAINER");

  // ── secret-gated ──────────────────────────────────────────────────
  console.log("\n=== internal chatbot endpoint auth ===");
  let r = await apply(cid, { replace: true, items: [{ tableType: "EXERCISE", weekday: "MON", content: "x" }] }, null);
  ck("apply without secret → 401", r.status === 401, `status=${r.status}`);

  // ── chatbot writes a plan ─────────────────────────────────────────
  console.log("\n=== chatbot writes the plan ===");
  r = await apply(cid, { replace: true, items: [
        { tableType: "EXERCISE", weekday: "MON", content: "AI: 5x5 back squat", youtubeLink: "https://youtu.be/dQw4w9WgXcQ" },
        { tableType: "MEAL", weekday: "MON", content: "AI: 150g salmon + 200g potato" }] }, INTERNAL);
  ck("apply (replace) → 200, items stamped CHATBOT, no authorId", r.status === 200 && r.body.length === 2
      && r.body.every(i => i.authorType === "CHATBOT" && i.authorId === null), `status=${r.status} body=${JSON.stringify(r.body).slice(0,160)}`);
  ck("chatbot YouTube link parsed to id", r.body[0].youtubeVideoId === "dQw4w9WgXcQ", `vid=${r.body[0].youtubeVideoId}`);

  // ── client sees + filters them ────────────────────────────────────
  console.log("\n=== client sees the chatbot plan ===");
  r = await call("GET", "/schedule/me", C);
  ck("client sees chatbot items in both tables", has(monEx(r.body), "AI: 5x5 back squat") && has(monMeal(r.body), "AI: 150g salmon + 200g potato"));
  r = await call("GET", "/schedule/me?author=chatbot", C);
  ck("author=chatbot → only chatbot items", monEx(r.body).length === 1 && monMeal(r.body).length === 1 && has(monEx(r.body), "AI: 5x5 back squat"));
  r = await call("GET", "/schedule/me?author=me", C);
  ck("author=me → none of the chatbot items", monEx(r.body).length === 0 && monMeal(r.body).length === 0);

  // ── chatbot content is NOT contact-filtered ───────────────────────
  console.log("\n=== chatbot not contact-filtered ===");
  r = await apply(cid, { replace: false, items: [{ tableType: "EXERCISE", weekday: "MON", content: "ping me on 01012345678 if stuck" }] }, INTERNAL);
  ck("chatbot content with a phone → accepted (200)", r.status === 200, `status=${r.status} msg=${r.body && r.body.message}`);

  // ── replace regenerates (clears only chatbot items) ───────────────
  console.log("\n=== regenerate (replace=true) ===");
  r = await apply(cid, { replace: true, items: [{ tableType: "EXERCISE", weekday: "MON", content: "AI: regenerated plan" }] }, INTERNAL);
  const aiItem = r.body[0].id;
  r = await call("GET", "/schedule/me?author=chatbot", C);
  ck("regenerate replaced prior chatbot items", monEx(r.body).length === 1 && has(monEx(r.body), "AI: regenerated plan") && monMeal(r.body).length === 0);

  // ── coaches do NOT see chatbot items ──────────────────────────────
  console.log("\n=== coaches don't see chatbot items ===");
  await call("POST", "/internal/trainer-links", null, { trainerId: tid, clientId: cid, active: true }, { "X-Internal-Secret": INTERNAL });
  await call("POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "Coach T item" });
  r = await call("GET", `/schedule/clients/${cid}`, T);
  ck("coach sees own item, NOT the chatbot's", has(monEx(r.body), "Coach T item") && !has(monEx(r.body), "AI: regenerated plan"), `T sees: ${monEx(r.body).map(i=>i.content)}`);

  // ── client edits a chatbot item freely ────────────────────────────
  console.log("\n=== client edits chatbot item ===");
  r = await call("PATCH", `/schedule/me/items/${aiItem}`, C, { content: "I tweaked the AI plan" });
  ck("client edits a CHATBOT item via /me → 200", r.status === 200 && r.body.content === "I tweaked the AI plan", `status=${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
