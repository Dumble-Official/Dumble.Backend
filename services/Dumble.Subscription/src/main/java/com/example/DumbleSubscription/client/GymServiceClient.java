package com.example.DumbleSubscription.client;

import com.example.DumbleSubscription.client.dto.PromoValidationResponse;
import com.example.DumbleSubscription.security.UserTokenForwarder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Component
public class GymServiceClient {

    private final WebClient client;
    private final UserTokenForwarder userTokenForwarder;

    public GymServiceClient(@Qualifier("gymClient") WebClient client,
                            UserTokenForwarder userTokenForwarder) {
        this.client = client;
        this.userTokenForwarder = userTokenForwarder;
    }

    /**
     * Per Subscription PDF Decision 9.3 — validate promo against GymService at
     * checkout. User-context call.
     *
     * Returns null if the GymService promo-code feature isn't yet built (404),
     * letting checkout proceed without applying any discount.
     */
    public PromoValidationResponse validatePromoCode(UUID gymId, String code) {
        try {
            return client.post()
                    .uri("/api/gyms/{gymId}/promo-codes/{code}/validate", gymId, code)
                    .header("Authorization", forwardOrNull())
                    .retrieve()
                    .bodyToMono(PromoValidationResponse.class)
                    .block();
        } catch (WebClientResponseException.NotFound notFound) {
            return null;        // promo feature not deployed yet — fail open
        }
    }

    private String forwardOrNull() {
        String h = userTokenForwarder.currentBearer();
        return h != null ? h : "";
    }
}
