package com.example.DumbleSubscription.security;

import com.example.DumbleSubscription.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;

/**
 * Verifies system JWTs (Class B inter-service auth, Decision 8.4) used on
 * inbound webhook + system-context endpoints. Mirror of {@link SystemTokenSigner}
 * but for the receiving side.
 *
 * In v1 every service shares the same signing-key (HS256) so any signed
 * service token COULD be acceptable here. The audience check still acts as
 * defense-in-depth: a token minted with {@code aud=payment} cannot
 * authenticate against Subscription's webhook endpoints, even though both
 * services share the v1 key. The moment per-service keys land, this gap
 * stops being defense-in-depth and becomes hard isolation.
 *
 * The exp check is explicit because jjwt only validates exp when the claim
 * is present — a token without exp was effectively a forever-valid key, so
 * a leaked one had no clock-based recovery.
 */
@Component
public class SystemTokenVerifier {

    private static final String EXPECTED_AUDIENCE = "subscription";

    private final SecretKey signingKey;

    public SystemTokenVerifier(@Value("${service-jwt.signing-key}") String secret) {
        // bug_006 — fail loud instead of silently zero-padding short keys.
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
            // jjwt 0.12 returns Set<String> for aud; older versions returned
            // a scalar. Handle both via Collection membership, NEVER via a
            // substring match (otherwise "subscriptionservice" would slip past).
            if (!audienceMatches(claims.getAudience())) {
                throw new UnauthorizedAccessException("System token aud does not include 'subscription'");
            }
            // Refuse tokens without exp — see class javadoc.
            if (claims.getExpiration() == null) {
                throw new UnauthorizedAccessException("System token missing exp claim");
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
