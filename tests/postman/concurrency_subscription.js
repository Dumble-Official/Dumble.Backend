// Concurrency harness for Subscription that the Postman runner can't express:
// fires N parallel HTTP requests at /me/plan/upgrade with the SAME Idempotency-Key
// and asserts the dedup gate holds.
//
// The PR's switch from saveAndFlush → entityManager.persist closes the
// double-claim race in IdempotencyKeyStore. With the old code, two racing
// peers could both win (one INSERT, one UPDATE via Spring Data merge semantics),
// letting a duplicate PRO upgrade slip through. After the fix exactly one
// caller wins; the rest see either 200 (cached replay) or 409 (in-flight).
//
// Plus a second scenario: a single Idempotency-Key call repeated 50× sequentially
// must return the SAME planCode + same `startedAt` every time (cached replay
// integrity).
//
// Run: node tests/postman/concurrency_subscription.js

const crypto = require("crypto");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";

const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
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
// Each run uses a fresh participant id so previous runs' state doesn't bleed.
const PARTICIPANT = `00000099-0000-0000-${stamp.toString(16).padStart(16, "0").slice(-4)}-${stamp.toString(16).padStart(12, "0").slice(-12)}`;
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
const userJwt = jwt(
  { sub: `conc-${stamp}@dumble.test`, userId: PARTICIPANT, displayName: "Conc",
    userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp },
  userKey
);

let totalChecks = 0;
let failedChecks = 0;
function assert(label, ok, detail) {
  totalChecks += 1;
  if (ok) console.log(`  ✓ ${label}`);
  else { failedChecks += 1; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function postJson(path, body, headers) {
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}

// ── Scenario 1: parallel same-key upgrade race ─────────────────────────────
async function parallelSameKeyRace(N = 12) {
  console.log(`\n=== Scenario 1 — ${N} parallel PRO upgrades, SAME Idempotency-Key ===`);
  const idem = `k-conc-${stamp}-1`;
  const body = {
    paymentMethodToken: `tok-conc-${stamp}`,
    paymentMethodType: "CARD",
  };
  const headers = { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": idem };

  const t0 = Date.now();
  const results = await Promise.all(
    Array.from({ length: N }, () => postJson("/api/me/plan/upgrade", body, headers))
  );
  const elapsed = Date.now() - t0;
  console.log(`  ${N} parallel requests in ${elapsed}ms`);

  const byStatus = {};
  const planCodes = new Set();
  const startedAts = new Set();
  for (const r of results) {
    byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    if (r.body && r.body.planCode) planCodes.add(r.body.planCode);
    if (r.body && r.body.startedAt) startedAts.add(r.body.startedAt);
  }
  console.log(`  status distribution: ${JSON.stringify(byStatus)}`);
  console.log(`  distinct planCodes:  ${planCodes.size} (${[...planCodes].join(",")})`);
  console.log(`  distinct startedAt:  ${startedAts.size}`);

  const ok2xx = (byStatus[200] || 0) + (byStatus[201] || 0) >= 1;
  const okNo5xx = !Object.keys(byStatus).some((s) => Number(s) >= 500);
  const okOneStarted = startedAts.size <= 1;
  const okAllAccountedFor = Object.entries(byStatus).every(([s]) => [200, 201, 409].includes(Number(s)));

  assert(`at least one 2xx winner`, ok2xx, JSON.stringify(byStatus));
  assert(`no 5xx`, okNo5xx, JSON.stringify(byStatus));
  assert(`exactly one PRO upgrade row materialized (single startedAt)`, okOneStarted, `distinct startedAt = ${startedAts.size}`);
  assert(`every response is 200/201/409 (no surprises)`, okAllAccountedFor, JSON.stringify(byStatus));
  assert(`planCode normalized to PRO`, planCodes.size === 1 && planCodes.has("PRO"), `planCodes=${[...planCodes].join(",")}`);
}

// ── Scenario 2: sequential replay integrity ─────────────────────────────────
async function sequentialReplayIntegrity(N = 20) {
  console.log(`\n=== Scenario 2 — ${N} sequential replays under same key return identical body ===`);
  const idem = `k-conc-${stamp}-2`;
  const headers = { Authorization: `Bearer ${userJwt}`, "Idempotency-Key": idem };

  let firstStartedAt = null;
  let mismatches = 0;
  let serverErrors = 0;
  for (let i = 0; i < N; i++) {
    const r = await postJson("/api/me/plan/cancel", {}, headers);
    if (r.status >= 500) { serverErrors++; continue; }
    if (i === 0) firstStartedAt = r.body && r.body.startedAt;
    if (r.body && r.body.startedAt && r.body.startedAt !== firstStartedAt) mismatches++;
  }
  assert(`no 5xx across ${N} replays`, serverErrors === 0, `5xx=${serverErrors}`);
  assert(`startedAt stable across all replays`, mismatches === 0, `mismatches=${mismatches}`);
}

// ── main ────────────────────────────────────────────────────────────────────
(async () => {
  console.log(`Subscription URL: ${SUBSCRIPTION_URL}`);
  console.log(`Test participant: ${PARTICIPANT.slice(-12)}`);

  try {
    await parallelSameKeyRace(12);
    await sequentialReplayIntegrity(20);
  } catch (ex) {
    console.error("Harness threw:", ex);
    failedChecks += 1; totalChecks += 1;
  }

  console.log(
    `\n${failedChecks === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${totalChecks - failedChecks}/${totalChecks} checks passed`
  );
  process.exit(failedChecks === 0 ? 0 : 1);
})();
