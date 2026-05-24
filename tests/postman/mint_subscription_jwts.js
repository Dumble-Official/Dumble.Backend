// Mints the JWTs the subscription collections expect, plus the
// subscription_url / gateway_url / payment_url / wallet_url / run_stamp
// + known user ids. Emits one --env-var line per pair so the output goes
// straight into newman run ... @args.
//
// Subscription's auth surface:
//   - Most /api/** endpoints require a USER JWT (signed with JWT_SECRET,
//     verified by JwtAuthenticationFilter)
//   - /webhooks/system/** require a SYSTEM JWT (signed with
//     SERVICE_JWT_SIGNING_KEY, aud=subscription, verified by
//     SystemTokenVerifier — the fix in this branch added aud + exp
//     enforcement)
//   - /admin/** also requires a USER JWT but with ROLE_ADMIN
//   - /plans is permitAll (no auth)
// We mint both shapes plus all the failure-mode variants so the security
// suite can probe the full boundary.

const crypto = require("crypto");

const SYSTEM_KEY_B64 =
  process.env.SIGNING_KEY ||
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const SYSTEM_WRONG_KEY_B64 =
  "AAAAwzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8";
const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";

const systemKey = Buffer.from(SYSTEM_KEY_B64, "base64");
const systemWrongKey = Buffer.from(SYSTEM_WRONG_KEY_B64, "base64");
const userKey = Buffer.from(USER_KEY_B64, "base64");

const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

function jwt(claims, signingKey) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest());
  return `${hdr}.${payload}.${sig}`;
}

const now = Math.floor(Date.now() / 1000);
const exp = now + 3600;
const expPast = now - 3600;

// Stable user ids so assertions referencing them stay deterministic.
const PARTICIPANT = "00000099-0000-0000-0000-00000000pa01".replace(/p/g, "f");
const SELLER      = "00000099-0000-0000-0000-00000000se01".replace(/[se]/g, "e");
const ADMIN       = "00000099-0000-0000-0000-0000000ad301";
const GYM         = "00000099-0000-0000-0000-00000000gy01".replace(/[gy]/g, "a");

function userJwt(userId, role, extra = {}) {
  return jwt(
    {
      sub: `${role.toLowerCase()}-${userId.slice(-4)}@dumble.test`,
      userId,
      displayName: `${role.charAt(0)}${role.slice(1).toLowerCase()} ${userId.slice(-4)}`,
      userType: role,
      roles: [role],
      iat: now,
      exp,
      ...extra,
    },
    userKey
  );
}

const tokens = {
  // user JWTs (sign w/ platform JWT_SECRET)
  user_jwt_participant: userJwt(PARTICIPANT, "PARTICIPANT"),
  user_jwt_seller:      userJwt(SELLER,      "TRAINER"),
  user_jwt_admin:       userJwt(ADMIN,       "ADMIN"),
  user_jwt_gym:         userJwt(GYM,         "GYM_OWNER"),
  user_jwt_expired:     userJwt(PARTICIPANT, "PARTICIPANT", { iat: now - 7200, exp: expPast }),
  user_jwt_wrong_key:   jwt(
    { sub: "evil@dumble.test", userId: PARTICIPANT, userType: "PARTICIPANT",
      roles: ["PARTICIPANT"], iat: now, exp },
    systemKey
  ),

  // system JWTs (sign w/ SERVICE_JWT_SIGNING_KEY)
  system_jwt_service:        jwt({ iss: "auth-service",   aud: "subscription", iat: now, exp },           systemKey),
  system_jwt_admin_iss:      jwt({ iss: "admin",          aud: "subscription", iat: now, exp },           systemKey),
  system_jwt_payment_aud:    jwt({ iss: "payment-service",aud: "payment",      iat: now, exp },           systemKey),
  system_jwt_no_aud:         jwt({ iss: "auth-service",                         iat: now, exp },          systemKey),
  system_jwt_expired:        jwt({ iss: "auth-service",   aud: "subscription", iat: now - 7200, exp: expPast }, systemKey),
  system_jwt_wrong_key:      jwt({ iss: "auth-service",   aud: "subscription", iat: now, exp },           systemWrongKey),
  system_jwt_no_exp:         jwt({ iss: "auth-service",   aud: "subscription", iat: now },                 systemKey),
};

for (const [k, v] of Object.entries(tokens)) {
  console.log("--env-var");
  console.log(`${k}=${v}`);
}

const sub = process.env.SUBSCRIPTION_HOST_URL || "http://localhost:18182";
const gw  = process.env.GATEWAY_HOST_URL      || "http://localhost:18080";
const pay = process.env.PAYMENT_HOST_URL      || "http://localhost:18183";
const wal = process.env.WALLET_HOST_URL       || "http://localhost:18184";

console.log("--env-var"); console.log(`subscription_url=${sub}`);
console.log("--env-var"); console.log(`gateway_url=${gw}`);
console.log("--env-var"); console.log(`payment_url=${pay}`);
console.log("--env-var"); console.log(`wallet_url=${wal}`);
console.log("--env-var"); console.log(`participant_user_id=${PARTICIPANT}`);
console.log("--env-var"); console.log(`seller_user_id=${SELLER}`);
console.log("--env-var"); console.log(`admin_user_id=${ADMIN}`);
console.log("--env-var"); console.log(`gym_user_id=${GYM}`);
console.log("--env-var"); console.log(`run_stamp=${now}`);
