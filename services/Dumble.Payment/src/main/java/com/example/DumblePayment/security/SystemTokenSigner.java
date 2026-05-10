package com.example.DumblePayment.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints short-lived (60s) signed JWTs for outbound system-context calls.
 * Decision 9.3 — used when Payment publishes events or makes downstream
 * calls (none in v1; placeholder for future flows).
 */
@Component
public class SystemTokenSigner {

    private final SecretKey signingKey;
    private final String issuer;
    private final long ttlSeconds;

    public SystemTokenSigner(@Value("${service-jwt.signing-key}") String secret,
                             @Value("${service-jwt.issuer}") String issuer,
                             @Value("${service-jwt.ttl-seconds:60}") long ttlSeconds) {
        this.signingKey = SystemSigningKey.resolve(secret);
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String mint(String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }
}
