package com.dumble.service.schedule.security;

import com.dumble.service.schedule.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated {@link AuthPrincipal} from the security context. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        throw new UnauthorizedException("Not authenticated");
    }
}
