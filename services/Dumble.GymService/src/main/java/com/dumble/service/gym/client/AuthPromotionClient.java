package com.dumble.service.gym.client;

import com.dumble.service.gym.exception.UpstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Calls the auth service's internal endpoint to promote an applicant to
 * GYM_OWNER once their gym registration is approved. Auth owns userType — the
 * gym service only verifies. Authenticated by the shared X-Internal-Secret.
 */
@Component
public class AuthPromotionClient {

    private final RestClient client;
    private final String internalSecret;

    public AuthPromotionClient(@Value("${services.auth-url:http://localhost:8081}") String authUrl,
                               @Value("${internal.api-secret:}") String internalSecret) {
        // Bounded timeouts so a slow/hung auth can't stall the caller (and, before
        // this was moved out of the DB transaction, hold a connection open).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().baseUrl(authUrl).requestFactory(factory).build();
        this.internalSecret = internalSecret;
    }

    public void promoteToGymOwner(UUID userId) {
        // Fail fast with a clear message rather than sending an empty secret and
        // getting back a 401 that surfaces as a confusing generic error.
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new UpstreamServiceException(
                    "Cannot promote applicant to GYM_OWNER: INTERNAL_API_SECRET is not configured on the gym service");
        }
        try {
            client.post()
                    .uri("/api/internal/users/{id}/promote-gym-owner", userId)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new UpstreamServiceException(
                    "Failed to promote applicant " + userId + " to GYM_OWNER in auth: " + e.getMessage(), e);
        }
    }
}
