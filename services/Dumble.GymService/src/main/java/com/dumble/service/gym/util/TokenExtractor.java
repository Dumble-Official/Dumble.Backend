package com.dumble.service.gym.util;

import com.dumble.service.gym.domain.dto.UserResponse;
import com.dumble.service.gym.exception.UnauthorizedAccessException;
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

@Component
public class TokenExtractor {

    private final SecretKey signingKey;

    public TokenExtractor(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public UserResponse extractUser(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedAccessException("Missing Authorization token");
        }
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;

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

        Object userIdClaim = claims.get("userId");
        UUID uuid;
        if (userIdClaim instanceof String s) {
            try {
                uuid = UUID.fromString(s);
            } catch (IllegalArgumentException ex) {
                throw new UnauthorizedAccessException("JWT 'userId' claim is not a valid UUID");
            }
        } else {
            throw new UnauthorizedAccessException("JWT missing 'userId' claim");
        }

        // Prefer the explicit `userType` claim (single source of truth) and fall
        // back to deriving from the roles list for backward-compat with tokens
        // minted before that claim was added.
        String userType = "PARTICIPANT";
        Object userTypeClaim = claims.get("userType");
        if (userTypeClaim instanceof String s && !s.isBlank()) {
            userType = s;
        } else {
            Object rolesClaim = claims.get("roles");
            if (rolesClaim instanceof List<?> roles) {
                for (Object r : roles) {
                    String role = String.valueOf(r);
                    if ("ROLE_ADMIN".equals(role)) {
                        userType = "ADMIN";
                        break;
                    } else if ("ROLE_GYM_OWNER".equals(role)) {
                        userType = "GYM_OWNER";
                    } else if ("ROLE_GYM".equals(role) && !"GYM_OWNER".equals(userType)) {
                        userType = "GYM";
                    } else if ("ROLE_TRAINER".equals(role) && "PARTICIPANT".equals(userType)) {
                        userType = "TRAINER";
                    } else if ("ROLE_MODERATOR".equals(role) && "PARTICIPANT".equals(userType)) {
                        userType = "MODERATOR";
                    }
                }
            }
        }

        UserResponse user = new UserResponse();
        user.setId(uuid);
        user.setUserType(userType);
        return user;
    }
}
