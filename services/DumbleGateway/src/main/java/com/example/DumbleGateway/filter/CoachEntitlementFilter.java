package com.example.DumbleGateway.filter;

import com.example.DumbleGateway.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Third filter in the chain (Order 3).
 * Gates the FitCoach AI chatbot ({@code /api/coach/**}):
 *   1. fetches the caller's plan from Subscription /me/entitlements
 *   2. enforces the per-day Redis-backed message counter for non-unlimited tiers
 *   3. injects {@code X-User-Id} and {@code X-Internal-Secret} on the outgoing
 *      proxied request so FitCoach can trust the identity without re-verifying
 *      the user JWT (FitCoach has no Spring Security / JWT verifier of its own)
 *
 * Fail mode for the Subscription entitlement call is configurable
 * ({@code fitcoach.fail-mode}); default is {@code closed} because the chatbot
 * is a PRO-tier feature and giving it away free during an outage is worse
 * than a brief unavailability.
 */
@Component
@Order(3)
public class CoachEntitlementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CoachEntitlementFilter.class);

    private static final String COACH_PATH_PREFIX = "/api/coach/";
    private static final String COUNTER_KEY_FMT = "chatbot:msgs:%s:%s";
    private static final DateTimeFormatter DAY_KEY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    // /health is the FitCoach liveness probe (cheap, no Gemini call) and
    // /feedback only updates per-user memory weights — neither should count
    // against the daily Gemini-message quota.
    private static final Set<String> NON_BILLABLE_SUFFIXES = Set.of("/health", "/feedback");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient subscriptionClient;
    private final String internalSecret;
    private final boolean failOpen;

    public CoachEntitlementFilter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${fitcoach.subscription-base-url:http://localhost:8182}") String subscriptionBaseUrl,
            @Value("${fitcoach.internal-secret:}") String internalSecret,
            @Value("${fitcoach.fail-mode:closed}") String failMode) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.subscriptionClient = RestClient.builder()
                .baseUrl(subscriptionBaseUrl)
                .build();
        this.internalSecret = internalSecret;
        this.failOpen = "open".equalsIgnoreCase(failMode);
        if (internalSecret == null || internalSecret.isBlank()) {
            // Hard fail-loud at boot. FitCoach refuses requests without the
            // shared secret, so missing it means /api/coach/** is dead anyway —
            // surface the misconfiguration in the gateway log instead of a
            // mysterious 401 from the upstream Python service.
            log.error("INTERNAL_API_SECRET is empty — every /api/coach/** call will fail upstream");
        }
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(COACH_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // userId is set by JwtAuthenticationFilter (Order 1). If it's missing
        // here, BannedUserFilter (Order 2) would have already 401'd — defence
        // in depth.
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing user identity");
            return;
        }
        String userId = userIdAttr.toString();

        Entitlements ent;
        try {
            String authHeader = request.getHeader("Authorization");
            ent = subscriptionClient.get()
                    .uri("/api/me/entitlements")
                    .header("Authorization", authHeader == null ? "" : authHeader)
                    .retrieve()
                    .body(Entitlements.class);
        } catch (Exception ex) {
            log.warn("Entitlement lookup failed for user {} (failMode={}): {}",
                    userId, failOpen ? "open" : "closed", ex.getMessage());
            if (failOpen) {
                ent = null;
            } else {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Coach is temporarily unavailable; please retry");
                return;
            }
        }

        if (ent != null && !ent.canUseChatbot()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "AI Coach requires a PRO subscription");
            return;
        }

        // Counter is per-UTC-day. We only increment on the *billable* paths
        // (/chat, /chat/stream, /voice, /analyze, /analyze/stream) so probes
        // and feedback don't burn quota. ent==null happens only under fail-open;
        // in that case skip the quota check too (already chose availability).
        if (ent != null && ent.chatbotMessagesPerDay() != null && isBillable(path)) {
            int cap = ent.chatbotMessagesPerDay();
            // Cap of 0 means the plan grants chatbot access but no messages — the
            // entitlement bundle is inconsistent. Refuse rather than counting up
            // from zero (every request would be 429 anyway). Cap of 0 with
            // canUseChatbot=true is the FREE-plus-bug case we want to surface.
            if (cap <= 0) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "AI Coach requires a PRO subscription");
                return;
            }
            String key = String.format(COUNTER_KEY_FMT, userId, DAY_KEY.format(Instant.now()));
            try {
                Long after = redis.opsForValue().increment(key);
                if (after != null && after == 1L) {
                    // 36h TTL > one UTC day so the row survives a slow midnight
                    // rollover but doesn't linger forever. The next day's request
                    // mints a fresh key (date suffix), so no race with reset.
                    redis.expire(key, 36, TimeUnit.HOURS);
                }
                if (after != null && after > cap) {
                    // 429 — jakarta.servlet.http.HttpServletResponse doesn't
                    // define SC_TOO_MANY_REQUESTS, so use the literal.
                    response.setHeader("Retry-After", "3600");
                    writeError(response, 429,
                            "Daily AI Coach quota exhausted; resets at 00:00 UTC");
                    return;
                }
            } catch (RedisConnectionFailureException ex) {
                // Same posture as the entitlement lookup: closed = refuse,
                // open = let the request through (Gemini cost > Redis outage cost
                // is the trade-off operators tune via FITCOACH_FAIL_MODE).
                log.warn("Redis unreachable during quota check for user {}: {}",
                        userId, ex.getMessage());
                if (!failOpen) {
                    writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "Coach quota service unavailable; please retry");
                    return;
                }
            }
        }

        // Inject identity + shared secret for the upstream proxied request.
        // Wrapping the HttpServletRequest is how Spring Cloud Gateway
        // server-webmvc picks the headers up — its forward step reads from
        // request.getHeaderNames() / getHeader(name) when building the outgoing
        // RestClient call.
        HttpServletRequest forwarded = new HeaderInjectingRequest(request, Map.of(
                "X-User-Id", userId,
                "X-Internal-Secret", internalSecret == null ? "" : internalSecret
        ));
        filterChain.doFilter(forwarded, response);
    }

    private boolean isBillable(String path) {
        for (String suffix : NON_BILLABLE_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse error = new ErrorResponse(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    /**
     * Subset of {@code EntitlementsResponse} from Subscription — only the
     * fields the chatbot gate needs. Kept as a record so the deserialization
     * is positional-by-name and tolerant of extra fields.
     */
    public record Entitlements(
            String planCode,
            boolean canUseChatbot,
            Integer chatbotMessagesPerDay,
            boolean canDmAnyone
    ) {}

    /**
     * Wraps the inbound servlet request to expose extra headers (case-insensitive).
     * Spring Cloud Gateway's webmvc forward step iterates getHeaderNames() so the
     * added headers must show up there too — not just in getHeader().
     */
    private static class HeaderInjectingRequest extends HttpServletRequestWrapper {
        private final Map<String, String> extra;

        HeaderInjectingRequest(HttpServletRequest request, Map<String, String> extra) {
            super(request);
            this.extra = extra;
        }

        @Override
        public String getHeader(String name) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(e.getValue()));
                }
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            // LinkedHashSet preserves the original order and de-dupes any
            // header the client already sent under the same name — gateway
            // should never trust client-supplied X-User-Id / X-Internal-Secret.
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String n = original.nextElement();
                // Drop client-supplied versions of the injected headers so
                // they don't leak through alongside ours.
                boolean isInjected = false;
                for (String injected : extra.keySet()) {
                    if (injected.equalsIgnoreCase(n)) {
                        isInjected = true;
                        break;
                    }
                }
                if (!isInjected) {
                    names.add(n);
                }
            }
            names.addAll(extra.keySet());
            return Collections.enumeration(names);
        }
    }
}
