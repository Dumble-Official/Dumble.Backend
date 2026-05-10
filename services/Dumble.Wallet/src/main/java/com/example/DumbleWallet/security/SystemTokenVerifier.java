package com.example.DumbleWallet.security;

import com.example.DumbleWallet.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Verifies system JWTs (Class B inter-service auth, Wallet PDF Decision 6.4)
 * presented by callers like Subscription on system-context endpoints
 * ({@code POST /wallet/credit} during a ban refund where no user JWT is in
 * flight). Mirror of {@link SystemTokenSigner} but for the receiving side.
 */
@Component
public class SystemTokenVerifier {

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
