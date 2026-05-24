// Mints the user JWTs (signed with JWT_SECRET) and system JWTs (signed with
// SERVICE_JWT_SIGNING_KEY) the wallet collections expect, plus the wallet_url,
// gateway_url, payment_url, run_stamp, and known userIds. Emits one --env-var
// line per pair so newman run ... @args slurps the lot.
//
// Wallet's auth surface is dual:
//   - /wallet/me/** + /admin/wallet/** require a USER JWT (signed with JWT_SECRET,
//     verified by TokenExtractor against the platform-wide secret)
//   - /wallet/credit, /wallet/debit, /wallet/{userId}/summary require a SYSTEM
//     JWT (signed with SERVICE_JWT_SIGNING_KEY, aud=wallet, verified by
//     SystemTokenVerifier)
// We mint both shapes so the contract suite can cover both auth boundaries.

const crypto = require("crypto");

const systemKey = Buffer.from(
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8",
  "base64"
);
const systemWrongKey = Buffer.from(
  "AAAAwzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8",
  "base64"
);
const userKey = Buffer.from(
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo",
  "base64"
);

const b64u = (b) =>
  Buffer.from(b)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");

function jwt(claims, signingKey) {
  const hdr = b64u(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(
    crypto.createHmac("sha256", signingKey).update(`${hdr}.${payload}`).digest()
  );
  return `${hdr}.${payload}.${sig}`;
}

const now = Math.floor(Date.now() / 1000);
const exp = now + 3600;
const expPast = now - 3600;

// Stable user ids so test assertions can reference them across runs.
const ALICE = "00000099-0000-0000-0000-00000000a11c";
const BOB   = "00000099-0000-0000-0000-00000000b0b1";
const ADMIN = "00000099-0000-0000-0000-0000000ad301";

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
  // ---- user JWTs (sign w/ platform JWT_SECRET) ----
  user_jwt_alice: userJwt(ALICE, "PARTICIPANT"),
  user_jwt_bob:   userJwt(BOB,   "PARTICIPANT"),
  user_jwt_admin: userJwt(ADMIN, "ADMIN"),
  user_jwt_no_userid: jwt(
    { sub: "noid@dumble.test", iat: now, exp, roles: ["PARTICIPANT"] },
    userKey
  ),
  user_jwt_expired: userJwt(ALICE, "PARTICIPANT", { iat: now - 7200, exp: expPast }),
  user_jwt_wrong_key: jwt(
    { sub: "evil@dumble.test", userId: ALICE, userType: "PARTICIPANT",
      roles: ["PARTICIPANT"], iat: now, exp },
    systemKey  // wrong signing key for user-context
  ),

  // ---- system JWTs (sign w/ SERVICE_JWT_SIGNING_KEY) ----
  system_jwt_service: jwt(
    { iss: "subscription-service", aud: "wallet", iat: now, exp },
    systemKey
  ),
  system_jwt_admin_iss: jwt(
    { iss: "admin", aud: "wallet", iat: now, exp },
    systemKey
  ),
  // Wrong audience: token meant for Payment, presented to Wallet — must reject.
  system_jwt_payment_aud: jwt(
    { iss: "wallet-service", aud: "payment", iat: now, exp },
    systemKey
  ),
  // No audience claim at all.
  system_jwt_no_aud: jwt({ iss: "wallet-service", iat: now, exp }, systemKey),
  // Past expiry.
  system_jwt_expired: jwt(
    { iss: "wallet-service", aud: "wallet", iat: now - 7200, exp: expPast },
    systemKey
  ),
  // Signed with a key the verifier doesn't trust.
  system_jwt_wrong_key: jwt(
    { iss: "wallet-service", aud: "wallet", iat: now, exp },
    systemWrongKey
  ),
  // No exp claim — Payment had a bug where this was accepted; Wallet inherits
  // the same pattern. Security suite uses this to probe.
  system_jwt_no_exp: jwt(
    { iss: "wallet-service", aud: "wallet", iat: now },
    systemKey
  ),
  // No iss claim — should still validate; iss is informational on Wallet.
  system_jwt_no_iss: jwt({ aud: "wallet", iat: now, exp }, systemKey),
};

for (const [k, v] of Object.entries(tokens)) {
  console.log("--env-var");
  console.log(`${k}=${v}`);
}

// Wallet exposes both user-context (/wallet/me/*) and system-context
// (/wallet/credit etc.) APIs. The gateway routes the user-context endpoints;
// system-context callers (Payment, Subscription) hit Wallet's host port
// directly so the gateway's user-JWT filter doesn't reject their bearer.
console.log("--env-var");
console.log(`wallet_url=${process.env.WALLET_HOST_URL || "http://localhost:18184"}`);
console.log("--env-var");
console.log(`gateway_url=${process.env.GATEWAY_HOST_URL || "http://localhost:18080"}`);
console.log("--env-var");
console.log(`payment_url=${process.env.PAYMENT_HOST_URL || "http://localhost:18183"}`);
console.log("--env-var");
console.log(`alice_user_id=${ALICE}`);
console.log("--env-var");
console.log(`bob_user_id=${BOB}`);
console.log("--env-var");
console.log(`admin_user_id=${ADMIN}`);
console.log("--env-var");
console.log(`run_stamp=${now}`);
