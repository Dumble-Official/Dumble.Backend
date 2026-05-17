package com.example.DumbleGateway.filter;

import com.example.DumbleGateway.dto.ErrorResponse;
import com.example.DumbleGateway.service.JwtService;
import tools.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * First filter in the chain (Order 1).
 * Validates JWT signature + expiry using math only — no DB or Auth service call.
 * Stores extracted user claims in request attributes for downstream filters.
 */
@Component
@Order(1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    // Routes that do not require authentication. Includes the actuator
    // liveness/readiness endpoints so external load balancers and orchestrators
    // (k8s, ECS, ALB target group) can probe the gateway without minting a JWT.
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/google",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness"
    );

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip public routes — no JWT required
        if (isPublicRoute(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        // Extract JWT from Authorization header or access_token query param (SignalR)
        String jwt = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        } else if (path.startsWith("/hubs/")) {
            // SignalR negotiates over HTTP and upgrades to WebSocket; browsers
            // disallow custom headers on the upgrade, so the token must travel
            // via query string. To bound browser-history / Referer / proxy-log
            // leakage, clients MUST exchange their regular access token for a
            // 60-second hub token via POST /api/auth/hub-token before opening
            // the connection. The gateway validates either token the same way
            // (same signing key) — the short TTL is the actual mitigation.
            // We also strip access_token from logs and emit no-store headers
            // on /hubs responses below.
            jwt = request.getParameter("access_token");
        }

        // No token provided
        if (jwt == null || jwt.isBlank()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header");
            return;
        }

        try {
            // Validate signature + parse claims (throws if invalid/expired)
            Claims claims = jwtService.extractAllClaims(jwt);

            // Enforce hub-token usage on /hubs/** so the long-lived access token
            // never ends up in browser history / proxy logs / Referer. Only
            // tokens minted via POST /api/auth/hub-token carry purpose=hub.
            if (path.startsWith("/hubs/")) {
                Object purpose = claims.get("purpose");
                if (!"hub".equals(purpose)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Hub connections must use a token from POST /api/auth/hub-token");
                    return;
                }
            }

            // Store user info in request attributes for BannedUserFilter
            request.setAttribute("userId", jwtService.extractUserId(claims));
            request.setAttribute("userEmail", jwtService.extractUsername(claims));
            request.setAttribute("userRoles", jwtService.extractRoles(claims));

            // Defense-in-depth for SignalR token-in-URL: prevent any HTTP cache,
            // proxy, or browser back/forward cache from holding the URL with
            // its access_token query string, and suppress the Referer header.
            if (path.startsWith("/hubs/")) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Referrer-Policy", "no-referrer");
            }

        } catch (ExpiredJwtException e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
            return;
        } catch (Exception e) {
            // WARN level so brute-force / forgery attempts are visible in prod.
            // Path is logged WITHOUT query string to avoid leaking SignalR tokens.
            log.warn("JWT validation failed (path={}, type={}): {}",
                    path, e.getClass().getSimpleName(), e.getMessage());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
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
