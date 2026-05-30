// Observability probes for Auth.
//
// 5 surfaces — each check is OK (works today) or FINDING (recommended).
// Output distinguishes the two so the suite is informative without going
// red on platform-recommendation items.
//
// Run: node tests/postman/observability_auth.js

const { spawnSync } = require("child_process");

const AUTH_URL = process.env.AUTH_HOST_URL || "http://localhost:18081";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];
const stamp = Date.now();

let ok = 0, finding = 0, fail = 0;
function OK(l)        { ok++; console.log(`  ✓ ${l}`); }
function FAIL(l, d)   { fail++; console.log(`  ✗ ${l}${d ? "  — " + d : ""}`); }
function FINDING(l, d){ finding++; console.log(`  ⚠ FINDING: ${l}${d ? "  — " + d : ""}`); }

async function req(method, path, body, headers = {}) {
  const opts = { method, headers };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(`${AUTH_URL}${path}`, opts);
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed, headers: Object.fromEntries(res.headers) };
}
function recentLogs(secs = 30) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "logs", "--since", `${secs}s`, "authentication"], { encoding: "utf8" });
  return r.stdout || "";
}

(async () => {
  console.log(`Auth: ${AUTH_URL}`);

  console.log("\n=== 1. /actuator/health surface ===");
  const h = await req("GET", "/actuator/health");
  (h.status === 200 && h.body?.status === "UP")
    ? OK(`/actuator/health UP`)
    : FINDING(`/actuator/health not UP or not exposed`, `status=${h.status}`);

  const liv = await req("GET", "/actuator/health/liveness");
  (liv.status === 200) ? OK(`/health/liveness (k8s probe wired)`)
                       : FINDING(`/health/liveness missing`, `status=${liv.status}`);

  const ready = await req("GET", "/actuator/health/readiness");
  (ready.status === 200) ? OK(`/health/readiness (k8s probe wired)`)
                         : FINDING(`/health/readiness missing`, `status=${ready.status}`);

  console.log("\n=== 2. /actuator/metrics + /prometheus ===");
  const m = await req("GET", "/actuator/metrics");
  (m.status === 200 && Array.isArray(m.body?.names))
    ? OK(`/actuator/metrics lists ${m.body.names.length} metrics`)
    : FINDING(`/actuator/metrics not publicly exposed`, `status=${m.status}`);

  const prom = await req("GET", "/actuator/prometheus");
  (prom.status === 200)
    ? OK(`/actuator/prometheus scrape-ready`)
    : FINDING(`/actuator/prometheus not exposed`, "add micrometer-registry-prometheus");

  console.log("\n=== 3. No password / JWT secret leaks in logs ===");
  // Generate some activity then sample logs
  const email = `obs-${stamp}@dumble.test`;
  const PLAIN = "validpass123-obs-secret";
  await req("POST", "/api/auth/register", {
    firstName: "Obs", lastName: "Test", email, password: PLAIN,
  });
  await req("POST", "/api/auth/login", { email, password: PLAIN });
  await req("POST", "/api/auth/login", { email, password: "wrong-pass-obs-sec" });
  await new Promise(r => setTimeout(r, 1000));

  const logs = recentLogs(30);
  logs.includes(PLAIN)
    ? FAIL(`PLAINTEXT password '${PLAIN.slice(0,8)}...' appears in logs`)
    : OK(`password does not appear in logs verbatim`);

  // bcrypt $2a$ prefix — any log line containing a stored hash is a leak
  logs.includes("$2a$") || logs.includes("$2b$")
    ? FINDING(`bcrypt hash prefix '$2a$' appears in logs — could leak the stored hash`,
        "audit log statements for any user.passwordHash references")
    : OK(`bcrypt hash not echoed in logs`);

  // JWT secret check — application property is base64 so don't search for
  // exact match. Instead check for the literal env var name reference,
  // which would indicate a config-dump.
  logs.includes("JWT_SECRET=")
    ? FAIL(`JWT_SECRET env var value visible in logs`)
    : OK(`JWT_SECRET not echoed verbatim`);

  console.log("\n=== 4. Log structure ===");
  const lines = logs.split("\n").filter(l => l.includes("authentication"));
  lines.length > 0
    ? OK(`auth emits log lines (${lines.length} in 30s)`)
    : FAIL(`no log lines from auth`);

  // Spring's default console layout: "2026-... INFO ... --- [thread] logger : msg"
  // Hibernate's show-sql=true emits raw "Hibernate: SELECT ..." lines that
  // bypass the layout entirely. So we look for AT LEAST one matching line
  // rather than require all lines.
  lines.some(l => /\d{4}-\d{2}-\d{2}T?\d{2}:\d{2}:\d{2}/.test(l))
    ? OK(`at least one line carries ISO-8601 timestamp`)
    : FINDING(`no timestamped lines visible (layout may be misconfigured)`);
  lines.some(l => /\b(INFO|WARN|ERROR|DEBUG)\b/.test(l))
    ? OK(`at least one line carries log level`)
    : FINDING(`no leveled lines visible (layout may be misconfigured)`);

  const hibernateLines = lines.filter(l => l.includes("Hibernate:")).length;
  if (hibernateLines > 10 && hibernateLines > lines.length * 0.5) {
    FINDING(`spring.jpa.show-sql=true floods logs (${hibernateLines}/${lines.length} are SQL)`,
      "disable in non-dev profiles; kills log volume + cost in production");
  }

  const isJson = lines.some(l => {
    const idx = l.indexOf("{");
    if (idx < 0) return false;
    try { JSON.parse(l.slice(idx)); return true; } catch { return false; }
  });
  isJson ? OK(`structured JSON logs`)
         : FINDING(`logs are plain text, not JSON-structured`,
             "add logstash-logback-encoder + logback-spring.xml");

  console.log("\n=== 5. Failed-login auditability ===");
  // Auth currently has NO persisted audit log (per surface map). Document
  // that gap so on-call can't investigate "who tried to log in as alice@
  // last hour from where".
  if (lines.some(l => /BadCredentials|Invalid email or password|login.*fail/i.test(l))) {
    OK(`failed login attempts are at least logged to console`);
  } else {
    FINDING(`failed login attempts not visibly logged`,
      "ops can't reconstruct brute-force attempts without DB-level audit OR structured log");
  }
  FINDING(`no audit_log table for sensitive ops`,
    "register/login/change-password/ban events should land in a persistent table for forensic queries beyond log-retention window");

  console.log("\n— Summary —");
  console.log(`  OK:       ${ok}`);
  console.log(`  FINDING:  ${finding}  (recommended improvements, not blockers)`);
  console.log(`  FAIL:     ${fail}  (broken; must fix)`);
  process.exit(fail === 0 ? 0 : 1);
})();
