// Time-based job harness for Subscription.
//
// Recreates the subscription container with release/docker-compose
// .subscription-fasttime.yml so the idempotency cleanup cron fires every 15s
// instead of nightly. Seeds expired idempotency_keys rows directly via SQL
// (so we don't have to wait 24h for production-default TTL to elapse), waits
// one cleanup cycle, asserts the rows are gone — proving the cleanup-repo
// fix on this branch actually works.
//
// On success the harness recreates subscription with the regular overlay so
// the stack goes back to prod defaults.

const { spawnSync } = require("child_process");

const COMPOSE_BASE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];
const COMPOSE_FAST = [...COMPOSE_BASE, "-f", "release/docker-compose.subscription-fasttime.yml"];

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) console.log(`  ✓ ${label}`);
  else { failedChecks += 1; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function compose(args) { return spawnSync("docker", ["compose", ...args], { encoding: "utf8" }); }
function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE_BASE, "exec", "-T", "subscription-db", "psql",
    "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql], { encoding: "utf8" });
  return { code: r.status || 0, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}
async function waitForBoot(args, timeoutMs = 120000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    const logs = compose([...args, "logs", "--since", "30s", "subscription"]);
    if (logs.stdout && logs.stdout.includes("Started DumbleSubscriptionApplication")) return true;
    await sleep(2000);
  }
  return false;
}

async function idempotencyCleanupDrill() {
  console.log("\n=== Idempotency cleanup deletes expired rows ===");
  const stamp = Date.now();

  const seed = psql(`
    INSERT INTO idempotency_keys (key, endpoint, user_id, state, http_status, created_at, expires_at)
    VALUES
      ('test-expired-${stamp}-a', 'POST /me/plan/upgrade', '00000099-0000-0000-0000-000000000001', 'COMPLETED', 200, now() - interval '2 hours', now() - interval '1 hour'),
      ('test-expired-${stamp}-b', 'POST /me/plan/upgrade', '00000099-0000-0000-0000-000000000002', 'COMPLETED', 200, now() - interval '2 hours', now() - interval '1 hour'),
      ('test-expired-${stamp}-c', 'POST /me/plan/upgrade', '00000099-0000-0000-0000-000000000003', 'COMPLETED', 200, now() - interval '2 hours', now() - interval '1 hour');
  `);
  assert(`seeded 3 expired idempotency rows`, seed.code === 0, seed.stderr.split("\n")[0]);

  const before = psql(`SELECT count(*) FROM idempotency_keys WHERE key LIKE 'test-expired-${stamp}-%';`);
  const beforeCount = parseInt(before.stdout || "0", 10);
  console.log(`  expired rows present pre-cleanup: ${beforeCount}`);

  console.log("  waiting ~20 s for cleanup to fire ...");
  await sleep(20000);

  const after = psql(`SELECT count(*) FROM idempotency_keys WHERE key LIKE 'test-expired-${stamp}-%';`);
  const afterCount = parseInt(after.stdout || "0", 10);
  console.log(`  expired rows present post-cleanup: ${afterCount}`);
  assert(`expired rows deleted by cleanup job`, afterCount === 0, `before=${beforeCount}, after=${afterCount}`);

  // No exception in logs (the pre-fix code threw IncorrectResultSizeDataAccessException
  // every cycle and the table grew without bound)
  const logs = compose([...COMPOSE_BASE, "logs", "--since", "30s", "subscription"]);
  const erroredAtCleanup = logs.stdout && /IncorrectResultSizeDataAccessException|deleteByExpiresAtBefore.*more than one element/.test(logs.stdout);
  assert(`no IncorrectResultSizeDataAccessException in cleanup`, !erroredAtCleanup,
    "the pre-fix derived-delete repo signature throws on every cycle where >1 row is expired");
}

(async () => {
  console.log("Recreating subscription with fast-time overlay ...");
  compose([...COMPOSE_FAST, "up", "-d", "--force-recreate", "--no-deps", "subscription"]);
  const ready = await waitForBoot(COMPOSE_BASE);
  if (!ready) {
    console.log("✗ subscription did not boot in fast-time mode");
    process.exit(2);
  }
  console.log("Subscription (fast-time) ready.");

  try {
    await idempotencyCleanupDrill();
  } catch (ex) {
    console.error("Time-jobs harness threw:", ex);
    failedChecks += 1; totalChecks += 1;
  } finally {
    console.log("\nRecreating subscription with prod-default overlay ...");
    compose([...COMPOSE_BASE, "up", "-d", "--force-recreate", "--no-deps", "subscription"]);
    await waitForBoot(COMPOSE_BASE);
    console.log("Subscription (prod-default) ready.");
  }

  console.log(`\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`);
  process.exit(failedChecks === 0 ? 0 : 1);
})();
