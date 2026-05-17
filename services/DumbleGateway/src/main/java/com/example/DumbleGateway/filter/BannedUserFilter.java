package com.example.DumbleGateway.filter;

import com.example.DumbleGateway.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Second filter in the chain (Order 2).
 * Checks if the authenticated user's ID exists in the Redis "banned_users" set.
 * This is an O(1) lookup — no DB call needed.
 */
@Component
@Order(2)
public class BannedUserFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BannedUserFilter.class);
    private static final String BANNED_USERS_KEY = "banned_users";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Same public routes as JwtAuthenticationFilter — no check needed.
    // Includes the actuator liveness/readiness endpoints so external probes
    // don't hit Redis for every health check.
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/google",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness"
    );

    public BannedUserFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip public routes
        if (isPublicRoute(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // userId was set by JwtAuthenticationFilter (Order 1) and is guaranteed
        // non-null at this point — the auth filter rejects tokens without userId.
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing user identity");
            return;
        }

        try {
            Boolean isBanned = redisTemplate.opsForSet()
                    .isMember(BANNED_USERS_KEY, userId.toString());

            if (Boolean.TRUE.equals(isBanned)) {
                log.info("Blocked request from banned user: {}", userId);
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "User account is banned");
                return;
            }
        } catch (RedisConnectionFailureException e) {
            // Fail-closed: cannot verify ban status, refuse rather than risk a
            // banned user slipping through during a Redis outage.
            log.error("Redis unavailable — refusing request for safety (userId={})", userId);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Authorization service temporarily unavailable");
            return;
        } catch (Exception e) {
            log.error("Unexpected error in banned-user check (userId={}): {}",
                    userId, e.getMessage());
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Authorization service temporarily unavailable");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicRoute(String path) {
        // Exact match — `startsWith` would match `/api/auth/loginX` and bypass auth.
        return PUBLIC_PATHS.contains(path);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse error = new ErrorResponse(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
