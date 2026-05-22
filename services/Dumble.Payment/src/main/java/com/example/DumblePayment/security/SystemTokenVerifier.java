package com.example.DumblePayment.security;

import com.example.DumblePayment.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;

/**
 * Decision 9.3 — every Payment endpoint called by other services
 * (Subscription, Wallet) must carry a system-context JWT with
 * {@code aud=payment}. Validated against the configured signing key; the
 * issuer claim records who initiated the money movement for the audit log.
 *
 * v1 keeps the contract simple: same signing key across all services. The
 * design leaves the door open to a per-service key registry — when that
 * lands, this class swaps for one that selects the verification key by
 * {@code iss}.
 */
@Component
public class SystemTokenVerifier {

    private static final String EXPECTED_AUDIENCE = "payment";

    private final SecretKey signingKey;

    public SystemTokenVerifier(@Value("${service-jwt.signing-key}") String secret) {
        this.signingKey = SystemSigningKey.resolve(secret);
    }

    public Claims verify(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedAccessException("Missing system bearer token");
        }
        String jwt = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            // jjwt validates exp if present (throws ExpiredJwtException) but
            // accepts tokens that omit exp entirely. A no-exp token never goes
            // stale, so a leaked service token would be valid forever —
            // refuse it explicitly. (Caught by the QA security probe.)
            if (claims.getExpiration() == null) {
                throw new UnauthorizedAccessException("System token missing exp claim");
            }
            // jjwt 0.12 returns Set<String> for aud; older versions returned
            // a scalar. Handle both via Collection membership, NEVER via a
            // substring match (otherwise "paymentservice" would slip past).
            if (!audienceMatches(claims.getAudience())) {
                throw new UnauthorizedAccessException("System token aud does not include 'payment'");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedAccessException("Invalid or expired system token");
        }
    }

    private static boolean audienceMatches(Object audience) {
        if (audience == null) return false;
        if (audience instanceof Collection<?> coll) {
            for (Object o : coll) {
                if (EXPECTED_AUDIENCE.equals(String.valueOf(o))) return true;
            }
            return false;
        }
        return EXPECTED_AUDIENCE.equals(audience.toString());
    }
}
