package com.dumble.service.session.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Mints short-lived system JWTs for outbound calls to sibling services.
 * Uses the same {@code service-jwt.signing-key} property and base64
 * decoding as {@link SystemTokenVerifier} — otherwise the receiving
 * service's verifier rejects every token because the byte-array of a
 * base64 string is not the same as the bytes of the original key.
 */
@Component
public class SystemTokenMinter {

    private static final long TTL_MILLIS = 60_000L;
    private final SecretKey signingKey;

    public SystemTokenMinter(@Value("${service-jwt.signing-key}") String secret) {
        byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateSystemToken(String audience) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("session-service")
                .issuer("session")
                .audience().add(audience).and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_MILLIS))
                .signWith(signingKey)
                .compact();
    }
}