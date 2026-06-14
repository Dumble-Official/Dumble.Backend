package com.dumble.service.schedule.security;

import com.dumble.service.schedule.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Validates the shared X-Internal-Secret on service-to-service endpoints. Fail-closed if unset. */
@Component
public class InternalSecret {

    private final String secret;

    public InternalSecret(@Value("${internal.api-secret:}") String secret) {
        this.secret = secret;
    }

    public void require(String provided) {
        if (secret == null || secret.isBlank() || provided == null
                || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid internal secret");
        }
    }
}
