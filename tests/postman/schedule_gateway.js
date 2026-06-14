// End-to-end test — Slice 7: routing through the gateway.
// Client + trainer flows go client → gateway → schedule; /api/internal/** is
// confirmed NOT routed (service-to-service only).
//
// Run: node tests/postman/schedule_gateway.js   (gateway on :8090, schedule on :8186)

const crypto = require("crypto");
const GW = process.env.GATEWAY_URL || "http://localhost:8090/api";
const DIRECT = process.env.SCHEDULE_DIRECT_URL || "http://localhost:8186/api";
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
async function call(base, method, path, token, body, extra) {
  const headers = { ...(extra || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const res = await fetch(`${base}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: res.status, body: await j(res) };
}

(async () => {
  console.log(`Gateway: ${GW}\nDirect:  ${DIRECT}`);
  const cid = crypto.randomUUID(), tid = crypto.randomUUID();
  const C = mint(cid, "PARTICIPANT"), T = mint(tid, "TRAINER");

  // active coaching link set up via the DIRECT internal endpoint (not gateway-routed)
  await call(DIRECT, "POST", "/internal/trainer-links", null, { trainerId: tid, clientId: cid, active: true }, { "X-Internal-Secret": INTERNAL });

  console.log("\n=== client flow through the gateway ===");
  let r = await call(GW, "GET", "/schedule/me", C);
  ck("GET /api/schedule/me via gateway → 200", r.status === 200 && !!r.body.scheduleId, `status=${r.status}`);
  r = await call(GW, "POST", "/schedule/me/items", C, { tableType: "EXERCISE", weekday: "MON", content: "via gateway" });
  ck("client add item via gateway → 201", r.status === 201, `status=${r.status}`);

  console.log("\n=== trainer flow through the gateway ===");
  r = await call(GW, "POST", `/schedule/clients/${cid}/items`, T, { tableType: "EXERCISE", weekday: "MON", content: "coach via gateway" });
  ck("trainer add item for client via gateway → 201", r.status === 201 && r.body.authorType === "TRAINER", `status=${r.status}`);
  r = await call(GW, "GET", `/schedule/clients/${cid}`, T);
  ck("trainer reads client schedule via gateway → 200", r.status === 200, `status=${r.status}`);

  console.log("\n=== auth + internal-not-routed ===");
  r = await call(GW, "GET", "/schedule/me", null);
  ck("no token via gateway → 401", r.status === 401, `status=${r.status}`);
  r = await call(GW, "POST", "/internal/trainer-links", T, { trainerId: tid, clientId: cid, active: false });
  ck("/api/internal/** NOT routed through gateway → 404", r.status === 404, `status=${r.status} body=${JSON.stringify(r.body).slice(0,100)}`);

  console.log(`\n${fail === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${pass}/${pass + fail} checks passed`);
  process.exit(fail === 0 ? 0 : 1);
})();
