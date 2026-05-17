package com.example.DumbleGateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

/**
 * JWT Service for the Gateway — validation only (no token generation).
 * Uses the same secret key and signing algorithm as the Auth service
 * to verify token signature and expiry without any DB/network call.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * Parse and validate the JWT, returning all claims.
     * Throws ExpiredJwtException if token is expired.
     * Throws other JwtException subtypes if token is invalid.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    public UUID extractUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ex) {
                throw new io.jsonwebtoken.MalformedJwtException("Invalid 'userId' claim: not a UUID");
            }
        }
        // Fail-closed: a token without userId cannot be authorised — otherwise
        // BannedUserFilter and any downstream userId-based check is bypassed.
        throw new io.jsonwebtoken.MalformedJwtException("Missing 'userId' claim");
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return List.of();
    }

    /**
     * Same key derivation as Auth service:
     * Base64-decode the secret → HMAC-SHA key.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
