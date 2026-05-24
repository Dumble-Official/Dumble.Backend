package com.example.DumbleWallet.security;

import com.example.DumbleWallet.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;

/**
 * Verifies system JWTs (Class B inter-service auth, Wallet PDF Decision 6.4)
 * presented by callers like Subscription on system-context endpoints
 * ({@code POST /wallet/credit} during a ban refund where no user JWT is in
 * flight). Mirror of {@link SystemTokenSigner} but for the receiving side.
 *
 * Audience check is symmetric with Payment's verifier: a token minted with
 * {@code aud=payment} cannot authenticate against Wallet, even though both
 * services currently share the v1 signing key. The moment per-service keys
 * land, this gap stops being defense-in-depth.
 */
@Component
public class SystemTokenVerifier {

    private static final String EXPECTED_AUDIENCE = "wallet";

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
            // jjwt 0.12 returns Set<String> for aud; older versions returned
            // a scalar. Handle both via Collection membership, NEVER via a
            // substring match (otherwise "walletservice" would slip past).
            if (!audienceMatches(claims.getAudience())) {
                throw new UnauthorizedAccessException("System token aud does not include 'wallet'");
            }
            // jjwt only enforces exp when the claim is present. A token issued
            // without exp would otherwise be valid forever — leaking one would
            // have no clock-based recovery. Refuse explicitly.
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
