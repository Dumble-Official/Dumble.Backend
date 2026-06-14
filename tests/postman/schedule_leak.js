// End-to-end test for the Schedule service — Slice 4 (contact-leak filter).
// Trainer content is scrubbed for off-platform contact; client content is not.
//
// Run: node tests/postman/schedule_leak.js
//   SCHEDULE_HOST_URL / JWT_SECRET / INTERNAL_API_SECRET (match the service)

const crypto = require("crypto");
const SCHED = process.env.SCHEDULE_HOST_URL || "http://localhost:8186/api";
const SECRET = process.env.JWT_SECRET;
const INTERNAL = process.env.INTERNAL_API_SECRET;
if (!SECRET || !INTERNAL) { console.error("ERROR: JWT_SECRET and INTERNAL_API_SECRET env vars are required (source release/.env)."); process.exit(2); }
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
async function call(method, path, token, body, extraHeaders) {
  const headers = { ...(extraHeaders || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${SCHED}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}

(async () => {
  console.log(`Schedule: ${SCHED}`);
  const tid = crypto.randomUUID(), cid = crypto.randomUUID();
  const T = mint(tid, "TRAINER"), C = mint(cid, "PARTICIPANT");
  await call("POST", "/internal/trainer-links", null, { trainerId: tid, clientId: cid, active: true }, { "X-Internal-Secret": INTERNAL });

  const tAdd = (content, youtubeLink) => call("POST", `/schedule/clients/${cid}/items`, T,
      { tableType: "EXERCISE", weekday: "MON", content, youtubeLink });

  // ── trainer content with contact info → rejected ──────────────────
  console.log("\n=== trainer content blocked (anti-disintermediation) ===");
  const blocked = [
    ["intl phone", "Great work! reach +20 100 1234567"],
    ["egyptian mobile", "call 010 123 4567 after the session"],
    ["grouped phone", "my cell 123-456-7890"],
    ["email", "questions? coach.mike@gmail.com"],
    ["instagram handle", "follow @coachmike for tips"],
    ["external link", "full plan at instagram.com/coachmike"],
    ["whatsapp link", "ping me wa.me/201001234567"],
    ["lure phrase", "DM me for the real program"],
  ];
  for (const [label, content] of blocked) {
    const r = await tAdd(content);
    ck(`block: ${label} → 400`, r.status === 400, `status=${r.status} msg=${r.body && r.body.message}`);
  }

  // ── legit training / nutrition text → accepted (no false positives) ─
  console.log("\n=== clean training/nutrition text accepted ===");
  const clean = [
    ["rep scheme", "Bench press 4x8, rest 90s; then 3x12"],
    ["grams + vitamins", "200g chicken + 80g rice, B12 + omega-3"],
    ["number-heavy warmup", "Warmup sets: 10 20 30 40 50 then work sets"],
  ];
  for (const [label, content] of clean) {
    const r = await tAdd(content);
    ck(`allow: ${label} → 201`, r.status === 201, `status=${r.status} msg=${r.body && r.body.message}`);
  }

  // ── a YouTube link belongs in the video field → accepted ──────────
  let r = await tAdd("Squat — watch my form cue", "https://youtu.be/dQw4w9WgXcQ");
  ck("clean text + YouTube in the video field → 201", r.status === 201 && r.body.youtubeVideoId === "dQw4w9WgXcQ", `status=${r.status}`);

  // ── filter also applies on trainer edit ───────────────────────────
  console.log("\n=== filter on edit ===");
  const okItem = r.body.id;
  r = await call("PATCH", `/schedule/clients/${cid}/items/${okItem}`, T, { content: "actually text me 01012345678" });
  ck("trainer edit with phone → 400", r.status === 400, `status=${r.status}`);

  // ── the SAME text from the client is allowed (clients aren't filtered) ─
  console.log("\n=== client content is not contact-filtered ===");
  r = await call("POST", "/schedule/me/items", C, { tableType: "EXERCISE", weekday: "MON", content: "my own note: call mom 010 123 4567" });
  ck("client posts contact-looking text on their own schedule → 201", r.status === 201, `status=${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
