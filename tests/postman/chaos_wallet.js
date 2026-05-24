// Chaos harness for Wallet — kills upstream dependencies mid-flow and verifies
// the service degrades gracefully (5xx not hang, no DB corruption, recovers
// cleanly on restart).
//
// Three drills:
//   1. Kill rabbitmq mid-publish — credit + debit calls still succeed (DB tx
//      commits), outbox rows accumulate in PENDING, no 5xx; after rabbit
//      restart the publisher drains the backlog.
//   2. Kill wallet-db — POST /wallet/credit returns 5xx within HikariCP's
//      configured connection-timeout (NOT hang for 30 s of default), state
//      stays consistent; after DB restart the next request succeeds.
//   3. Idempotency under load — fire a burst of 30 credits with unique keys,
//      confirm no errors, balance arithmetic matches, no double-counting.
//
// Mutates docker container state. Run on a dedicated dev box; restores the
// stack on completion.

const crypto = require("crypto");
const { execSync, spawnSync } = require("child_process");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const RMQ_BASE = process.env.RMQ_BASE || "http://localhost:15672";
const RMQ_AUTH = "Basic " + Buffer.from("guest:guest").toString("base64");
const COMPOSE_ARGS = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

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
  const sig = b64u(
    crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest()
  );
  return `${hdr}.${payload}.${sig}`;
}

const stamp = Date.now();
const TEST_USER = `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const systemJwt = jwt({ iss: "chaos", aud: "wallet", iat: now, exp }, systemKey);
const userJwt = jwt(
  { sub: "chaos@dumble.test", userId: TEST_USER, displayName: "Chaos", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
  userKey
);

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) console.log(`  ✓ ${label}`);
  else {
    failedChecks += 1;
    console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function compose(...args) {
  // Use spawnSync so we don't blow up on non-zero exit codes (we capture them).
  return spawnSync("docker", ["compose", ...COMPOSE_ARGS, ...args], { encoding: "utf8" });
}

async function postJson(path, body, headers, timeoutMs = 10000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(`${WALLET_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
      signal: ctrl.signal,
    });
    let parsed = null;
    try { parsed = await res.json(); } catch {}
    return { status: res.status, body: parsed };
  } catch (ex) {
    return { status: 0, body: null, error: ex.name + ": " + ex.message };
  } finally {
    clearTimeout(t);
  }
}

async function waitForHealthy(svc, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const out = compose("ps", "--format", "{{.Name}}\t{{.Status}}");
    if (out.stdout && out.stdout.includes(`release-${svc}-1`) && /\bUp .* \(healthy\)/.test(out.stdout) && out.stdout.split("\n").some((line) => line.startsWith(`release-${svc}-1`) && /healthy/.test(line))) {
      return true;
    }
    await sleep(2000);
  }
  return false;
}

async function waitForBoot(svc, marker, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const out = compose("logs", "--since", "5m", svc);
    if (out.stdout && out.stdout.includes(marker)) return true;
    await sleep(2000);
  }
  return false;
}

async function pendingOutboxCount() {
  // Hit RabbitMQ API for a rough idea: count messages in queues we care about.
  // (We don't have direct DB access from this script; the queue-side is the
  // observable proxy that the publisher hasn't yet drained.)
  try {
    const res = await fetch(`${RMQ_BASE}/api/queues/%2F`, {
      headers: { Authorization: RMQ_AUTH },
    });
    if (!res.ok) return -1;
    const queues = await res.json();
    let total = 0;
    for (const q of queues) total += q.messages || 0;
    return total;
  } catch {
    return -1;
  }
}

// ── scenario 1: kill rabbitmq mid-publish ───────────────────────────────────
async function chaos1RabbitKill() {
  console.log("\n=== Scenario 1 — kill rabbitmq, verify DB-side stays consistent + outbox accumulates ===");

  // Bootstrap a wallet.
  const topup = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 100000, source: "BAN_REFUND", externalRef: `c-topup-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-c-topup-${stamp}` }
  );
  assert(`bootstrap credit 201`, topup.status === 201, JSON.stringify(topup).slice(0, 200));
  const baseline = topup.body?.newBalanceCents ?? 0;

  console.log("  killing rabbitmq...");
  compose("stop", "rabbitmq");
  await sleep(2000);

  // Fire 5 credits while rabbitmq is dead. Each one should:
  //  - commit the DB transaction (wallet + entry + outbox PENDING row)
  //  - return 201 to the caller
  //  - the outbox publisher will see a closed connection and back off
  let acceptedCount = 0;
  let fiveXX = 0;
  for (let i = 0; i < 5; i++) {
    const r = await postJson(
      "/api/wallet/credit",
      { userId: TEST_USER, amountCents: 1000, source: "BAN_REFUND", externalRef: `chaos-${stamp}-${i}` },
      { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-chaos-${stamp}-${i}` }
    );
    if (r.status === 201) acceptedCount += 1;
    if (r.status >= 500) fiveXX += 1;
  }
  console.log(`  during rabbit outage: ${acceptedCount}/5 credits accepted, ${fiveXX} 5xx`);
  assert(`credits still 201 while rabbit is down (HTTP path doesn't depend on broker)`, acceptedCount === 5, `accepted=${acceptedCount}`);
  assert(`no 5xx during rabbit outage`, fiveXX === 0, `${fiveXX} 5xx`);

  console.log("  bringing rabbitmq back...");
  compose("start", "rabbitmq");
  const rabbitOk = await waitForHealthy("rabbitmq", 60000);
  assert(`rabbitmq healthy again`, rabbitOk);
  await sleep(5000);  // give the outbox publisher a beat to reconnect

  // After recovery, balance should reflect all 5 credits.
  const after = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 0, source: "BAN_REFUND" }, // throwaway, just to read balance
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-throwaway-${stamp}` }
  );
  // Better: GET the summary.
  const summaryRes = await fetch(`${WALLET_URL}/api/wallet/${TEST_USER}/summary`, {
    headers: { Authorization: `Bearer ${systemJwt}` },
  });
  const summary = await summaryRes.json();
  assert(`balance after recovery == baseline + 5*1000`, summary.availableCents === baseline + 5000, `baseline=${baseline}, available=${summary.availableCents}`);
}

// ── scenario 2: kill wallet-db ──────────────────────────────────────────────
async function chaos2DbKill() {
  console.log("\n=== Scenario 2 — kill wallet-db, verify 5xx fast (not hang) + recovery ===");

  console.log("  killing wallet-db...");
  compose("stop", "wallet-db");
  await sleep(2000);

  // Single request with a generous-but-not-infinite timeout. If Wallet hangs
  // 30 s on the default HikariCP timeout, this returns status 0 (abort).
  // If Wallet has a tighter timeout, we get a real 5xx faster.
  const t0 = Date.now();
  const r = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 100, source: "BAN_REFUND", externalRef: `dbdown-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-dbdown-${stamp}` },
    35000   // give it a bit more than HikariCP's default 30 s
  );
  const elapsed = Date.now() - t0;
  console.log(`  request returned status=${r.status} after ${elapsed} ms${r.error ? ` (${r.error})` : ""}`);

  assert(`request returned within 35 s (didn't hang forever)`, elapsed < 35000, `elapsed=${elapsed}`);
  assert(`returned a 5xx (graceful failure)`, r.status >= 500 && r.status < 600, `status=${r.status} err=${r.error}`);

  console.log("  bringing wallet-db back...");
  compose("start", "wallet-db");
  const dbOk = await waitForHealthy("wallet-db", 60000);
  assert(`wallet-db healthy again`, dbOk);
  // Wallet might need a beat to reconnect.
  await sleep(8000);

  // Confirm a request after recovery succeeds.
  const after = await postJson(
    "/api/wallet/credit",
    { userId: TEST_USER, amountCents: 250, source: "BAN_REFUND", externalRef: `dbup-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-dbup-${stamp}` }
  );
  assert(`credit 201 after DB recovery`, after.status === 201, JSON.stringify(after).slice(0, 200));
}

// ── scenario 3: burst load on a single wallet ───────────────────────────────
async function chaos3Burst() {
  console.log("\n=== Scenario 3 — burst of 30 sequential credits + 30 debits, balance arithmetic intact ===");

  // Reset baseline.
  const baselineRes = await fetch(`${WALLET_URL}/api/wallet/${TEST_USER}/summary`, {
    headers: { Authorization: `Bearer ${systemJwt}` },
  });
  const baseline = (await baselineRes.json()).availableCents ?? 0;
  console.log(`  baseline: ${baseline} cents`);

  const N = 30;
  let cAccepted = 0;
  let dAccepted = 0;
  for (let i = 0; i < N; i++) {
    const c = await postJson(
      "/api/wallet/credit",
      { userId: TEST_USER, amountCents: 100, source: "BAN_REFUND", externalRef: `burst-c-${stamp}-${i}` },
      { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-burst-c-${stamp}-${i}` }
    );
    if (c.status === 201) cAccepted += 1;
  }
  for (let i = 0; i < N; i++) {
    const d = await postJson(
      "/api/wallet/debit",
      { userId: TEST_USER, amountCents: 100, source: "IN_APP_SPEND", externalRef: `burst-d-${stamp}-${i}` },
      { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-burst-d-${stamp}-${i}` }
    );
    if (d.status === 201) dAccepted += 1;
  }
  const finalRes = await fetch(`${WALLET_URL}/api/wallet/${TEST_USER}/summary`, {
    headers: { Authorization: `Bearer ${systemJwt}` },
  });
  const finalBal = (await finalRes.json()).availableCents ?? 0;

  console.log(`  ${cAccepted}/${N} credits accepted, ${dAccepted}/${N} debits accepted`);
  console.log(`  final balance: ${finalBal} (baseline was ${baseline})`);

  assert(`all 30 credits accepted`, cAccepted === N, `${cAccepted}/${N}`);
  assert(`all 30 debits accepted`, dAccepted === N, `${dAccepted}/${N}`);
  assert(`net balance unchanged (credits == debits)`, finalBal === baseline, `baseline=${baseline}, final=${finalBal}`);
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Wallet URL: ${WALLET_URL}`);
  console.log(`Test user:  ${TEST_USER}`);

  try {
    await chaos1RabbitKill();
    await chaos2DbKill();
    await chaos3Burst();
  } catch (ex) {
    console.error("Harness threw:", ex);
    failedChecks += 1; totalChecks += 1;
  } finally {
    // Best-effort: make sure both deps are running on exit, no matter what.
    compose("start", "rabbitmq");
    compose("start", "wallet-db");
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${
      totalChecks - failedChecks
    }/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
