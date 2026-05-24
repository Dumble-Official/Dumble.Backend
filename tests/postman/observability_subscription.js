// Observability harness for Subscription.
//
// 5 surfaces — each check is OK (works today) or FINDING (recommended
// improvement). Output distinguishes the two so the suite is informative
// without going red on platform recommendations.
//
// Run: node tests/postman/observability_subscription.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const userKey = Buffer.from(USER_KEY_B64, "base64");
const b64u = (b) => Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function jwt(claims, key) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", key).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const stamp = Date.now();
const PARTICIPANT = `00000099-0000-0000-${stamp.toString(16).padStart(16, "0").slice(-4)}-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const userJwt = jwt({
  sub: `obs-${stamp}@dumble.test`, userId: PARTICIPANT, displayName: "Obs",
  userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp,
}, userKey);

let ok = 0, finding = 0, fail = 0;
const findings = [];
function OK(label)        { ok++; console.log(`  ✓ ${label}`); }
function FINDING(l, d)    { finding++; findings.push({l,d}); console.log(`  ⚠ FINDING: ${l}${d ? "  — " + d : ""}`); }
function FAIL(l, d)       { fail++; console.log(`  ✗ ${l}${d ? "  — " + d : ""}`); }

async function get(path, headers = {}) {
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, { headers });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, headers: Object.fromEntries(res.headers) };
}
async function post(path, body, headers) {
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, headers: Object.fromEntries(res.headers) };
}
function recentLogs(secs = 30) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "logs", "--since", `${secs}s`, "subscription"], { encoding: "utf8" });
  return r.stdout || "";
}
function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "subscription-db", "psql",
    "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql], { encoding: "utf8" });
  return { code: r.status || 0, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}

(async () => {
  console.log(`Subscription: ${SUBSCRIPTION_URL}`);
  console.log(`Test user:    ${PARTICIPANT.slice(-12)}`);

  // 1. health + info
  console.log("\n=== 1. /actuator/health + /actuator/info ===");
  const h = await get("/api/actuator/health");
  (h.status === 200 && h.body?.status === "UP")
    ? OK(`/actuator/health = UP`)
    : FAIL(`/actuator/health not UP`, `status=${h.status}`);

  const liveness = await get("/api/actuator/health/liveness");
  (liveness.status === 200 && liveness.body?.status === "UP")
    ? OK(`/health/liveness UP (k8s probe wired)`)
    : FINDING(`/health/liveness missing`, `status=${liveness.status}`);

  const readiness = await get("/api/actuator/health/readiness");
  (readiness.status === 200 && readiness.body?.status === "UP")
    ? OK(`/health/readiness UP (k8s probe wired)`)
    : FINDING(`/health/readiness missing`, `status=${readiness.status}`);

  const info = await get("/api/actuator/info");
  (info.status === 200) ? OK(`/actuator/info reachable`)
                        : FINDING(`/actuator/info missing`, `status=${info.status}`);

  // 2. metrics
  console.log("\n=== 2. /actuator/metrics ===");
  const metrics = await get("/api/actuator/metrics");
  if (metrics.status === 200 && Array.isArray(metrics.body?.names)) {
    OK(`/actuator/metrics lists ${metrics.body.names.length} metrics`);
  } else {
    FINDING(`/actuator/metrics not publicly exposed`, `status=${metrics.status} (decide policy: scrape-internal vs gated)`);
  }
  const prom = await get("/api/actuator/prometheus");
  (prom.status === 200)
    ? OK(`/actuator/prometheus scrape-ready`)
    : FINDING(`/actuator/prometheus not exposed`, `add micrometer-registry-prometheus + expose endpoint`);

  // 3. tracing
  console.log("\n=== 3. Trace propagation (W3C trace context) ===");
  const traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
  const r = await post("/api/me/plan/upgrade",
    { paymentMethodToken: `tok-obs-${stamp}`, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-obs-trace-${stamp}`,
      traceparent: `00-${traceId}-00f067aa0ba902b7-01` });
  (r.status >= 200 && r.status < 300)
    ? OK(`request with traceparent accepted (${r.status})`)
    : FAIL(`upgrade with traceparent failed`, `status=${r.status}`);

  await new Promise((res) => setTimeout(res, 500));
  const logs = recentLogs(20);
  logs.includes(traceId)
    ? OK(`trace id appears in subscription logs`)
    : FINDING(`trace id NOT in logs`, "add micrometer-tracing-bridge-otel + traceparent-aware logback pattern");

  (r.headers.traceresponse || r.headers["traceresponse"])
    ? OK(`Subscription sets traceresponse outbound`)
    : FINDING(`no traceresponse outbound header`, "comes free with Micrometer Tracing");

  const outboxRow = psql(`SELECT payload_json FROM outbox_events WHERE created_at > now() - interval '30 seconds' ORDER BY created_at DESC LIMIT 1;`);
  outboxRow.stdout.includes(traceId)
    ? OK(`outbox event carries trace id (cross-bus correlation)`)
    : FINDING(`outbox events don't carry trace context`, "AMQP message headers should forward traceparent");

  // 4. log structure
  console.log("\n=== 4. Log structure ===");
  const lines = logs.split("\n").filter((l) => l.includes("subscription-1"));
  lines.length > 0 ? OK(`subscription emits log lines (${lines.length} in 20s)`)
                   : FAIL(`no log lines from subscription`);

  lines.some((l) => /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(l))
    ? OK(`ISO-8601 timestamps`) : FAIL(`no timestamps`);

  lines.some((l) => / INFO | WARN | ERROR | DEBUG /.test(l))
    ? OK(`log levels`) : FAIL(`no log levels`);

  const isJson = lines.some((l) => {
    const idx = l.indexOf("{");
    if (idx < 0) return false;
    try { JSON.parse(l.slice(idx)); return true; } catch { return false; }
  });
  isJson ? OK(`structured JSON logs`)
         : FINDING(`logs are plain text, not JSON-structured`, "add logstash-logback-encoder + logback-spring.xml");

  const jwtPrefix = userJwt.slice(0, 60);
  lines.some((l) => l.includes(jwtPrefix))
    ? FAIL(`Subscription logs leak JWT tokens verbatim`)
    : OK(`JWT tokens not echoed in logs`);

  FINDING(`no automatic PII redaction policy verified`,
    "add a logback MaskingPatternLayout for IBANs, last4, full userIds");

  // 5. trio: log ↔ audit ↔ outbox
  console.log("\n=== 5. log ↔ audit ↔ outbox trio correlation ===");
  const tag = `obs-trio-${stamp}`;
  const upgrade = await post("/api/me/plan/upgrade",
    { paymentMethodToken: tag, paymentMethodType: "CARD" },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-${tag}` });
  await new Promise((res) => setTimeout(res, 2000));
  const outboxQ = psql(`SELECT payload_json FROM outbox_events WHERE created_at > now() - interval '10 seconds' ORDER BY created_at DESC LIMIT 1;`);
  outboxQ.stdout.length > 0 ? OK(`outbox row created for the upgrade`)
                            : FINDING(`no outbox row found for the upgrade in window`);

  const recentLogs2 = recentLogs(15);
  recentLogs2.includes(PARTICIPANT)
    ? OK(`log line references the test user id`)
    : FINDING(`logs don't reference the user id`, "without it, on-call can't filter logs by affected user");

  console.log("\n— Summary —");
  console.log(`  OK:       ${ok}`);
  console.log(`  FINDING:  ${finding}  (recommended improvements, not blockers)`);
  console.log(`  FAIL:     ${fail}  (broken; must fix)`);
  if (finding) {
    console.log("\n— Findings detail —");
    for (const f of findings) console.log(`  • ${f.l}${f.d ? " — " + f.d : ""}`);
  }
  console.log(`\n${fail === 0 ? "✓ no broken assertions" : "✗ broken assertions present"}`);
  process.exit(fail === 0 ? 0 : 1);
})();
