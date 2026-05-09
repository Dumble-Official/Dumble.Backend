package com.example.DumbleSubscription.security;

import com.example.DumbleSubscription.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            keyBytes = secret.getBytes();
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
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
