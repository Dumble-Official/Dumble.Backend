package com.example.DumbleSubscription.security;

import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

/**
 * Validates a Bearer JWT against the platform-wide JWT_SECRET (same key used
 * by Authentication, Gateway and Gym). Pulls the userId, audience/userType
 * and roles from the token. Locally verified — no callback to Authentication.
 *
 * Pattern matches services/Dumble.GymService/.../util/TokenExtractor.java.
 */
@Component
public class TokenExtractor {

    private final SecretKey signingKey;

    public TokenExtractor(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public CurrentUser extractUser(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new UnauthorizedAccessException("Missing Authorization token");
        }
        String jwt = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedAccessException("Invalid or expired JWT");
        }

        UUID userId = parseUserId(claims.get("userId"));
        String userType = resolveUserType(claims);

        CurrentUser user = new CurrentUser();
        user.setId(userId);
        user.setEmail(claims.getSubject());
        user.setDisplayName((String) claims.get("displayName"));
        user.setUserType(userType);
        user.setRoles(extractRoles(claims));
        return user;
    }

    private UUID parseUserId(Object claim) {
        if (claim instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ex) {
                throw new UnauthorizedAccessException("JWT 'userId' claim is not a valid UUID");
            }
        }
        throw new UnauthorizedAccessException("JWT 'userId' claim is missing or unreadable");
    }

    private static String resolveUserType(Claims claims) {
        Object explicit = claims.get("userType");
        if (explicit instanceof String s && !s.isBlank()) {
            return s;
        }
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> roles) {
            String resolved = "PARTICIPANT";
            for (Object r : roles) {
                String role = String.valueOf(r);
                if ("ROLE_ADMIN".equals(role)) return "ADMIN";
                if ("ROLE_GYM_OWNER".equals(role)) resolved = "GYM_OWNER";
                else if ("ROLE_GYM".equals(role) && !"GYM_OWNER".equals(resolved)) resolved = "GYM";
                else if ("ROLE_TRAINER".equals(role) && "PARTICIPANT".equals(resolved)) resolved = "TRAINER";
            }
            return resolved;
        }
        return "PARTICIPANT";
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractRoles(Claims claims) {
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof List<?> roles) {
            return roles.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
