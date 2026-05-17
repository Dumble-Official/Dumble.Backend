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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Fail-mode on Redis outage is configurable (auth.rate-limit.fail-mode):
 *   open   — let requests through (default; availability over security).
 *   closed — return 503 (security over availability).
 *
 * Client-IP resolution: X-Forwarded-For is only honoured when the immediate
 * peer is in {@code gateway.trusted-proxies}; otherwise we use the direct
 * remote address. Without this, any client can spoof the header and rotate
 * IPs per request, making the per-IP limit useless.
 */
@Component
@Order(0)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Atomic INCR + EXPIRE. Without the Lua wrapper there's a window where a
     * crash between INCR (returning 1) and EXPIRE leaves a counter with no TTL,
     * eventually locking the IP out forever. The script also sets the TTL
     * idempotently if the key somehow already exists without one.
     */
    private static final DefaultRedisScript<Long> INCR_AND_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]); "
                    + "if redis.call('TTL', KEYS[1]) < 0 then "
                    + "  redis.call('EXPIRE', KEYS[1], ARGV[1]) "
                    + "end; "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> capsPerMinute;
    private final Set<String> trustedProxies;
    private final boolean failClosed;

    public AuthRateLimitFilter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${auth.rate-limit.requests-per-minute:10}") int credentialCap,
            @Value("${auth.rate-limit.hub-token-per-minute:30}") int hubTokenCap,
            @Value("${gateway.trusted-proxies:}") String trustedProxiesCsv,
            @Value("${auth.rate-limit.fail-mode:open}") String failMode) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.capsPerMinute = Map.of(
                "/api/auth/login",     credentialCap,
                "/api/auth/register",  credentialCap,
                "/api/auth/refresh",   credentialCap,
                "/api/auth/google",    credentialCap,
                "/api/auth/hub-token", hubTokenCap
        );
        this.trustedProxies = parseTrustedProxies(trustedProxiesCsv);
        this.failClosed = "closed".equalsIgnoreCase(failMode);
        if (failMode != null && !failMode.isBlank()
                && !"open".equalsIgnoreCase(failMode)
                && !"closed".equalsIgnoreCase(failMode)) {
            log.warn("auth.rate-limit.fail-mode='{}' is not recognised; defaulting to 'open'", failMode);
        }
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {

        // CORS preflights are unauthenticated infrastructure traffic — counting
        // them against the credential-abuse budget can lock real users out
        // during a burst of preflighted requests from a busy SPA.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Integer cap = capsPerMinute.get(path);
        if (cap == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        String key = "ratelimit:auth:" + path + ":" + ip;

        try {
            Long count = redis.execute(
                    INCR_AND_EXPIRE,
                    Collections.singletonList(key),
                    String.valueOf(WINDOW.getSeconds()));
            if (count != null && count > cap) {
                log.warn("Rate limit exceeded for {} from {} (count={}, cap={})", path, ip, count, cap);
                response.setHeader("Retry-After", String.valueOf(WINDOW.getSeconds()));
                writeError(response, 429, "Too many requests, please try again later");
                return;
            }
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable during rate-limit check for {} (fail-mode={})",
                    path, failClosed ? "closed" : "open");
            if (failClosed) {
                writeError(response, 503, "Authentication service temporarily unavailable");
                return;
            }
            // fall through — fail-open
        } catch (Exception e) {
            log.error("Unexpected error in rate-limit check for {}: {}", path, e.getMessage());
            // For unexpected exceptions (script error, serialization, etc.) we keep
            // the same fail-mode behaviour as Redis outage.
            if (failClosed) {
                writeError(response, 503, "Authentication service temporarily unavailable");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolve the rate-limit IP. We only honour X-Forwarded-For when the
     * immediate peer is in the configured trusted-proxy allowlist. Otherwise
     * the header is attacker-controlled and lets anyone rotate the rate-limit
     * key per request — making the per-IP cap useless. Defaults to the direct
     * remote address.
     */
    private String clientIp(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        if (remote != null && trustedProxies.contains(remote)) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // X-Forwarded-For: client, proxy1, proxy2 → first hop is the real client
                return xff.split(",")[0].trim();
            }
        }
        return remote;
    }

    private static Set<String> parseTrustedProxies(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse error = new ErrorResponse(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    // Kept for potential test injection of additional trusted proxies; no public mutators.
    @SuppressWarnings("unused")
    private List<String> debugTrustedProxies() {
        return List.copyOf(trustedProxies);
    }
}
