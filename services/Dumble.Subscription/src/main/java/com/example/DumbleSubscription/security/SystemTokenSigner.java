package com.example.DumbleSubscription.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints short-lived (60s) signed JWTs for Class B system-context outbound
 * calls — see Subscription PDF Decision 8.4. Used when calling Payment service
 * from a scheduler / event handler where there's no user JWT to forward.
 */
@Component
public class SystemTokenSigner {

    private final SecretKey signingKey;
    private final String issuer;
    private final long ttlSeconds;

    public SystemTokenSigner(@Value("${service-jwt.signing-key}") String secret,
                             @Value("${service-jwt.issuer}") String issuer,
                             @Value("${service-jwt.ttl-seconds:60}") long ttlSeconds) {
        // bug_006 — accept base64 or raw; refuse anything < 32 bytes rather
        // than silently zero-padding (the old behaviour produced a publicly
        // predictable HMAC key).
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
