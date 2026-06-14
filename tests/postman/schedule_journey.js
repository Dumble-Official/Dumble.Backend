// Functional walkthrough of the Schedule service as a real user would experience
// it — narrated, printing actual responses. Goes through the gateway (:8090); the
// trainer↔client link is driven by REAL Subscription bundle events over RabbitMQ.
//
// Run: node tests/postman/schedule_journey.js   (JWT_SECRET / INTERNAL_API_SECRET)

const crypto = require("crypto");
const GW = process.env.GATEWAY_URL || "http://localhost:8090/api";
const DIRECT = process.env.SCHEDULE_DIRECT_URL || "http://localhost:8186/api";
const SECRET = process.env.JWT_SECRET;
const INTERNAL = process.env.INTERNAL_API_SECRET;
if (!SECRET || !INTERNAL) { console.error("ERROR: JWT_SECRET and INTERNAL_API_SECRET env vars are required."); process.exit(2); }
const MGMT = process.env.RABBIT_MGMT_URL || "http://localhost:15673";
const RAUTH = "Basic " + Buffer.from(`${process.env.RABBIT_USER || "app"}:${process.env.RABBIT_PASS || "app"}`).toString("base64");
const KEY = Buffer.from(SECRET, "base64");

let problems = 0;
const b64 = (b) => Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function mint(userId, userType) {
  const h = b64(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const p = b64(JSON.stringify({ sub: userId, userId, userType, iat: now, exp: now + 3600 }));
  return `${h}.${p}.${b64(crypto.createHmac("sha256", KEY).update(h + "." + p).digest())}`;
}
const j = async (r) => { const t = await r.text(); try { return JSON.parse(t); } catch { return t; } };
async function api(base, method, path, token, body, extra) {
  const headers = { ...(extra || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${base}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
async function publishBundle(rk, payload) {
  await fetch(`${MGMT}/api/exchanges/%2F/dumble.events/publish`, {
    method: "POST", headers: { "Content-Type": "application/json", Authorization: RAUTH },
    body: JSON.stringify({ properties: {}, routing_key: rk, payload: JSON.stringify(payload), payload_encoding: "string" }),
  });
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function expect(label, ok, detail) {
  console.log(`   ${ok ? "✓ expected" : "✗ UNEXPECTED"}: ${label}${detail ? " — " + detail : ""}`);
  if (!ok) problems++;
}
const monEx = (s) => (s.exercises || []).find(d => d.weekday === "MON").items;
const monMeal = (s) => (s.meals || []).find(d => d.weekday === "MON").items;
const monTarget = (s) => (s.meals || []).find(d => d.weekday === "MON").target;

(async () => {
  const cid = crypto.randomUUID(), tid = crypto.randomUUID(), nutid = crypto.randomUUID();
  const C = mint(cid, "PARTICIPANT"), T = mint(tid, "TRAINER"), NUT = mint(nutid, "TRAINER");
  console.log("Functional journey — everything via the gateway (" + GW + ")\n");

  console.log("1) New client opens their schedule");
  let r = await api(GW, "GET", "/schedule/me", C);
  console.log(`   GET /schedule/me -> ${r.status}; exercises days=${r.body.exercises?.length}, meals days=${r.body.meals?.length}, all empty=${r.body.exercises?.every(d => d.items.length === 0)}`);
  expect("a fresh Sun–Sat schedule, both tables empty", r.status === 200 && r.body.exercises.length === 7 && r.body.exercises.every(d => d.items.length === 0));

  console.log("\n2) A trainer with no subscription tries to coach this client");
  r = await api(GW, "POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "x" });
  console.log(`   trainer POST /schedule/clients/${cid.slice(0,8)}…/items -> ${r.status} (${r.body.message || ""})`);
  expect("blocked — no active subscription", r.status === 403);

  console.log("\n3) Client buys the trainer's bundle → Subscription emits bundle.activated over RabbitMQ");
  await publishBundle("subscription.bundle.activated", { participantId: cid, sellerId: tid, sellerType: "TRAINER", status: "ACTIVE" });
  let opened = false;
  for (let i = 0; i < 20 && !opened; i++) { await sleep(300); const t = await api(GW, "GET", `/schedule/clients/${cid}`, T); opened = t.status === 200; }
  console.log(`   after the event was consumed, trainer access -> ${opened ? "granted" : "still denied"}`);
  expect("the coaching link opened from the real event", opened);

  console.log("\n4) Trainer builds Monday: a squat with a form video + a meal, and a macro target");
  r = await api(GW, "POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "Back squat 5x5 @ RPE7", youtubeLink: "https://youtu.be/dQw4w9WgXcQ" });
  console.log(`   add exercise -> ${r.status}; author=${r.body.authorType}, video=${r.body.youtubeVideoId}`);
  expect("exercise saved as TRAINER with the embedded video id", r.status === 201 && r.body.authorType === "TRAINER" && r.body.youtubeVideoId === "dQw4w9WgXcQ");
  await api(GW, "POST", `/schedule/clients/${cid}/items`, T, { tableType: "MEAL", weekday: "MON", content: "200g chicken + 80g rice" });
  r = await api(GW, "PUT", `/schedule/clients/${cid}/meal-targets/MON`, T, { calories: 2100, proteinG: 160, carbsG: 210, fatG: 60 });
  console.log(`   set MON macro target -> ${r.status}; ${JSON.stringify(r.body)}`);
  expect("macro target stored", r.status === 200 && r.body.calories === 2100);

  console.log("\n5) Trainer tries to slip their WhatsApp into the plan");
  r = await api(GW, "POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "great work! whatsapp me 01012345678 for the full plan" });
  console.log(`   add with contact info -> ${r.status}: "${r.body.message || ""}"`);
  expect("rejected by the contact-leak filter", r.status === 400);

  console.log("\n6) Client opens their schedule and sees the coach's Monday program");
  r = await api(GW, "GET", "/schedule/me", C);
  console.log(`   MON exercises: ${monEx(r.body).map(i => `"${i.content}"${i.youtubeVideoId ? " [video]" : ""}`).join(", ")}`);
  console.log(`   MON meals: ${monMeal(r.body).map(i => `"${i.content}"`).join(", ")} | target ${JSON.stringify(monTarget(r.body))}`);
  expect("client sees the squat (with video) + meal + target", monEx(r.body).some(i => i.content.startsWith("Back squat") && i.youtubeVideoId) && monTarget(r.body).calories === 2100);
  expect("the WhatsApp item never made it in", !monEx(r.body).some(i => i.content.includes("whatsapp")));

  console.log("\n7) Client does Monday and marks the squat done");
  const squat = monEx(r.body).find(i => i.content.startsWith("Back squat"));
  await api(GW, "PUT", `/schedule/me/items/${squat.id}/completion`, C, { done: true });
  r = await api(GW, "GET", "/schedule/me", C);
  console.log(`   squat done today? ${monEx(r.body).find(i => i.id === squat.id).done}`);
  expect("completion recorded for today", monEx(r.body).find(i => i.id === squat.id).done === true);

  console.log("\n8) A second (nutrition) coach joins — cross-coach privacy");
  await publishBundle("subscription.bundle.activated", { participantId: cid, sellerId: nutid, sellerType: "TRAINER", status: "ACTIVE" });
  let nutOk = false;
  for (let i = 0; i < 20 && !nutOk; i++) { await sleep(300); const t = await api(GW, "POST", `/schedule/clients/${cid}/items`, NUT, { tableType: "MEAL", weekday: "MON", content: "Nutrition coach: +1 scoop whey post-workout" }); nutOk = t.status === 201; }
  let tView = await api(GW, "GET", `/schedule/clients/${cid}`, T);
  let nutView = await api(GW, "GET", `/schedule/clients/${cid}`, NUT);
  let cView = await api(GW, "GET", "/schedule/me", C);
  console.log(`   strength coach sees meals: ${monMeal(tView.body).map(i => `"${i.content.slice(0,28)}"`).join(", ") || "(none)"}`);
  console.log(`   nutrition coach sees meals: ${monMeal(nutView.body).map(i => `"${i.content.slice(0,28)}"`).join(", ")}`);
  console.log(`   client sees meals: ${monMeal(cView.body).map(i => `"${i.content.slice(0,28)}"`).join(", ")}`);
  expect("nutrition coach's note is NOT visible to the strength coach", !monMeal(tView.body).some(i => i.content.includes("whey")));
  expect("client sees BOTH coaches' meal items", monMeal(cView.body).some(i => i.content.includes("whey")) && monMeal(cView.body).some(i => i.content.includes("chicken")));
  let filtered = await api(GW, "GET", `/schedule/me?author=${nutid}`, C);
  console.log(`   client filters by nutrition coach -> meals: ${monMeal(filtered.body).map(i => `"${i.content.slice(0,28)}"`).join(", ")}`);
  expect("filter by coach shows only that coach's items", monMeal(filtered.body).length === 1 && monMeal(filtered.body)[0].content.includes("whey"));

  console.log("\n9) Pro client asks the AI coach to add a finisher (internal, secret-gated)");
  await api(DIRECT, "POST", `/internal/clients/${cid}/chatbot/items`, null, { replace: false, items: [{ tableType: "EXERCISE", weekday: "MON", content: "AI: 10-min incline walk finisher" }] }, { "X-Internal-Secret": INTERNAL });
  cView = await api(GW, "GET", "/schedule/me", C);
  console.log(`   client MON exercises now: ${monEx(cView.body).map(i => `${i.authorType}:"${i.content.slice(0,22)}"`).join(", ")}`);
  expect("chatbot item appears for the client", monEx(cView.body).some(i => i.authorType === "CHATBOT"));
  tView = await api(GW, "GET", `/schedule/clients/${cid}`, T);
  expect("coach does NOT see the chatbot item", !monEx(tView.body).some(i => i.authorType === "CHATBOT"));

  console.log("\n10) The strength coach's subscription expires");
  await publishBundle("subscription.bundle.expired", { participantId: cid, sellerId: tid, sellerType: "TRAINER", status: "EXPIRED" });
  let locked = false;
  for (let i = 0; i < 20 && !locked; i++) { await sleep(300); const t = await api(GW, "POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "x" }); locked = t.status === 403; }
  cView = await api(GW, "GET", "/schedule/me", C);
  console.log(`   ex-coach write access -> ${locked ? "revoked (403)" : "STILL OPEN"}; client still sees their plan? ${monEx(cView.body).some(i => i.content.startsWith("Back squat"))}`);
  expect("ex-coach is locked out but the client keeps the whole plan", locked && monEx(cView.body).some(i => i.content.startsWith("Back squat")));

  console.log(`\n${problems === 0 ? "✓ FUNCTIONAL JOURNEY OK — behaved as expected at every step" : "✗ " + problems + " step(s) did not behave as expected"}`);
  process.exit(problems === 0 ? 0 : 1);
})();
