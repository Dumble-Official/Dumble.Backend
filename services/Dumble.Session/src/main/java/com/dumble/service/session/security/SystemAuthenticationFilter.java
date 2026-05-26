package com.dumble.service.session.security;

import com.dumble.service.session.exception.UnauthorizedAccessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

                // Roles are derived ONLY from the `roles` claim Auth puts in
                // the user JWT — never from the `iss` value. The previous
                // shape granted ROLE_ADMIN to any token whose `iss` claim
                // equalled "admin"; since `iss` is a freely-set payload
                // field, anyone holding JWT_SECRET could mint themselves an
                // admin token. Trust only what Auth signs into `roles`.
                List<SimpleGrantedAuthority> authorities = new ArrayList<>(3);

                String principalName = claims.get("userId", String.class);
                if (principalName == null) {
                    principalName = claims.getSubject() != null
                            ? claims.getSubject()
                            : (claims.getIssuer() == null ? "unknown" : claims.getIssuer());
                }

                List<?> roles = claims.get("roles", List.class);
                if (roles != null) {
                    for (Object role : roles) {
                        authorities.add(new SimpleGrantedAuthority(role.toString()));
                    }
                }

                var auth = new UsernamePasswordAuthenticationToken(principalName, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (UnauthorizedAccessException ex) {
                log.warn("System JWT validation failed (path={}): {}", request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();

                writeUnauthorizedResponse(response, ex.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":401,\"message\":\"Unauthorized - " + message + "\"}");
    }
}