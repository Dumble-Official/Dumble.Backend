package com.example.DumbleWallet.security;

import com.example.DumbleWallet.dto.CurrentUser;
import com.example.DumbleWallet.exception.UnauthorizedAccessException;
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
 * Validates the user JWT and populates the Spring Security context. The
 * system JWT path (Class B per Wallet PDF Decision 6.4) is verified inside
 * the controllers that need it via {@link SystemTokenVerifier} — those
 * endpoints are whitelisted in {@code SecurityConfig}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenExtractor tokenExtractor;

    public JwtAuthenticationFilter(TokenExtractor tokenExtractor) {
        this.tokenExtractor = tokenExtractor;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                CurrentUser user = tokenExtractor.extractUser(header);
                var auth = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getUserType()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UnauthorizedAccessException e) {
                log.warn("JWT validation failed (path={}): {}", request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
