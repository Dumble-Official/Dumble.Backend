package com.dumble.service.session.security;

import com.dumble.service.session.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Collection;

@Component
public class SystemTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(SystemTokenVerifier.class);
    private static final String EXPECTED_AUDIENCE = "dumble-app";
    private final SecretKey signingKey;

    public SystemTokenVerifier(@Value("${service-jwt.signing-key}") String secret) {
        byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
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

            if (claims.getExpiration() == null) {
                throw new UnauthorizedAccessException("System token missing exp claim");
            }

            if (!audienceMatches(claims.getAudience())) {
                throw new UnauthorizedAccessException("System token aud does not include 'session'");
            }
            return claims;
        } catch (JwtException ex) {
            // Slf4j keeps this in the structured log pipeline; the previous
            // System.out.println + ex.printStackTrace() bypassed log levels
            // and rotation, dumping straight to stderr.
            log.warn("System JWT verification failed: {}", ex.getMessage());
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