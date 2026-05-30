// Mints user JWT variants Auth tests need: well-formed, expired, wrong-key,
// alg=none, manipulated claims (role escalation), missing claims. Auth signs
// real user tokens internally so these are only used by tests that probe how
// the service REJECTS bad tokens — not for the happy-path which goes through
// /api/auth/login.

const crypto = require("crypto");

const USER_KEY_B64 =
  process.env.JWT_SECRET ||
  "K0Q6NCGDFncmftENnNLP9r9lzaJbvOFCnznXqP0PrI4ag5D8tl5kHpFfoM7tDNjo";
const userKey = Buffer.from(USER_KEY_B64, "base64");
const wrongKey = Buffer.from(
  "AAAAwzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8",
  "base64"
);

const b64u = (b) =>
  Buffer.from(b)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");

function jwt(claims, key, header) {
  const hdr = b64u(JSON.stringify(header || { alg: "HS256", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  const sig = b64u(
    crypto.createHmac("sha256", key).update(`${hdr}.${payload}`).digest()
  );
  return `${hdr}.${payload}.${sig}`;
}

function jwtUnsigned(claims) {
  // alg=none — no signature; legitimate verifiers MUST reject this.
  const hdr = b64u(JSON.stringify({ alg: "none", typ: "JWT" }));
  const payload = b64u(JSON.stringify(claims));
  return `${hdr}.${payload}.`;
}

const now = Math.floor(Date.now() / 1000);
const exp = now + 3600;
const expPast = now - 3600;

module.exports = {
  userKey,
  wrongKey,
  jwt,
  jwtUnsigned,
  b64u,
  // Returns a real-shape user JWT for a given email.
  mintAccess(email, opts = {}) {
    const claims = {
      sub: email,
      iss: "dumble-auth",
      aud: "dumble-app",
      iat: opts.iat || now,
      exp: opts.exp || exp,
      roles: opts.roles || ["ROLE_PARTICIPANT"],
      userId: opts.userId || crypto.randomUUID(),
      userType: opts.userType || "PARTICIPANT",
    };
    return jwt(claims, opts.key || userKey);
  },
  expired(email) {
    return module.exports.mintAccess(email, { iat: now - 7200, exp: expPast });
  },
  wrongKeyToken(email) {
    return module.exports.mintAccess(email, { key: wrongKey });
  },
  algNone(email, roles = ["ROLE_ADMIN"]) {
    return jwtUnsigned({
      sub: email, iss: "dumble-auth", aud: "dumble-app",
      iat: now, exp, roles, userType: "ADMIN",
    });
  },
  // Privilege-escalation attempt: legitimately signed token but with ADMIN
  // role claim. Test verifies the service derives authorities from the DB row
  // for that user, not from the token claim.
  escalated(email) {
    return module.exports.mintAccess(email, {
      roles: ["ROLE_ADMIN"], userType: "ADMIN",
    });
  },
};
