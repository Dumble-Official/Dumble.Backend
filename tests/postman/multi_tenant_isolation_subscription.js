// Cross-user isolation harness for Subscription. Two users (A, B) operate
// on overlapping endpoints; B must NEVER see A's data and vice versa.
//
// Coverage:
//   I1: A creates a PRO upgrade (idempotent). B's /me/plan still reflects
//       B's state (NOT_PRO or whatever B has) — never A's upgraded state.
//   I2: B GETs A's known PRO subscription's id-shaped paths and gets 404
//       (the controllers use "hide existence" — they raise
//       ResourceNotFoundException, not AccessDeniedException — so 404 is
//       correct; 403 would leak that the resource exists for someone).
//   I3: B's /me/receipts is independent — B sees zero receipts even after
//       A has been issued a receipt.
//   I4: Idempotency keys are per-(user, key). B replaying A's
//       Idempotency-Key with a DIFFERENT body must not collide / leak A's
//       cached response. (Subscription stores user_id in idempotency_keys.)
//   I5: /me/bundle-subscriptions doesn't cross-leak rows.
//
// Run: node tests/postman/multi_tenant_isolation_subscription.js

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
const now = Math.floor(stamp / 1000);
const exp = now + 3600;
function userId(n) {
  const tailHex = (BigInt(stamp) + BigInt(n)).toString(16).padStart(12, "0").slice(-12);
  return `00000099-0000-0000-0000-${tailHex}`;
}
const A_ID = userId(1);
const B_ID = userId(2);
const tokA = jwt({ sub: `a-${stamp}@dumble.test`, userId: A_ID, displayName: "A", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp }, userKey);
const tokB = jwt({ sub: `b-${stamp}@dumble.test`, userId: B_ID, displayName: "B", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp }, userKey);

let passed = 0, failed = 0;
function check(label, ok, detail) {
  if (ok) { passed++; console.log(`  ✓ ${label}`); }
  else    { failed++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function call(method, path, token, body, extraHeaders = {}) {
  const headers = { "Content-Type": "application/json", ...extraHeaders };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  let parsed = null;
  try { parsed = await res.json(); } catch {}
  return { status: res.status, body: parsed };
}
function psql(sql) {
  const r = spawnSync("docker", ["compose", ...COMPOSE, "exec", "-T", "subscription-db", "psql",
    "-U", "postgres", "-d", "dumble_subscription", "-tA", "-c", sql], { encoding: "utf8" });
  return { code: r.status || 0, stdout: (r.stdout || "").trim(), stderr: r.stderr || "" };
}

(async () => {
  console.log(`Subscription: ${SUBSCRIPTION_URL}`);
  console.log(`User A: ${A_ID.slice(-12)}, User B: ${B_ID.slice(-12)}`);

  console.log("\n=== 1. A upgrades to PRO; B's plan view unaffected ===");
  const aUp = await call("POST", "/api/me/plan/upgrade",
    tokA, { paymentMethodToken: `tok-mt-a-${stamp}`, paymentMethodType: "CARD" },
    { "Idempotency-Key": `k-mt-a-${stamp}` });
  check(`A upgrade succeeds`, aUp.status >= 200 && aUp.status < 300, `status=${aUp.status}`);

  const aPlan = await call("GET", "/api/me/plan", tokA);
  const bPlan = await call("GET", "/api/me/plan", tokB);
  check(`A's /me/plan reflects A's state`, aPlan.status === 200, `status=${aPlan.status}`);
  check(`B's /me/plan reflects B's state`, bPlan.status === 200, `status=${bPlan.status}`);
  // A and B must have DIFFERENT plan responses because they're different
  // users at different lifecycle points (A upgraded just now).
  const aPlanCode = aPlan.body?.planCode || aPlan.body?.plan || null;
  const bPlanCode = bPlan.body?.planCode || bPlan.body?.plan || null;
  // B should not have A's PRO state.
  check(`A and B see distinct plan states (no shared user data)`,
    JSON.stringify(aPlan.body) !== JSON.stringify(bPlan.body),
    `A.plan=${aPlanCode} B.plan=${bPlanCode}`);

  console.log("\n=== 2. B requesting a random / A's resource ids returns 404 (no existence leak) ===");
  // Look up a real receipt id that belongs to A; if none yet, use a random
  // UUID — the point is: B's request must not return 403 (which would leak
  // existence). 404 is correct ("hide existence" pattern).
  let aReceiptId = null;
  const aReceipts = await call("GET", "/api/me/receipts", tokA);
  if (Array.isArray(aReceipts.body) && aReceipts.body.length > 0) {
    aReceiptId = aReceipts.body[0].id || aReceipts.body[0].receiptId || null;
  }
  const probeId = aReceiptId || crypto.randomUUID();

  const bAtA = await call("GET", `/api/me/receipts/${probeId}`, tokB);
  check(`B GET /me/receipts/{A's id} → 404 (hide existence)`,
    bAtA.status === 404, `got ${bAtA.status}; should be 404 not 403`);

  // Try a bundle-subscription id — random UUID since checkout requires a
  // real Bundle from the .NET service. Still proves the 404-vs-403 contract.
  const bsProbe = await call("GET", `/api/bundle-subscriptions/${crypto.randomUUID()}`, tokB);
  check(`B GET /bundle-subscriptions/{random} → 404`,
    bsProbe.status === 404, `got ${bsProbe.status}`);

  const cancelProbe = await call("POST", `/api/me/bundle-subscriptions/${crypto.randomUUID()}/cancel`, tokB);
  check(`B POST .../cancel on unknown id → 404`,
    cancelProbe.status === 404, `got ${cancelProbe.status}`);

  console.log("\n=== 3. B's /me/receipts list is empty (no cross-user rows) ===");
  const bReceipts = await call("GET", "/api/me/receipts", tokB);
  check(`B /me/receipts is array`, Array.isArray(bReceipts.body), `body=${typeof bReceipts.body}`);
  // B never upgraded → no receipts.
  check(`B /me/receipts contains zero of A's receipts`,
    Array.isArray(bReceipts.body) && !bReceipts.body.some((r) => r.userId === A_ID),
    `B got ${bReceipts.body?.length || 0} receipts; should be 0`);

  console.log("\n=== 4. Idempotency keys cannot be replayed across users (IDOR guard) ===");
  // idempotency_keys.key is the PK — single-column. Without an explicit
  // userId check on cached resolution, B reusing A's key would receive A's
  // cached response (cross-user IDOR). Fix on this branch:
  // IdempotencyService.resolveExisting throws 409 when the cached row's
  // userId doesn't match the caller. Assert: A gets 2xx; B gets 409 (NOT
  // a 2xx body matching A).
  const sharedKey = `k-mt-shared-${stamp}`;
  const aFirst = await call("POST", "/api/me/plan/upgrade",
    tokA, { paymentMethodToken: `tok-mt-shared-a-${stamp}`, paymentMethodType: "CARD" },
    { "Idempotency-Key": sharedKey });
  const bSame = await call("POST", "/api/me/plan/upgrade",
    tokB, { paymentMethodToken: `tok-mt-shared-b-${stamp}`, paymentMethodType: "CARD" },
    { "Idempotency-Key": sharedKey });
  check(`A's idem-key call succeeds`,
    aFirst.status >= 200 && aFirst.status < 300, `status=${aFirst.status}`);
  check(`B reusing A's idem-key → 409 (cross-user replay blocked)`,
    bSame.status === 409, `status=${bSame.status}; if 2xx and body matches A, IDOR is open`);
  // Even if status is 2xx, prove the response body wasn't A's cached row.
  if (bSame.status >= 200 && bSame.status < 300) {
    check(`B's body is NOT A's cached row (no IDOR leak)`,
      JSON.stringify(bSame.body) !== JSON.stringify(aFirst.body),
      `B got A's exact response back`);
  }

  // DB cross-check: the single key row belongs to A (the original claimant)
  const ownerRow = psql(`SELECT user_id::text FROM idempotency_keys WHERE key = '${sharedKey}';`);
  check(`idempotency_keys row for ${sharedKey.slice(-8)} belongs to A (PK is single-column)`,
    ownerRow.stdout === A_ID, `owner='${ownerRow.stdout}'`);

  console.log("\n=== 5. /me/bundle-subscriptions doesn't cross-leak ===");
  const aSubs = await call("GET", "/api/me/bundle-subscriptions", tokA);
  const bSubs = await call("GET", "/api/me/bundle-subscriptions", tokB);
  check(`A /me/bundle-subscriptions is array`, Array.isArray(aSubs.body), `body=${typeof aSubs.body}`);
  check(`B /me/bundle-subscriptions is array`, Array.isArray(bSubs.body), `body=${typeof bSubs.body}`);
  if (Array.isArray(aSubs.body) && Array.isArray(bSubs.body)) {
    const aSeesB = aSubs.body.some((s) => s.userId === B_ID);
    const bSeesA = bSubs.body.some((s) => s.userId === A_ID);
    check(`A's list contains no B subscriptions`, !aSeesB);
    check(`B's list contains no A subscriptions`, !bSeesA);
  }

  console.log("\n=== 6. /me/entitlements doesn't bleed across users ===");
  const aEnt = await call("GET", "/api/me/entitlements", tokA);
  const bEnt = await call("GET", "/api/me/entitlements", tokB);
  // Both 200 but the bodies should differ in user-scoped fields if any are
  // surfaced (some entitlement responses include userId/proSince).
  check(`A's entitlements 200`, aEnt.status === 200, `status=${aEnt.status}`);
  check(`B's entitlements 200`, bEnt.status === 200, `status=${bEnt.status}`);
  const aHasUid = aEnt.body && (aEnt.body.userId === A_ID || !aEnt.body.userId);
  const bHasUid = bEnt.body && (bEnt.body.userId === B_ID || !bEnt.body.userId);
  check(`A's entitlements userId field is A (or absent)`, aHasUid, `got ${aEnt.body?.userId}`);
  check(`B's entitlements userId field is B (or absent)`, bHasUid, `got ${bEnt.body?.userId}`);

  console.log(`\n${failed === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${passed}/${passed + failed} checks passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
