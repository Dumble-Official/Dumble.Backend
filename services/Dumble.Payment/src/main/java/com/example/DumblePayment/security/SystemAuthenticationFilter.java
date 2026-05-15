package com.example.DumblePayment.security;

import com.example.DumblePayment.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the system JWT (Decision 9.3) and populates the security context
 * with the caller's {@code iss} as a ROLE so downstream {@code @PreAuthorize}
 * checks can pin admin endpoints to the admin caller, etc.
 *
 * Webhook endpoints are deliberately whitelisted in {@code SecurityConfig} —
 * they use HMAC verification (Decision 4.1) instead of system JWT.
 */
@Component
public class SystemAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SystemAuthenticationFilter.class);

    private final SystemTokenVerifier verifier;

    public SystemAuthenticationFilter(SystemTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = verifier.verify(header);
                String issuer = claims.getIssuer() == null ? "unknown" : claims.getIssuer();
                String role = "ROLE_SERVICE_" + issuer.toUpperCase();
                var auth = new UsernamePasswordAuthenticationToken(
                        issuer,
                        null,
                        List.of(new SimpleGrantedAuthority(role),
                                new SimpleGrantedAuthority("ROLE_SERVICE")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UnauthorizedAccessException ex) {
                log.warn("System JWT validation failed (path={}): {}", request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
