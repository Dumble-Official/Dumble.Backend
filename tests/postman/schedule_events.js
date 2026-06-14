// End-to-end test — Slice 6a: the real Subscription→link feed over RabbitMQ.
// Publishes bundle-subscription events to the dumble.events exchange (via the
// RabbitMQ management API) and verifies the Schedule consumer flips the
// trainer-write gate accordingly.
//
// Run: node tests/postman/schedule_events.js
//   SCHEDULE_HOST_URL / JWT_SECRET
//   RABBIT_MGMT_URL (default http://localhost:15673)  RABBIT_USER/RABBIT_PASS (app/app)

const crypto = require("crypto");
const SCHED = process.env.SCHEDULE_HOST_URL || "http://localhost:8186/api";
const SECRET = process.env.JWT_SECRET || "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const MGMT = process.env.RABBIT_MGMT_URL || "http://localhost:15673";
const RUSER = process.env.RABBIT_USER || "app", RPASS = process.env.RABBIT_PASS || "app";
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
async function api(method, path, token, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${SCHED}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
async function publish(routingKey, payloadObj) {
  const res = await fetch(`${MGMT}/api/exchanges/%2F/dumble.events/publish`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: "Basic " + Buffer.from(`${RUSER}:${RPASS}`).toString("base64") },
    body: JSON.stringify({ properties: {}, routing_key: routingKey, payload: JSON.stringify(payloadObj), payload_encoding: "string" }),
  });
  return (await j(res)).routed;
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
// poll the trainer write-gate until it reaches the expected status (consumption is async)
async function gateReaches(trainerToken, clientId, expected, tries = 20) {
  for (let i = 0; i < tries; i++) {
    const r = await api("POST", `/schedule/clients/${clientId}/items`, trainerToken,
        { tableType: "EXERCISE", weekday: "MON", content: "gate probe " + i });
    if (r.status === expected) return true;
    await sleep(300);
  }
  return false;
}

(async () => {
  console.log(`Schedule: ${SCHED}\nRabbit mgmt: ${MGMT}`);
  const tid = crypto.randomUUID(), cid = crypto.randomUUID();
  const T = mint(tid, "TRAINER");

  console.log("\n=== before any event: no link ===");
  let r = await api("POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "x" });
  ck("trainer write with no link → 403", r.status === 403, `status=${r.status}`);

  console.log("\n=== publish bundle.activated (TRAINER seller) → link goes active ===");
  const routed = await publish("subscription.bundle.activated",
      { participantId: cid, sellerId: tid, sellerType: "TRAINER", status: "ACTIVE" });
  ck("event routed to a bound queue", routed === true, `routed=${routed}`);
  ck("consumer activates the link → trainer write now 201", await gateReaches(T, cid, 201), "gate never opened");

  console.log("\n=== publish bundle.expired → link goes inactive ===");
  await publish("subscription.bundle.expired", { participantId: cid, sellerId: tid, sellerType: "TRAINER", status: "EXPIRED" });
  ck("consumer deactivates the link → trainer write back to 403", await gateReaches(T, cid, 403), "gate never closed");

  console.log("\n=== GYM-seller bundle is ignored (no coaching link) ===");
  const gid = crypto.randomUUID(), gcid = crypto.randomUUID();
  const G = mint(gid, "TRAINER");
  await publish("subscription.bundle.activated", { participantId: gcid, sellerId: gid, sellerType: "GYM", status: "ACTIVE" });
  await sleep(1500); // give the consumer time; it should NOT create a link
  r = await api("POST", `/schedule/clients/${gcid}/items`, G, { tableType: "EXERCISE", weekday: "MON", content: "x" });
  ck("GYM-seller event grants no schedule link → 403", r.status === 403, `status=${r.status}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
