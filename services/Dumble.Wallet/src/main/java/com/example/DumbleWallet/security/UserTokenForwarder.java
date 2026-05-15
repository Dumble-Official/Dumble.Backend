package com.example.DumbleWallet.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Returns the inbound {@code Authorization: Bearer ...} header so outbound
 * HTTP calls can forward the user's JWT (Class A pass-through, Wallet PDF
 * Decision 6.4). Returns {@code null} when there's no servlet request in
 * scope (background thread, scheduler) — caller falls back to a system JWT.
 */
@Component
public class UserTokenForwarder {

    public String currentBearer() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        String header = req.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header : null;
    }
}
