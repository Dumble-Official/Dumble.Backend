// End-to-end test for the Schedule service — Slice 3 (trainer authoring).
// Sub-gate (via the trainer↔client link read-model) + cross-coach privacy.
//
// Run: node tests/postman/schedule_trainer.js
//   SCHEDULE_HOST_URL   default http://localhost:8186/api
//   JWT_SECRET          must match the running service
//   INTERNAL_API_SECRET must match the running service

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
async function call(method, path, token, body, extraHeaders) {
  const headers = { ...(extraHeaders || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${SCHED}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}
const link = (trainerId, clientId, active, secret) =>
  call("POST", "/internal/trainer-links", null, { trainerId, clientId, active }, secret ? { "X-Internal-Secret": secret } : {});
const monEx = (resp) => (resp.exercises || []).find(d => d.weekday === "MON").items;
const has = (resp, text) => monEx(resp).some(i => i.content === text);

(async () => {
  console.log(`Schedule: ${SCHED}`);
  const tid = crypto.randomUUID(), yid = crypto.randomUUID(), cid = crypto.randomUUID(), nid = crypto.randomUUID();
  const T = mint(tid, "TRAINER"), Y = mint(yid, "TRAINER"), C = mint(cid, "PARTICIPANT"), N = mint(nid, "TRAINER");

  // ── internal link endpoint is secret-gated ────────────────────────
  console.log("\n=== internal link endpoint auth ===");
  let r = await link(tid, cid, true, null);
  ck("upsert link without secret → 401", r.status === 401, `status=${r.status}`);
  r = await link(tid, cid, true, "wrong-secret");
  ck("upsert link wrong secret → 401", r.status === 401, `status=${r.status}`);
  r = await link(tid, cid, true, INTERNAL);
  ck("upsert link with correct secret → 204", r.status === 204, `status=${r.status}`);

  // ── sub-gate on trainer writes ────────────────────────────────────
  console.log("\n=== sub-gate ===");
  r = await call("POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "T-bench 4x8" });
  const tItem = r.body.id;
  ck("trainer WITH active link → 201, item stamped TRAINER", r.status === 201 && r.body.authorType === "TRAINER" && r.body.authorId === tid, `status=${r.status} body=${JSON.stringify(r.body).slice(0,140)}`);
  r = await call("POST", `/schedule/clients/${cid}/items`, N, { tableType: "EXERCISE", weekday: "MON", content: "hack" });
  ck("trainer WITHOUT a link → 403", r.status === 403, `status=${r.status}`);

  // ── second coach + the client both author ─────────────────────────
  console.log("\n=== multiple coaches + client on one schedule ===");
  await link(yid, cid, true, INTERNAL);
  r = await call("POST", `/schedule/clients/${cid}/items`, Y, { tableType: "EXERCISE", weekday: "MON", content: "Y-squat 5x5" });
  const yItem = r.body.id;
  ck("coach Y (active link) adds an item → 201", r.status === 201 && r.body.authorType === "TRAINER", `status=${r.status}`);
  r = await call("POST", "/schedule/me/items", C, { tableType: "EXERCISE", weekday: "MON", content: "C-cardio 20min" });
  const cItem = r.body.id;
  ck("client adds own item → 201 (CLIENT)", r.status === 201 && r.body.authorType === "CLIENT", `status=${r.status}`);

  // ── cross-coach privacy ───────────────────────────────────────────
  console.log("\n=== cross-coach privacy ===");
  r = await call("GET", `/schedule/clients/${cid}`, T);
  ck("coach T sees own + client items, NOT coach Y's", has(r.body, "T-bench 4x8") && has(r.body, "C-cardio 20min") && !has(r.body, "Y-squat 5x5"), `T sees: ${monEx(r.body).map(i=>i.content)}`);
  r = await call("GET", `/schedule/clients/${cid}`, Y);
  ck("coach Y sees own + client items, NOT coach T's", has(r.body, "Y-squat 5x5") && has(r.body, "C-cardio 20min") && !has(r.body, "T-bench 4x8"), `Y sees: ${monEx(r.body).map(i=>i.content)}`);
  r = await call("GET", "/schedule/me", C);
  ck("client sees ALL three authors", has(r.body, "T-bench 4x8") && has(r.body, "Y-squat 5x5") && has(r.body, "C-cardio 20min"), `C sees: ${monEx(r.body).map(i=>i.content)}`);

  // ── per-coach filter on the client view ───────────────────────────
  console.log("\n=== per-coach filter (client choosing a coach) ===");
  r = await call("GET", `/schedule/me?author=${tid}`, C);
  ck("author=<T> → only T's item", monEx(r.body).length === 1 && has(r.body, "T-bench 4x8"), `=${monEx(r.body).map(i=>i.content)}`);
  r = await call("GET", `/schedule/me?author=${yid}`, C);
  ck("author=<Y> → only Y's item", monEx(r.body).length === 1 && has(r.body, "Y-squat 5x5"), `=${monEx(r.body).map(i=>i.content)}`);
  r = await call("GET", "/schedule/me?author=me", C);
  ck("author=me → only the client's item", monEx(r.body).length === 1 && has(r.body, "C-cardio 20min"), `=${monEx(r.body).map(i=>i.content)}`);

  // ── coach write boundaries ────────────────────────────────────────
  console.log("\n=== coach write boundaries ===");
  r = await call("PATCH", `/schedule/clients/${cid}/items/${tItem}`, T, { content: "T-bench 5x5" });
  ck("coach edits OWN item → 200", r.status === 200 && r.body.content === "T-bench 5x5", `status=${r.status}`);
  r = await call("PATCH", `/schedule/clients/${cid}/items/${yItem}`, T, { content: "steal" });
  ck("coach edits ANOTHER coach's item → 404", r.status === 404, `status=${r.status}`);
  r = await call("PATCH", `/schedule/clients/${cid}/items/${cItem}`, T, { content: "steal" });
  ck("coach edits the client's item → 404", r.status === 404, `status=${r.status}`);
  r = await call("DELETE", `/schedule/clients/${cid}/items/${yItem}`, T);
  ck("coach deletes another coach's item → 404", r.status === 404, `status=${r.status}`);

  // ── client edits a coach's item freely ────────────────────────────
  console.log("\n=== client edits freely ===");
  r = await call("PATCH", `/schedule/me/items/${tItem}`, C, { content: "C tweaked the coach plan" });
  ck("client edits a TRAINER item via /me → 200", r.status === 200 && r.body.content === "C tweaked the coach plan", `status=${r.status}`);

  // ── trainer meal target (gated) ───────────────────────────────────
  console.log("\n=== trainer meal target (gated) ===");
  r = await call("PUT", `/schedule/clients/${cid}/meal-targets/MON`, T, { calories: 2200, proteinG: 170, carbsG: 210, fatG: 70 });
  ck("coach with link sets client meal target → 200", r.status === 200 && r.body.calories === 2200, `status=${r.status}`);
  r = await call("PUT", `/schedule/clients/${cid}/meal-targets/MON`, N, { calories: 9 });
  ck("coach without link sets meal target → 403", r.status === 403, `status=${r.status}`);

  // ── revoking the sub cuts off the trainer ─────────────────────────
  console.log("\n=== sub revoked → access cut ===");
  r = await link(tid, cid, false, INTERNAL);
  ck("deactivate T↔C link → 204", r.status === 204, `status=${r.status}`);
  r = await call("POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "after revoke" });
  ck("trainer write after revoke → 403", r.status === 403, `status=${r.status}`);
  r = await call("GET", `/schedule/clients/${cid}`, T);
  ck("trainer read after revoke → 403", r.status === 403, `status=${r.status}`);
  r = await call("GET", "/schedule/me", C);
  ck("client still sees the (now ex-)coach's items — data persists", has(r.body, "C tweaked the coach plan"), `C sees: ${monEx(r.body).map(i=>i.content)}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
