package com.example.DumbleWallet.client;

import com.example.DumbleWallet.client.dto.PaymentWithdrawalRequest;
import com.example.DumbleWallet.client.dto.PaymentWithdrawalResponse;
import com.example.DumbleWallet.security.SystemTokenSigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wallet PDF Decision 1.3 — Wallet does not integrate with Paymob. When a
 * user-initiated withdrawal needs to move money to a bank, Wallet calls
 * {@code POST /api/payment/withdrawals}; Payment handles Paymob mechanics
 * and emits {@code WithdrawalCompleted} / {@code WithdrawalFailed} so Wallet
 * can finalise the ledger.
 */
@Component
public class PaymentServiceClient {

    private final WebClient client;
    private final SystemTokenSigner signer;

    public PaymentServiceClient(@Qualifier("paymentClient") WebClient client,
                                SystemTokenSigner signer) {
        this.client = client;
        this.signer = signer;
    }

    public PaymentWithdrawalResponse requestWithdrawal(String idempotencyKey, PaymentWithdrawalRequest body) {
        return client.post()
                .uri("/api/payment/withdrawals")
                .header("Authorization", "Bearer " + signer.mint("payment"))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PaymentWithdrawalResponse.class)
                .block();
    }

    public void cancelWithdrawal(String paymentRef) {
        client.post()
                .uri("/api/payment/withdrawals/{id}/cancel", paymentRef)
                .header("Authorization", "Bearer " + signer.mint("payment"))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
