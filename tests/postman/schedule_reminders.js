// End-to-end test — Slice 6b: end-of-day reminder publishing over RabbitMQ.
// Binds a queue to dumble.events (schedule.reminder.*) via the management API,
// then verifies the sweeper emits a reminder for a client with unmarked items
// today and NOT for a client whose items are all done.
//
// The service must run with SCHEDULE_REMINDER_FROM_HOUR=0 (window = all day) and
// a short SCHEDULE_REMINDER_SWEEP_MS so a sweep happens during the test.
//
// Run: node tests/postman/schedule_reminders.js

const crypto = require("crypto");
const SCHED = process.env.SCHEDULE_HOST_URL || "http://localhost:8186/api";
const SECRET = process.env.JWT_SECRET;
if (!SECRET) { console.error("ERROR: JWT_SECRET env var is required (source release/.env)."); process.exit(2); }
const MGMT = process.env.RABBIT_MGMT_URL || "http://localhost:15673";
const RAUTH = "Basic " + Buffer.from(`${process.env.RABBIT_USER || "app"}:${process.env.RABBIT_PASS || "app"}`).toString("base64");
const KEY = Buffer.from(SECRET, "base64");

let pass = 0, fail = 0;
const ck = (l, ok, d) => { ok ? (pass++, console.log("  ✓ " + l)) : (fail++, console.log("  ✗ " + l + (d ? "  — " + d : ""))); };
const b64 = (b) => Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function mint(userId) {
  const h = b64(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const p = b64(JSON.stringify({ sub: userId, userId, userType: "PARTICIPANT", iat: now, exp: now + 3600 }));
  return `${h}.${p}.${b64(crypto.createHmac("sha256", KEY).update(h + "." + p).digest())}`;
}
const j = async (r) => { const t = await r.text(); try { return JSON.parse(t); } catch { return t; } };
async function api(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${SCHED}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
async function mgmt(method, path, body) {
  const res = await fetch(`${MGMT}${path}`, { method, headers: { Authorization: RAUTH, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const WEEKDAY = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"][new Date().getUTCDay()];

(async () => {
  console.log(`Schedule: ${SCHED}\nRabbit mgmt: ${MGMT}\nToday (UTC): ${WEEKDAY}`);
  const q = "test.reminders." + crypto.randomUUID().slice(0, 8);

  // bind a queue to the reminder events
  let r = await mgmt("PUT", `/api/queues/%2F/${q}`, { durable: false, auto_delete: true });
  ck("create test queue", r.status === 201 || r.status === 204, `status=${r.status}`);
  r = await mgmt("POST", `/api/bindings/%2F/e/dumble.events/q/${q}`, { routing_key: "schedule.reminder.*" });
  ck("bind queue to schedule.reminder.*", r.status === 201 || r.status === 204, `status=${r.status}`);

  // client C: an unmarked item today → should be reminded
  const cid = crypto.randomUUID(), C = mint(cid);
  await api("POST", "/schedule/me/items", C, { tableType: "EXERCISE", weekday: WEEKDAY, content: "today's unmarked workout" });

  // client D: item today but marked done → should NOT be reminded
  const did = crypto.randomUUID(), D = mint(did);
  r = await api("POST", "/schedule/me/items", D, { tableType: "EXERCISE", weekday: WEEKDAY, content: "today's done workout" });
  await api("PUT", `/schedule/me/items/${r.body.id}/completion`, D, { done: true });

  // wait for sweep cycles, then drain the queue
  console.log("\n=== waiting for the reminder sweep ===");
  let msgs = [];
  for (let i = 0; i < 8; i++) {
    await sleep(2500);
    r = await mgmt("POST", `/api/queues/%2F/${q}/get`, { count: 500, ackmode: "ack_requeue_false", encoding: "auto" });
    if (Array.isArray(r.body)) msgs.push(...r.body);
    if (msgs.some(m => { try { return JSON.parse(m.payload).userId === cid; } catch { return false; } })) break;
  }
  const parsed = msgs.map(m => { try { return JSON.parse(m.payload); } catch { return {}; } });
  const forC = parsed.find(p => p.userId === cid);
  const forD = parsed.find(p => p.userId === did);

  ck("reminder emitted for the client with unmarked items", !!forC, `got ${parsed.length} reminders, none for C`);
  ck("reminder payload has pendingCount >= 1 + date + timezone", forC && forC.pendingCount >= 1 && !!forC.date && !!forC.timezone, `payload=${JSON.stringify(forC)}`);
  ck("NO reminder for the client whose items are all done", !forD, `unexpected: ${JSON.stringify(forD)}`);

  await mgmt("DELETE", `/api/queues/%2F/${q}`);
  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
