package com.example.DumbleWallet.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints short-lived (60s) signed JWTs for Class B system-context outbound
 * calls — Wallet PDF Decision 6.4. Used when calling Payment service from a
 * background worker / event handler where there's no user JWT in flight.
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
