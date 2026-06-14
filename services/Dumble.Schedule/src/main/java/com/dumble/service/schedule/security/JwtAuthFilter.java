package com.dumble.service.schedule.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates the gateway-issued user JWT locally (same shared secret + claim
 * shape as auth/gym: {@code userId} + {@code userType}) and sets the
 * SecurityContext. Invalid/absent tokens are left unauthenticated so
 * SecurityConfig returns 401/403 on protected routes.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(header.substring(7))
                        .getPayload();

                Object userIdClaim = claims.get("userId");
                if (userIdClaim instanceof String idStr) {
                    UUID userId = UUID.fromString(idStr);
                    Object typeClaim = claims.get("userType");
                    String userType = (typeClaim instanceof String s && !s.isBlank()) ? s : "PARTICIPANT";

                    AuthPrincipal principal = new AuthPrincipal(userId, userType);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + userType)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // Bad/expired token → stay unauthenticated; SecurityConfig denies.
            }
        }
        chain.doFilter(request, response);
    }
}
