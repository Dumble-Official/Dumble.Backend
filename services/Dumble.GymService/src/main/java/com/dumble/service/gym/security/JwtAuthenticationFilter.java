package com.dumble.service.gym.security;

import com.dumble.service.gym.domain.dto.UserResponse;
import com.dumble.service.gym.exception.UnauthorizedAccessException;
import com.dumble.service.gym.util.TokenExtractor;
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
 * Validates the JWT in the Authorization header and populates the Spring
 * Security context. Lets Spring Security's filter chain enforce auth rules
 * (versus the previous "every controller calls TokenExtractor manually"
 * pattern, which silently left endpoints unauthenticated when forgotten).
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
                UserResponse user = tokenExtractor.extractUser(header);
                var auth = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        // userType is one of USER / GYM / ADMIN — expose as ROLE_X for @PreAuthorize
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
