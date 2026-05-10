package com.example.DumbleSubscription.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the inbound user JWT from the current HTTP request so that outbound
 * Class A calls (user-context) can forward it. Returns null when called from
 * background threads (no request bound) — callers must use SystemTokenSigner
 * instead in those cases.
 */
@Component
public class UserTokenForwarder {

    public String currentBearer() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String header = req.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                return header;
            }
        }
        return null;
    }
}
