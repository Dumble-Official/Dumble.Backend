package com.example.DumbleSubscription.client;

import com.example.DumbleSubscription.client.dto.WalletCreditRequest;
import com.example.DumbleSubscription.client.dto.WalletCreditResponse;
import com.example.DumbleSubscription.client.dto.WalletDebitRequest;
import com.example.DumbleSubscription.client.dto.WalletDebitResponse;
import com.example.DumbleSubscription.client.dto.WalletSummaryResponse;
import com.example.DumbleSubscription.security.SystemTokenSigner;
import com.example.DumbleSubscription.security.UserTokenForwarder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Component
public class WalletServiceClient {

    private final WebClient client;
    private final SystemTokenSigner signer;
    private final UserTokenForwarder userTokenForwarder;

    public WalletServiceClient(@Qualifier("walletClient") WebClient client,
                               SystemTokenSigner signer,
                               UserTokenForwarder userTokenForwarder) {
        this.client = client;
        this.signer = signer;
        this.userTokenForwarder = userTokenForwarder;
    }

    /** System-context: refund credits triggered by ban (no user JWT in flight). */
    public WalletCreditResponse credit(String idempotencyKey, WalletCreditRequest body) {
        return client.post()
                .uri("/api/wallet/credit")
                .header("Authorization", "Bearer " + signer.mint("wallet"))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WalletCreditResponse.class)
                .block();
    }

    /** User-context: spend wallet balance at checkout. Forward the inbound JWT. */
    public WalletDebitResponse debit(String idempotencyKey, WalletDebitRequest body) {
        String auth = userTokenForwarder.currentBearer();
        if (auth == null) {
            // Background-thread fallback — should be rare for debit; use system token.
            auth = "Bearer " + signer.mint("wallet");
        }
        return client.post()
                .uri("/api/wallet/debit")
                .header("Authorization", auth)
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(WalletDebitResponse.class)
                .block();
    }

    /** User-context: read summary (balance) — used for checkout gating + dashboard. */
    public WalletSummaryResponse summary(UUID userId) {
        String auth = userTokenForwarder.currentBearer();
        if (auth == null) {
            auth = "Bearer " + signer.mint("wallet");
        }
        return client.get()
                .uri("/api/wallet/{userId}/summary", userId)
                .header("Authorization", auth)
                .retrieve()
                .bodyToMono(WalletSummaryResponse.class)
                .block();
    }
}
