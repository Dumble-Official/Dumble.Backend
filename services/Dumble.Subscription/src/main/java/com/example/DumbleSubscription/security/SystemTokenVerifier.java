package com.example.DumbleSubscription.security;

import com.example.DumbleSubscription.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Verifies system JWTs (Class B inter-service auth, Decision 8.4) used on
 * inbound webhook + system-context endpoints. Mirror of {@link SystemTokenSigner}
 * but for the receiving side.
 *
 * In v1 every service shares the same signing-key (HS256) so any signed
 * service token is acceptable here. When a key registry is added, this class
 * grows to look up the verification key by issuer claim.
 */
@Component
public class SystemTokenVerifier {

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
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedAccessException("Invalid or expired system token");
        }
    }
}
