// Mints the 6 system JWTs the payment collection expects, plus the
// payment_url + run_stamp + paymob_hmac vars. Emits one `--env-var` line per
// pair so the output can be slurped straight into `newman run ... @args`.
const crypto = require("crypto");

const key = Buffer.from(
  "IxiswzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8",
  "base64"
);
const wrongKey = Buffer.from(
  "AAAAwzRP3n5ze4ci3+wJLEKZgOfDhUfh7qugd6a6Eoaw4yhXiFKbbzmZZ9WOPeQ8",
  "base64"
);

const b64u = (b) =>
  Buffer.from(b).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

function jwt(claims, signingKey = key) {
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

const tokens = {
  system_jwt_service: jwt({ iss: "payment-service", aud: "payment", iat: now, exp }),
  system_jwt_admin: jwt({ iss: "admin", aud: "payment", iat: now, exp }),
  system_jwt_expired: jwt({ iss: "payment-service", aud: "payment", iat: now - 7200, exp: expPast }),
  system_jwt_wrong_key: jwt({ iss: "payment-service", aud: "payment", iat: now, exp }, wrongKey),
  system_jwt_user: jwt({ iss: "user", aud: "payment", iat: now, exp }),
  system_jwt_no_iss: jwt({ aud: "payment", iat: now, exp }),
};

for (const [k, v] of Object.entries(tokens)) {
  console.log("--env-var");
  console.log(`${k}=${v}`);
}
console.log("--env-var");
console.log("paymob_hmac=dev-stub-ok");
// Payment exposes a system-JWT API. The gateway's user-JWT filter (signed with
// JWT_SECRET) rejects system tokens (signed with SERVICE_JWT_SIGNING_KEY), so
// the collection hits Payment directly on its host port — 18183 by default,
// or whatever PAYMENT_HOST_URL was exported into the shell.
const paymentUrl = process.env.PAYMENT_HOST_URL || "http://localhost:18183";
console.log("--env-var");
console.log(`payment_url=${paymentUrl}`);
console.log("--env-var");
console.log(`run_stamp=${now}`);
