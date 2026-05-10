package com.example.DumbleSubscription.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Resolves the configured {@code service-jwt.signing-key} into a real HS256
 * SecretKey. Refuses anything shorter than 32 bytes — bug_006: silently
 * zero-padding a short key (the previous behaviour) defeats jjwt's
 * Keys.hmacShaKeyFor minimum-length safeguard, leaving the trailing bytes
 * publicly predictable so an attacker who has read the repo can forge
 * system JWTs in any non-prod environment.
 */
final class SystemSigningKey {

    private SystemSigningKey() {}

    static SecretKey resolve(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "service-jwt.signing-key is missing; HS256 requires a >= 32-byte key");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            keyBytes = secret.getBytes();
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "service-jwt.signing-key resolves to " + keyBytes.length
                            + " bytes; HS256 requires >= 32. Refusing to silently zero-pad.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
