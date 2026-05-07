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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Per-IP rate limit on the auth endpoints — defends against credential
 * stuffing, password brute-force, registration spam, and hub-token pumping.
 * Runs at Order(0) so it kicks in BEFORE the JWT/Banned filters.
 *
 * Two tiers:
 *   • Public credential endpoints (login/register/refresh/google) get the
 *     strict cap — these are unauthenticated and the most-abused paths.
 *   • /api/auth/hub-token is authenticated (only valid-token holders reach it)
 *     but still capped to stop a compromised access token from pumping the
 *     short-lived hub-token mint as a CPU/JIT amplifier.
 *
 * Fail-open on Redis outage: locking everyone out of login because the cache
 * is down would be worse than the missed rate limit. BannedUserFilter is the
 * one that fails closed because that protects against an active threat.
 */
@Component
@Order(0)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> capsPerMinute;

    public AuthRateLimitFilter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${auth.rate-limit.requests-per-minute:10}") int credentialCap,
            @Value("${auth.rate-limit.hub-token-per-minute:30}") int hubTokenCap) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.capsPerMinute = Map.of(
                "/api/auth/login",     credentialCap,
                "/api/auth/register",  credentialCap,
                "/api/auth/refresh",   credentialCap,
                "/api/auth/google",    credentialCap,
                "/api/auth/hub-token", hubTokenCap
        );
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        Integer cap = capsPerMinute.get(path);
        if (cap == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        String key = "ratelimit:auth:" + path + ":" + ip;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            if (count != null && count > cap) {
                log.warn("Rate limit exceeded for {} from {} (count={}, cap={})", path, ip, count, cap);
                response.setHeader("Retry-After", String.valueOf(WINDOW.getSeconds()));
                writeError(response, 429, "Too many requests, please try again later");
                return;
            }
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable during rate-limit check for {}", path);
        } catch (Exception e) {
            log.error("Unexpected error in rate-limit check for {}: {}", path, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 → first hop is the real client
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse error = new ErrorResponse(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
