// Observability harness for Wallet.
//
// Tests current state across 5 surfaces — each check is either OK (works
// today) or FINDING (works but doesn't, recommended improvement). Run output
// distinguishes the two so the team can see what's instrumented + what isn't
// without the whole suite going red.
//
// Surfaces:
//   1. /actuator/health + readiness probes are wired correctly
//   2. /actuator/metrics returns a metric list, individual metric pages work
//   3. traceparent (W3C trace context) propagation through HTTP + outbox
//   4. Log structure (timestamps, levels, correlation ids, PII redaction)
//   5. Audit log correlates with state changes (the trio: log ↔ audit ↔ outbox)
//
// Run: node tests/postman/observability_wallet.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const COMPOSE_BASE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

const SYSTEM_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";

const systemKey = Buffer.from(SYSTEM_KEY_B64, "base64");
const userKey = Buffer.from(USER_KEY_B64, "base64");
const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
function jwt(claims, signingKey) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const stamp = Date.now();
const TEST_USER = `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const systemJwt = jwt({ iss: "obs", aud: "wallet", iat: now, exp }, systemKey);
const userJwt = jwt(
  { sub: "obs@dumble.test", userId: TEST_USER, displayName: "Obs", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
  userKey
);

let okCount = 0;
let findingCount = 0;
let failCount = 0;
const findings = [];

function ok(label) { okCount += 1; console.log(`  ✓ ${label}`); }
function finding(label, detail) {
  findingCount += 1;
  findings.push({ label, detail });
  console.log(`  ⚠ FINDING: ${label}${detail ? "  — " + detail : ""}`);
}
function fail(label, detail) {
  failCount += 1;
  console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`);
}

async function postJson(path, body, headers) {
  const res = await fetch(`${WALLET_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, headers: Object.fromEntries(res.headers) };
}
async function getJson(path, headers = {}) {
  const res = await fetch(`${WALLET_URL}${path}`, { headers });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, headers: Object.fromEntries(res.headers) };
}

function recentLogs(secs = 30) {
  const r = spawnSync("docker", ["compose", ...COMPOSE_BASE, "logs", "--since", `${secs}s`, "wallet"], { encoding: "utf8" });
  return r.stdout || "";
}
function psql(sql) {
  const r = spawnSync(
    "docker",
    ["compose", ...COMPOSE_BASE, "exec", "-T", "wallet-db", "psql", "-U", "postgres", "-d", "dumble_wallet", "-tA", "-c", sql],
    { encoding: "utf8" }
  );
  return { code: r.status || 0, stdout: r.stdout || "", stderr: r.stderr || "" };
}

// ── 1. health + info ─────────────────────────────────────────────────────────
async function healthAndInfo() {
  console.log("\n=== 1. /actuator/health + /actuator/info ===");

  const h = await getJson("/api/actuator/health");
  if (h.status === 200 && h.body && h.body.status === "UP") ok(`/actuator/health = UP`);
  else fail(`/actuator/health not UP (status=${h.status} body=${JSON.stringify(h.body).slice(0,100)})`);

  const liveness = await getJson("/api/actuator/health/liveness");
  const readiness = await getJson("/api/actuator/health/readiness");
  if (liveness.status === 200 && liveness.body?.status === "UP") ok(`/health/liveness = UP (k8s liveness probe wired)`);
  else finding(`/health/liveness missing or not UP`, `status=${liveness.status}`);
  if (readiness.status === 200 && readiness.body?.status === "UP") ok(`/health/readiness = UP (k8s readiness probe wired)`);
  else finding(`/health/readiness missing or not UP`, `status=${readiness.status}`);

  const info = await getJson("/api/actuator/info");
  if (info.status === 200) ok(`/actuator/info reachable`);
  else finding(`/actuator/info not reachable (status=${info.status})`, "ops dashboards rely on this for build metadata");
}

// ── 2. metrics ───────────────────────────────────────────────────────────────
async function metrics() {
  console.log("\n=== 2. /actuator/metrics ===");

  const list = await getJson("/api/actuator/metrics");
  if (list.status !== 200 || !Array.isArray(list.body?.names)) {
    finding(`/actuator/metrics list not exposed`, `status=${list.status}`);
    return;
  }
  ok(`/actuator/metrics lists ${list.body.names.length} metrics`);

  const wanted = ["jvm.memory.used", "http.server.requests", "hikaricp.connections.active"];
  for (const m of wanted) {
    if (list.body.names.includes(m)) ok(`exposed metric: ${m}`);
    else finding(`missing metric: ${m}`, `op dashboards expect this name`);
  }

  // Drive a credit to populate http.server.requests, then read the metric.
  await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 1, source: "BAN_REFUND", externalRef: `obs-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-obs-${stamp}` }
  );
  const httpMetric = await getJson("/api/actuator/metrics/http.server.requests");
  if (httpMetric.status === 200 && httpMetric.body?.measurements?.length > 0) {
    const count = httpMetric.body.measurements.find((m) => m.statistic === "COUNT")?.value;
    if (count > 0) ok(`http.server.requests COUNT increments (${count} requests observed)`);
    else finding(`http.server.requests has COUNT=0`, "Spring Boot HTTP auto-instrumentation may be disabled");
  } else {
    finding(`http.server.requests metric not readable`, `status=${httpMetric.status}`);
  }

  // Prometheus exposition format — common ops requirement.
  const prom = await getJson("/api/actuator/prometheus");
  if (prom.status === 200) ok(`/actuator/prometheus exposed (scrape-ready)`);
  else finding(`/actuator/prometheus not exposed (status=${prom.status})`, "If using Prometheus, add micrometer-registry-prometheus + expose endpoint");
}

// ── 3. trace propagation ─────────────────────────────────────────────────────
async function tracing() {
  console.log("\n=== 3. Trace propagation (W3C trace context) ===");

  const traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
  const spanId = "00f067aa0ba902b7";
  const traceparent = `00-${traceId}-${spanId}-01`;

  const r = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 1, source: "BAN_REFUND", externalRef: `trace-${stamp}` },
    {
      Authorization: `Bearer ${systemJwt}`,
      "Idempotency-Key": `k-trace-${stamp}`,
      traceparent,
    }
  );
  if (r.status === 201) ok(`request with traceparent header accepted (201)`);

  // The strongest signal Wallet propagates the trace is the trace id appearing
  // in its logs OR in the response headers (traceresponse).
  await new Promise((res) => setTimeout(res, 500));
  const logs = recentLogs(20);
  const inLogs = logs.includes(traceId);
  if (inLogs) ok(`trace id ${traceId.slice(0, 8)}… appears in wallet logs (trace-aware log pattern)`);
  else finding(`trace id NOT in wallet logs`, "Wallet has no Micrometer Tracing dep + no traceparent-aware logback pattern; add io.micrometer:micrometer-tracing-bridge-otel + management.tracing.sampling.probability=1.0 + %mdc-aware pattern");

  if (r.headers.traceresponse || r.headers["traceresponse"]) ok(`Wallet sets traceresponse on the way out`);
  else finding(`Wallet doesn't set traceresponse response header`, "Same fix as above; clients can't correlate without it");

  // Check whether the outbox row carries any trace identifier (some Spring AMQP
  // integrations stamp the message properties with traceparent so consumers
  // can resume the trace span).
  const outbox = psql(`SELECT payload_json FROM outbox_events WHERE created_at > now() - interval '30 seconds' ORDER BY created_at DESC LIMIT 1;`);
  if (outbox.stdout.includes(traceId)) ok(`outbox row carries the inbound trace id (cross-bus correlation)`);
  else finding(`outbox events don't carry trace context`, "Bus consumers can't reconstruct the cross-service trace span without traceparent header forwarding");
}

// ── 4. log structure ────────────────────────────────────────────────────────
async function logStructure() {
  console.log("\n=== 4. Log structure ===");

  await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 1, source: "BAN_REFUND", externalRef: `logs-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-logs-${stamp}` }
  );
  await new Promise((res) => setTimeout(res, 500));
  const logs = recentLogs(15);
  const lines = logs.split("\n").filter((l) => l.includes("wallet-1"));

  if (lines.length > 0) ok(`wallet emits log lines (${lines.length} in last 15s)`);
  else fail(`no log lines from wallet — something is wrong`);

  // Plain timestamp at the start of every line (Spring Boot default pattern).
  const hasTimestamp = lines.some((l) => /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(l));
  if (hasTimestamp) ok(`log lines carry ISO-8601 timestamps`);
  else fail(`log lines lack timestamps`);

  // Levels present (INFO/WARN/ERROR/DEBUG)
  const hasLevels = lines.some((l) => / INFO | WARN | ERROR | DEBUG /.test(l));
  if (hasLevels) ok(`log lines carry levels`);
  else fail(`log lines lack levels`);

  // JSON structured logs (preferred for ELK / Datadog ingest)
  const isJson = lines.some((l) => {
    const idx = l.indexOf("{");
    if (idx === -1) return false;
    try { JSON.parse(l.slice(idx)); return true; } catch { return false; }
  });
  if (isJson) ok(`log lines emit JSON (structured)`);
  else finding(`log lines are plain text, not JSON-structured`, "ELK/Datadog ingest needs JSON; add logstash-logback-encoder + logback-spring.xml JSON layout");

  // PII redaction check: a logged credit operation should NOT emit the full
  // userId in plain text. (Wallet does log userId in some lines — acceptable —
  // but credit card numbers, full JWTs, etc. should never appear.) We probe
  // the realistic risk: did anything leak from headers or bodies into logs?
  const jwtPrefix = userJwt.slice(0, 60);
  if (lines.some((l) => l.includes(jwtPrefix))) {
    fail(`Wallet logs leak JWT tokens verbatim`);
  } else {
    ok(`JWT tokens not echoed in logs`);
  }

  // Wallet logs userId quite freely. For Egyptian PII (ID numbers, full IBANs)
  // we'd want redaction, but those aren't in our test traffic to probe with.
  // Flag this as a forward-looking finding.
  finding(`no automatic PII redaction policy verified`,
    "Recommend adding a logback MaskingPatternLayout or similar before launch — IBANs, last4, full userIds should be masked in INFO-level logs");
}

// ── 5. log ↔ audit ↔ outbox trio correlation ────────────────────────────────
async function trioCorrelation() {
  console.log("\n=== 5. log ↔ audit ↔ outbox trio correlation ===");

  const tag = `trio-${stamp}`;
  await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 77, source: "BAN_REFUND", externalRef: tag },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-${tag}` }
  );
  await new Promise((res) => setTimeout(res, 1500));

  // Outbox row for this credit
  const outboxQ = psql(
    `SELECT payload_json FROM outbox_events WHERE payload_json LIKE '%${tag}%' AND created_at > now() - interval '10 seconds';`
  );
  const outboxOk = outboxQ.code === 0 && outboxQ.stdout.includes(tag);
  if (outboxOk) ok(`outbox row references externalRef '${tag}'`);
  else fail(`no outbox row found for externalRef '${tag}'`);

  // Audit row
  const auditQ = psql(
    `SELECT payload_json FROM wallet_event_log WHERE wallet_user_id = '${TEST_USER}' AND timestamp > now() - interval '10 seconds';`
  );
  const auditOk = auditQ.code === 0 && auditQ.stdout.includes(tag);
  if (auditOk) ok(`audit row references externalRef '${tag}'`);
  else finding(`audit log row for the credit does not carry externalRef`,
    "When investigating a customer dispute, the audit row is the canonical record — including externalRef makes cross-system search trivial");

  // Log line referencing the user — Wallet logs the wallet user id on credit.
  const logs = recentLogs(20);
  const logOk = logs.includes(TEST_USER);
  if (logOk) ok(`log line references the wallet user id`);
  else finding(`log lines don't reference the operation's user id`,
    "Without that, ops can't filter logs by affected user during an incident");
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Wallet URL: ${WALLET_URL}`);
  console.log(`Test user:  ${TEST_USER}`);

  try {
    await healthAndInfo();
    await metrics();
    await tracing();
    await logStructure();
    await trioCorrelation();
  } catch (ex) {
    console.error("Observability harness threw:", ex);
    failCount += 1;
  }

  console.log("\n— Summary —");
  console.log(`  OK:       ${okCount}`);
  console.log(`  FINDING:  ${findingCount}  (recommended improvements, not blockers)`);
  console.log(`  FAIL:     ${failCount}  (broken; must fix)`);
  if (findingCount > 0) {
    console.log("\n— Findings detail —");
    for (const f of findings) console.log(`  • ${f.label}${f.detail ? " — " + f.detail : ""}`);
  }
  console.log(`\n${failCount === 0 ? "✓ no broken assertions" : "✗ broken assertions present"}`);
  process.exit(failCount === 0 ? 0 : 1);
})();
