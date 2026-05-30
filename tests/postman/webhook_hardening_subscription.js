// Webhook hardening drill for Subscription.
//
// /webhooks/system/** is whitelisted in SecurityConfig (no user JWT
// required) but each request must carry:
//   - Authorization: Bearer <system-jwt>  signed with SERVICE_JWT_SIGNING_KEY,
//                                          aud="subscription", non-null exp
//   - X-Webhook-Event-Id header           caller-supplied dedup key
//   - JSON body with required fields      e.g. sellerId for seller-frozen
//
// We probe every failure mode plus the dedup invariant. The aud + exp
// enforcement is the fix shipped on this branch — verifies the
// SystemTokenVerifier change. The same eventId fired twice must dedup
// (idempotent webhook contract per Decision 8.4).
//
// Run: node tests/postman/webhook_hardening_subscription.js

const crypto = require("crypto");
const { spawnSync } = require("child_process");

const SUBSCRIPTION_URL = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const COMPOSE = ["-f", "release/docker-compose.yml", "-f", "release/docker-compose.override.yml"];

const SYSTEM_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const SYSTEM_WRONG_KEY_B64 = "AAAAwzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const systemKey = Buffer.from(SYSTEM_KEY_B64, "base64");
const systemWrongKey = Buffer.from(SYSTEM_WRONG_KEY_B64, "base64");
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

const sysOk         = jwt({ iss: "auth-service",  aud: "subscription", iat: now, exp }, systemKey);
const sysWrongAud   = jwt({ iss: "payment-svc",   aud: "payment",      iat: now, exp }, systemKey);
const sysNoAud      = jwt({ iss: "auth-service",                       iat: now, exp }, systemKey);
const sysExpired    = jwt({ iss: "auth-service",  aud: "subscription", iat: now - 7200, exp: now - 3600 }, systemKey);
const sysNoExp      = jwt({ iss: "auth-service",  aud: "subscription", iat: now }, systemKey);
const sysWrongKey   = jwt({ iss: "auth-service",  aud: "subscription", iat: now, exp }, systemWrongKey);
const userTok       = jwt({
  sub: `webhook-probe-${stamp}@dumble.test`,
  userId: `00000099-0000-0000-0000-${stamp.toString(16).padStart(12, "0").slice(-12)}`,
  displayName: "Probe", userType: "PARTICIPANT", roles: ["PARTICIPANT"], iat: now, exp,
}, userKey);

let passed = 0, failed = 0;
function check(label, ok, detail) {
  if (ok) { passed++; console.log(`  ✓ ${label}`); }
  else    { failed++; console.log(`  ✗ ${label}${detail ? "  — " + detail : ""}`); }
}

async function post(path, opts = {}) {
  const { token, eventId, body, omitContentType } = opts;
  const headers = {};
  if (!omitContentType) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (eventId !== undefined) headers["X-Webhook-Event-Id"] = eventId;
  const res = await fetch(`${SUBSCRIPTION_URL}${path}`, {
    method: "POST", headers,
    body: typeof body === "string" ? body : body ? JSON.stringify(body) : undefined,
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

const sellerId = (() => {
  const tailHex = (BigInt(stamp) + 555n).toString(16).padStart(12, "0").slice(-12);
  return `00000099-0000-0000-0000-${tailHex}`;
})();

(async () => {
  console.log(`Subscription: ${SUBSCRIPTION_URL}`);
  console.log(`Probe sellerId: ${sellerId.slice(-12)}`);

  const PATH = "/api/webhooks/system/seller-frozen";

  console.log("\n=== 1. Auth required: no token / user token / wrong key ===");
  const noAuth = await post(PATH, { eventId: `e-noauth-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`no Authorization → 401`, noAuth.status === 401, `got ${noAuth.status}`);

  const usrAuth = await post(PATH, { token: userTok, eventId: `e-userjwt-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`user JWT (wrong issuer/aud) → 401`, usrAuth.status === 401, `got ${usrAuth.status}`);

  const badKey = await post(PATH, { token: sysWrongKey, eventId: `e-badkey-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`system JWT signed with wrong key → 401`, badKey.status === 401, `got ${badKey.status}`);

  console.log("\n=== 2. aud enforcement (fix on this branch) ===");
  const wrongAud = await post(PATH, { token: sysWrongAud, eventId: `e-wrongaud-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`system JWT with aud=payment → 401`, wrongAud.status === 401, `got ${wrongAud.status}; aud check missing if 2xx`);
  const noAud = await post(PATH, { token: sysNoAud, eventId: `e-noaud-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`system JWT with no aud → 401`, noAud.status === 401, `got ${noAud.status}; aud check missing if 2xx`);

  console.log("\n=== 3. exp enforcement ===");
  const expired = await post(PATH, { token: sysExpired, eventId: `e-expired-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`expired system JWT → 401`, expired.status === 401, `got ${expired.status}`);
  const noExp = await post(PATH, { token: sysNoExp, eventId: `e-noexp-${stamp}`, body: { sellerId, reason: "probe" } });
  check(`system JWT with no exp → 401 (this branch enforces it)`, noExp.status === 401, `got ${noExp.status}`);

  console.log("\n=== 4. Required headers / body fields ===");
  const noEventId = await post(PATH, { token: sysOk, body: { sellerId, reason: "probe" } });
  check(`missing X-Webhook-Event-Id → 400 (not 5xx)`, noEventId.status === 400, `got ${noEventId.status}`);

  const noSeller = await post(PATH, { token: sysOk, eventId: `e-nosel-${stamp}`, body: { reason: "probe" } });
  check(`missing sellerId → 400`, noSeller.status === 400, `got ${noSeller.status}`);

  const badSeller = await post(PATH, { token: sysOk, eventId: `e-badsel-${stamp}`, body: { sellerId: "not-a-uuid", reason: "probe" } });
  check(`malformed sellerId → 400`, badSeller.status === 400, `got ${badSeller.status}`);

  const malformedJson = await post(PATH, { token: sysOk, eventId: `e-badjson-${stamp}`, body: "{ not-json" });
  check(`malformed JSON body → 400`, malformedJson.status === 400, `got ${malformedJson.status}`);

  console.log("\n=== 5. Idempotent dedup on X-Webhook-Event-Id ===");
  const eventId = `e-dedup-${stamp}`;
  const first  = await post(PATH, { token: sysOk, eventId, body: { sellerId, reason: "probe-dedup" } });
  const second = await post(PATH, { token: sysOk, eventId, body: { sellerId, reason: "probe-dedup" } });
  const third  = await post(PATH, { token: sysOk, eventId, body: { sellerId, reason: "probe-dedup" } });
  check(`first call → 202 Accepted`,  first.status === 202,  `got ${first.status}`);
  check(`second call (same eventId) → 202`, second.status === 202, `got ${second.status}`);
  check(`third call (same eventId) → 202`, third.status === 202, `got ${third.status}`);

  // DB-level dedup: webhook_events_inbound table should hold exactly ONE
  // row for this eventId (the saga commits once on the winner, never on
  // replays).
  const dedupRows = psql(`SELECT count(*) FROM webhook_events_inbound WHERE event_id = '${eventId}';`);
  check(`webhook_events has exactly 1 row for the replayed eventId`,
    parseInt(dedupRows.stdout || "0", 10) === 1, `count=${dedupRows.stdout}`);

  // And: the seller_lifecycle row for sellerId should have been mutated
  // EXACTLY once (not three times) — i.e. no double-effect. The freeze
  // service is also internally idempotent, but the dedup gate is the
  // first line of defence.
  await new Promise((r) => setTimeout(r, 500));
  const lcRows = psql(`SELECT count(*) FROM seller_lifecycle WHERE seller_id = '${sellerId}';`);
  check(`seller_lifecycle row exists for the probed seller (1 row)`,
    parseInt(lcRows.stdout || "0", 10) === 1, `count=${lcRows.stdout}`);

  console.log("\n=== 6. Other system-webhook endpoints reject the same way ===");
  for (const endpoint of ["/api/webhooks/system/seller-unfrozen", "/api/webhooks/system/seller-banned"]) {
    const r = await post(endpoint, { eventId: `e-cross-${endpoint.slice(-10)}-${stamp}`, body: { sellerId, reason: "probe" } });
    check(`${endpoint} no auth → 401`, r.status === 401, `got ${r.status}`);
  }

  console.log(`\n${failed === 0 ? "✓ ALL GREEN" : "✗ FAILURES"}: ${passed}/${passed + failed} checks passed`);
  process.exit(failed === 0 ? 0 : 1);
})();
