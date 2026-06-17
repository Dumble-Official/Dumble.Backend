package com.example.DumbleSubscription.client;

import com.example.DumbleSubscription.client.dto.ChargeRequest;
import com.example.DumbleSubscription.client.dto.ChargeResponse;
import com.example.DumbleSubscription.client.dto.PayoutRequest;
import com.example.DumbleSubscription.client.dto.PayoutResponse;
import com.example.DumbleSubscription.client.dto.TokenizeRequest;
import com.example.DumbleSubscription.client.dto.TokenizeResponse;
import com.example.DumbleSubscription.security.SystemTokenSigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PaymentServiceClient {

    private final WebClient client;
    private final SystemTokenSigner signer;

    public PaymentServiceClient(@Qualifier("paymentClient") WebClient client,
                                SystemTokenSigner signer) {
        this.client = client;
        this.signer = signer;
    }

    /** System-context call (Decision 8.4 Class B). Used by checkout + dunning retry. */
    public ChargeResponse charge(String idempotencyKey, ChargeRequest body) {
        return client.post()
                .uri("/api/payment/charges")
                .header("Authorization", "Bearer " + signer.mint("payment"))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ChargeResponse.class)
                .block();
    }

    /** System-context call. Used by cohort payout job. */
    public PayoutResponse payout(String idempotencyKey, PayoutRequest body) {
        return client.post()
                .uri("/api/payment/payouts")
                .header("Authorization", "Bearer " + signer.mint("payment"))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PayoutResponse.class)
                .block();
    }

    /**
     * #5 — register a tokenised card on behalf of a user. The caller (the
     * /me/payment-methods controller) sets {@code body.userId} from the
     * authenticated principal; we vouch for it with a minted system token.
     */
    public TokenizeResponse tokenize(TokenizeRequest body) {
        return client.post()
                .uri("/api/payment/payment-methods/tokenize")
                .header("Authorization", "Bearer " + signer.mint("payment"))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TokenizeResponse.class)
                .block();
    }
}
