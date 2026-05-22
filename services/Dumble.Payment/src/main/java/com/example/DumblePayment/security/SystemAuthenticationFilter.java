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
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the system JWT (Decision 9.3) and populates the security context
 * with the caller's {@code iss} as a ROLE so downstream {@code @PreAuthorize}
 * checks can pin admin endpoints to the admin caller, etc.
 *
 * <p>Granted authorities:
 * <ul>
 *   <li>{@code ROLE_SERVICE} — every valid system caller.</li>
 *   <li>{@code ROLE_SERVICE_<ISS>} — caller-specific (e.g. {@code ROLE_SERVICE_SUBSCRIPTION-SERVICE});
 *       lets a future route pin "only Subscription may call this" via
 *       {@code @PreAuthorize("hasRole('SERVICE_SUBSCRIPTION-SERVICE')")}.</li>
 *   <li>{@code ROLE_ADMIN} — granted only when {@code iss=admin}. Required by
 *       {@code SecurityConfig.requestMatchers("/admin/**").hasRole("ADMIN")} so
 *       the reconciliation dashboard is reachable. Without this explicit
 *       mapping the admin path was dead code — any caller had {@code ROLE_SERVICE_*}
 *       but never the bare {@code ROLE_ADMIN} authority {@code hasRole} expects.</li>
 * </ul>
 *
 * <p>Webhook endpoints are deliberately whitelisted in {@code SecurityConfig} —
 * they use HMAC verification (Decision 4.1) instead of system JWT.
 */
@Component
public class SystemAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SystemAuthenticationFilter.class);
    private static final String ADMIN_ISSUER = "admin";

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

                List<SimpleGrantedAuthority> authorities = new ArrayList<>(3);
                authorities.add(new SimpleGrantedAuthority("ROLE_SERVICE"));
                authorities.add(new SimpleGrantedAuthority("ROLE_SERVICE_" + issuer.toUpperCase()));
                if (ADMIN_ISSUER.equalsIgnoreCase(issuer)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }

                var auth = new UsernamePasswordAuthenticationToken(issuer, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UnauthorizedAccessException ex) {
                log.warn("System JWT validation failed (path={}): {}", request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
