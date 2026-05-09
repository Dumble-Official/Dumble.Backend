package com.example.DumbleSubscription.client;

import com.example.DumbleSubscription.client.dto.UserPublicProfile;
import com.example.DumbleSubscription.security.SystemTokenSigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Component
public class AuthenticationClient {

    private final WebClient client;
    private final SystemTokenSigner signer;

    public AuthenticationClient(@Qualifier("authenticationClient") WebClient client,
                                SystemTokenSigner signer) {
        this.client = client;
        this.signer = signer;
    }

    /**
     * Fetches the public profile (name, photo, etc.) for a user. System-context
     * call — used by Subscription's seller dashboards to enrich subscriber lists
     * with display names and photos (no PII per Decision 10.2).
     *
     * Returns null when the upstream user is not found or has been soft-deleted
     * (Decision 10.4 — caller should render "[deleted account]" in that case).
     */
    public UserPublicProfile getPublicProfile(UUID userId) {
        try {
            return client.get()
                    .uri("/api/users/{id}/public-profile", userId)
                    .header("Authorization", "Bearer " + signer.mint("authentication"))
                    .retrieve()
                    .bodyToMono(UserPublicProfile.class)
                    .block();
        } catch (WebClientResponseException.NotFound notFound) {
            return null;
        } catch (Exception ex) {
            // Failing closed (returning null) is safer than blocking the whole
            // dashboard render on Authentication availability.
            return null;
        }
    }
}
