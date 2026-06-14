package com.dumble.service.schedule.security;

import java.util.UUID;

/** The authenticated caller, resolved from the JWT (gateway-issued user token). */
public record AuthPrincipal(UUID userId, String userType) {

    public boolean isTrainer() {
        return "TRAINER".equals(userType);
    }
}
