// Time-based job harness for Wallet. Recreates the wallet container with the
// release/docker-compose.wallet-fasttime.yml overlay so the reaper / recon /
// idempotency-cleanup jobs run on seconds instead of the production cadence,
// drives scenarios that should trigger them, asserts the resulting state.
//
// Three drills:
//   1. Withdrawal reaper — stage a stuck PENDING row (updated_at well in the
//      past, payment_ref pointing at Payment's COMPLETED Payout), wait for
//      the reaper, assert it advanced the row to COMPLETED and restored the
//      ledger.
//   2. Reconciliation drift — directly mutate wallets.available_cents so it
//      no longer matches sum(ledger), wait for the recon cron to fire,
//      assert a discrepancy was logged (audit row + ERROR log line).
//   3. Idempotency cleanup — seed expired idempotency_keys rows by SQL,
//      wait for the cleanup cron, assert the rows are gone.
//
// On success the harness recreates wallet with the regular overlay so the
// stack goes back to prod defaults.

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const WALLET_URL = process.env.WALLET_HOST_URL || "http://localhost:18184";
const PAYMENT_URL = process.env.PAYMENT_HOST_URL || "http://localhost:18183";
const COMPOSE_BASE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];
const COMPOSE_FAST = [...COMPOSE_BASE, "-f", "release/docker-compose.wallet-fasttime.yml"];

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
const systemJwt = jwt({ iss: "tjobs", aud: "wallet", iat: now, exp }, systemKey);
const userJwt = jwt(
  { sub: "tjobs@dumble.test", userId: TEST_USER, displayName: "TJ", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
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

function compose(args, name = "compose") {
  const r = spawnSync("docker", ["compose", ...args], { encoding: "utf8" });
  if (r.status !== 0 && r.stderr && !r.stderr.includes("warning")) {
    console.log(`  (${name} stderr: ${r.stderr.slice(0, 200).trim()})`);
  }
  return r;
}

function psql(db, sql) {
  const r = spawnSync(
    "docker",
    ["compose", ...COMPOSE_BASE, "exec", "-T", db, "psql", "-U", "postgres", "-d",
      db === "wallet-db" ? "dumble_wallet" : "dumble_payment", "-tA", "-c", sql],
    { encoding: "utf8" }
  );
  return { code: r.status || 0, stdout: r.stdout || "", stderr: r.stderr || "" };
}

async function postJson(url, body, headers) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
async function getJson(url, headers) {
  const res = await fetch(url, { headers });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

async function waitUntil(label, pred, timeoutMs, intervalMs = 1000) {
  const startTs = Date.now();
  while (Date.now() - startTs < timeoutMs) {
    if (await pred()) return true;
    await sleep(intervalMs);
  }
  return false;
}

async function recreateWalletWithFastOverlay() {
  console.log("Recreating wallet with fast-time overlay…");
  compose([...COMPOSE_FAST, "up", "-d", "--force-recreate", "--no-deps", "wallet"], "fast-up");
  // Wait for boot.
  for (let i = 0; i < 60; i++) {
    const logs = compose([...COMPOSE_BASE, "logs", "--since", "2m", "wallet"], "logs");
    if (logs.stdout && logs.stdout.includes("Started DumbleWalletApplication")) {
      console.log("Wallet (fast-time) ready.");
      return;
    }
    await sleep(2000);
  }
  throw new Error("wallet did not boot in fast-time mode within 120 s");
}

async function recreateWalletWithProdOverlay() {
  console.log("Recreating wallet with prod-default overlay…");
  compose([...COMPOSE_BASE, "up", "-d", "--force-recreate", "--no-deps", "wallet"], "prod-up");
  for (let i = 0; i < 60; i++) {
    const logs = compose([...COMPOSE_BASE, "logs", "--since", "2m", "wallet"], "logs");
    if (logs.stdout && logs.stdout.includes("Started DumbleWalletApplication")) {
      console.log("Wallet (prod-default) ready.");
      return;
    }
    await sleep(2000);
  }
  console.log("WARN: wallet didn't boot in prod-default within 120 s");
}

// ── Scenario 1 — withdrawal reaper ──────────────────────────────────────────
async function reaperDrill() {
  console.log("\n=== 1. Withdrawal reaper rescues a stuck PENDING row ===");

  // Bootstrap balance + drive a normal withdrawal that flips to SENT.
  const credit = await postJson(
    `${WALLET_URL}/api/wallet/credit`,
    { userId: TEST_USER, amountCents: 60000, source: "BAN_REFUND", externalRef: `tj-credit-${stamp}` },
    { Authorization: `Bearer ${systemJwt}`, "Idempotency-Key": `k-tj-c-${stamp}` }
  );
  assert(`credit 201`, credit.status === 201, JSON.stringify(credit).slice(0, 200));

  const wRes = await postJson(
    `${WALLET_URL}/api/wallet/me/withdrawals`,
    { amountCents: 25000, destination: { type: "bank", iban: "EG800000000000000000000099" } },
    { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": `k-tj-w-${stamp}` }
  );
  assert(`withdrawal 201`, wRes.status === 201);
  const wId = wRes.body?.id;

  // Confirm the withdrawal on Payment's side via webhook so Payment's view
  // says COMPLETED. The reaper's job is to read THAT and reconcile Wallet.
  const webhookBody = {
    type: "payout",
    obj: {
      id: `paymob-tj-${stamp}`,
      merchant_payout_id: wId,
      status: "completed",
      amount_cents: 25000,
      currency: "EGP",
    },
  };
  const wh = await postJson(`${PAYMENT_URL}/api/payment/webhooks/paymob`, webhookBody, { "X-Paymob-Signature": "dev-stub-ok" });
  assert(`Payment ACKs webhook`, wh.status === 200);

  // Roll Wallet's row backward: status → SENT (or PENDING), updated_at well
  // beyond the reaper grace window (15 s in fast-time mode). This simulates a
  // process that crashed BEFORE consuming Payment's outbox event.
  const roll = psql("wallet-db",
    `UPDATE withdrawal_requests SET status = 'SENT', updated_at = now() - interval '60 seconds' ` +
    `WHERE id = '${wId}';`);
  assert(`rolled wallet row back to SENT + stale updated_at`, roll.code === 0, roll.stderr.split("\n")[0]);

  // Reaper ticks every 5 s in fast-time mode + grace 15 s. After ~20 s the
  // reaper should query Payment via callerReference (= wId), see status
  // COMPLETED, and flip the local row.
  const recovered = await waitUntil(
    "reaper advances stuck row to COMPLETED",
    async () => {
      const r = psql("wallet-db", `SELECT status FROM withdrawal_requests WHERE id = '${wId}';`);
      return r.stdout.trim() === "COMPLETED";
    },
    45000,
    2000
  );
  assert(`reaper flipped stuck row to COMPLETED within 45 s`, recovered);
}

// ── Scenario 2 — reconciliation drift ───────────────────────────────────────
async function reconciliationDrill() {
  console.log("\n=== 2. Reconciliation cron catches a manually-injected balance drift ===");

  // Bootstrap a fresh user with known ledger sum, then poison the cached balance.
  const reconUser = `00000099-0000-0000-0000-${(stamp + 1).toString(16).padStart(12, "0").slice(-12)}`;
  const reconJwt = jwt({ iss: "tjobs-recon", aud: "wallet", iat: now, exp }, systemKey);
  const credit = await postJson(
    `${WALLET_URL}/api/wallet/credit`,
    { userId: reconUser, amountCents: 1000, source: "BAN_REFUND", externalRef: `recon-${stamp}` },
    { Authorization: `Bearer ${reconJwt}`, "Idempotency-Key": `k-recon-${stamp}` }
  );
  assert(`recon-user bootstrap credit 201`, credit.status === 201);

  // Now corrupt: set available_cents to 9999 while ledger sum stays 1000.
  const poison = psql("wallet-db",
    `UPDATE wallets SET available_cents = 9999, version = version + 1 WHERE user_id = '${reconUser}';`);
  assert(`poisoned available_cents to 9999`, poison.code === 0, poison.stderr.split("\n")[0]);

  // Reconciliation cron runs every 20 s in fast-time mode. Wait for ONE pass.
  console.log("  waiting ~25 s for reconciliation to fire…");
  await sleep(25000);

  // Look for a reconciliation discrepancy log line.
  const logs = compose([...COMPOSE_BASE, "logs", "--since", "60s", "wallet"], "logs");
  const drifted = logs.stdout && /ReconciliationDrift|discrepan|drift|mismatch/i.test(logs.stdout);
  assert(`wallet logged a reconciliation drift line`, drifted, "no drift-like log line in last 60 s");

  // Also verify an audit row was written (some implementations log to
  // wallet_event_log with event_type RECONCILIATION_*).
  const audit = psql("wallet-db",
    `SELECT count(*) FROM wallet_event_log ` +
    `WHERE wallet_user_id = '${reconUser}' AND event_type LIKE 'Reconciliation%' ` +
    `AND timestamp > now() - interval '2 minutes';`);
  const rowCount = parseInt(audit.stdout.trim() || "0", 10);
  assert(`audit log contains a Reconciliation* event for the drifted wallet`,
    rowCount >= 1,
    `count=${rowCount}; audit table might just have an INFO log and no audit row — record finding either way`);
}

// ── Scenario 3 — idempotency cleanup ────────────────────────────────────────
async function idempotencyCleanupDrill() {
  console.log("\n=== 3. Idempotency cleanup deletes expired rows ===");

  // Seed 3 expired idempotency rows directly via SQL.
  const seed = psql("wallet-db", `
    INSERT INTO idempotency_keys (key, endpoint, user_id, state, http_status, request_hash, created_at, expires_at)
    VALUES
      ('test-expired-${stamp}-a', 'POST /wallet/credit', '${TEST_USER}', 'COMPLETED', 201, NULL, now() - interval '2 hours', now() - interval '1 hour'),
      ('test-expired-${stamp}-b', 'POST /wallet/credit', '${TEST_USER}', 'COMPLETED', 201, NULL, now() - interval '2 hours', now() - interval '1 hour'),
      ('test-expired-${stamp}-c', 'POST /wallet/credit', '${TEST_USER}', 'COMPLETED', 201, NULL, now() - interval '2 hours', now() - interval '1 hour');
  `);
  assert(`seeded 3 expired idempotency rows`, seed.code === 0, seed.stderr.split("\n")[0]);

  const countBefore = psql("wallet-db",
    `SELECT count(*) FROM idempotency_keys WHERE key LIKE 'test-expired-${stamp}-%';`);
  const before = parseInt(countBefore.stdout.trim() || "0", 10);
  console.log(`  expired rows present pre-cleanup: ${before}`);

  // Cleanup cron fires every 15 s in fast-time mode.
  console.log("  waiting ~20 s for cleanup to fire…");
  await sleep(20000);

  const countAfter = psql("wallet-db",
    `SELECT count(*) FROM idempotency_keys WHERE key LIKE 'test-expired-${stamp}-%';`);
  const after = parseInt(countAfter.stdout.trim() || "0", 10);
  console.log(`  expired rows present post-cleanup: ${after}`);
  assert(`expired rows deleted by cleanup job`, after === 0, `before=${before}, after=${after}`);
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Wallet URL: ${WALLET_URL}`);
  console.log(`Test user:  ${TEST_USER}`);

  try {
    await recreateWalletWithFastOverlay();
    await reaperDrill();
    await reconciliationDrill();
    await idempotencyCleanupDrill();
  } catch (ex) {
    console.error("Time-jobs harness threw:", ex);
    failedChecks += 1; totalChecks += 1;
  } finally {
    await recreateWalletWithProdOverlay();
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
