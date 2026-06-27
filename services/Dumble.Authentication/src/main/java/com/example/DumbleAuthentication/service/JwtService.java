package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.RefreshToken;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.exception.TokenRefreshException;
import com.example.DumbleAuthentication.repository.RefreshTokenRepository;
import com.example.DumbleAuthentication.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.hub-token-expiration}")
    private long hubTokenExpiration;

    public JwtService(RefreshTokenRepository refreshTokenRepository,
                      UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    // ── Access Token Operations ──────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails, User user) {
        return buildToken(baseClaims(userDetails, user), userDetails.getUsername(), accessTokenExpiration);
    }

    /**
     * Short-lived token (≈60s) for SignalR WebSocket auth. Same signing key
     * and claims as the regular access token — the gateway validates it the
     * same way — but with a `purpose=hub` claim so logging/audit can tell
     * them apart, and an aggressive expiration so browser-history /
     * Referer-header leakage of the URL is bounded.
     */
    public String generateHubToken(UserDetails userDetails, User user) {
        Map<String, Object> claims = baseClaims(userDetails, user);
        claims.put("purpose", "hub");
        return buildToken(claims, userDetails.getUsername(), hubTokenExpiration);
    }

    private Map<String, Object> baseClaims(UserDetails userDetails, User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList());
        claims.put("userId", user.getId());
        // Identity claims so downstream services don't need an extra /api/users/me round-trip
        // every request. Stale display names on cached tokens are acceptable until next refresh.
        // Use the EFFECTIVE display name (falls back to first+last name) — otherwise users who
        // never set an explicit displayName mint tokens with no name claim, and every post /
        // reaction / comment they create gets denormalised as a blank author → "Dumble User".
        String displayName = user.getEffectiveDisplayName();
        if (displayName != null && !displayName.isBlank()) claims.put("displayName", displayName);
        if (user.getPfp() != null) claims.put("profileImage", user.getPfp());
        if (user.getUserType() != null) claims.put("userType", user.getUserType().name());
        return claims;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            return false;
        }
    }

    // ── Refresh Token Operations ─────────────────────────────────────────

    @Transactional
    public RefreshToken generateRefreshToken(User user) {
        // Pessimistic-write lock on the user row serializes concurrent refresh-token
        // generation for the same user. Without it two parallel callers each see
        // an empty token table after their respective deletes and both `save` a
        // new row, leaving two live refresh tokens for the same user — a real
        // identity-leak window if one is later exfiltrated while the other is
        // still in active use.
        User locked = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User vanished mid-refresh: " + user.getId()));

        refreshTokenRepository.deleteByUser(locked);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(locked);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpiration));

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException("Refresh token has expired. Please login again.");
        }

        return refreshToken;
    }

    /**
     * Non-throwing lookup for idempotent flows like logout: returns the row if
     * present (regardless of expiry) without raising when it's absent. Unlike
     * {@link #validateRefreshToken}, a missing token is a normal outcome here,
     * not an error.
     */
    public Optional<RefreshToken> findRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public void deleteRefreshToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void deleteAllUserRefreshTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer("dumble-auth")
                .audience().add("dumble-app").and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
