package com.example.DumbleWallet.service;

import com.example.DumbleWallet.client.PaymentServiceClient;
import com.example.DumbleWallet.client.dto.PaymentWithdrawalRequest;
import com.example.DumbleWallet.client.dto.PaymentWithdrawalResponse;
import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import com.example.DumbleWallet.dto.WithdrawalRequestBody;
import com.example.DumbleWallet.dto.WithdrawalResponse;
import com.example.DumbleWallet.exception.BusinessRuleViolationException;
import com.example.DumbleWallet.repository.WithdrawalRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Wallet PDF Section 4 — manual user-initiated withdrawals.
 *
 * Lifecycle (Decision 4.2): PENDING → SENT → COMPLETED | FAILED, plus
 * CANCELLED for the user-initiated cancel allowed only while still PENDING
 * (Decision 4.3).
 *
 * Money flow:
 *   Request →   DEBIT entry source=WITHDRAWAL_REQUESTED, available -= W, pending += W.
 *   Complete →  no new ledger entry, pending -= W.
 *   Fail →      CREDIT entry source=WITHDRAWAL_REVERSED, available += W, pending -= W.
 *   Cancel →    same shape as Fail.
 *
 * Decision 1.3 — Wallet does not integrate with Paymob. Withdrawal execution
 * is delegated to Payment via {@link PaymentServiceClient}, and Wallet learns
 * the outcome via {@code WithdrawalCompleted} / {@code WithdrawalFailed}
 * events (Decision 6.2).
 *
 * Orchestration: the public {@code requestWithdrawal} method is NOT
 * {@code @Transactional} so the HTTP call to Payment doesn't hold a JPA
 * connection for the WebClient round-trip budget. Each DB step is a short
 * transaction inside {@link WithdrawalPersister}.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final WithdrawalPersister persister;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final ObjectMapper objectMapper;
    private final long minimumCents;

    public WithdrawalService(WithdrawalPersister persister,
                             WithdrawalRequestRepository withdrawalRequestRepository,
                             PaymentServiceClient paymentServiceClient,
                             ObjectMapper objectMapper,
                             @Value("${wallet.withdrawal.minimum-cents:5000}") long minimumCents) {
        this.persister = persister;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.objectMapper = objectMapper;
        this.minimumCents = minimumCents;
    }

    public WithdrawalResponse requestWithdrawal(UUID userId, WithdrawalRequestBody body, String idempotencyKey) {
        if (body.getAmountCents() < minimumCents) {
            // Decision 4.5 — below the per-environment minimum.
            throw new BusinessRuleViolationException(
                    "Withdrawal below minimum (" + minimumCents + " cents)");
        }
        String destinationJson = serializeDestination(body);

        // Phase 1 — claim balance + persist PENDING row in a short tx.
        WithdrawalRequest claimed = persister.claimBalanceAndPersist(userId, body.getAmountCents(), destinationJson);

        // Phase 2 — HTTP to Payment outside any tx.
        try {
            PaymentWithdrawalResponse paymentResponse = paymentServiceClient.requestWithdrawal(
                    idempotencyKey != null && !idempotencyKey.isBlank()
                            ? idempotencyKey
                            : "withdrawal-" + claimed.getId(),
                    PaymentWithdrawalRequest.builder()
                            .userId(userId)
                            .amountCents(claimed.getAmountCents())
                            .currency(claimed.getCurrency())
                            .destination(body.getDestination())
                            .callerReference(claimed.getId().toString())
                            .build());
            if (paymentResponse == null) {
                return WithdrawalResponse.from(persister.reverseAndFail(claimed.getId(), "payment_no_response"));
            }
            String status = paymentResponse.getStatus();
            if ("Failed".equalsIgnoreCase(status)) {
                String reason = paymentResponse.getFailureReason() == null
                        ? "payment_failed" : paymentResponse.getFailureReason();
                return WithdrawalResponse.from(persister.reverseAndFail(claimed.getId(), reason));
            }
            return WithdrawalResponse.from(persister.markSent(claimed.getId(), paymentResponse.getWithdrawalId()));
        } catch (RuntimeException ex) {
            log.error("Payment.requestWithdrawal threw for withdrawal {}", claimed.getId(), ex);
            return WithdrawalResponse.from(persister.reverseAndFail(claimed.getId(), "payment_error"));
        }
    }

    public WithdrawalResponse cancel(UUID userId, UUID withdrawalId) {
        return WithdrawalResponse.from(persister.cancel(userId, withdrawalId));
    }

    /** Decision 6.2 — Payment confirmed Paymob delivered the funds. */
    public void onWithdrawalCompleted(UUID withdrawalId, String paymentRef) {
        WithdrawalRequest w = locate(withdrawalId, paymentRef);
        if (w == null) {
            log.warn("WithdrawalCompleted matched no request (id={}, paymentRef={})", withdrawalId, paymentRef);
            return;
        }
        if (w.getStatus() != WithdrawalStatus.PENDING && w.getStatus() != WithdrawalStatus.SENT) {
            log.warn("WithdrawalCompleted on withdrawal {} in unexpected status {}", w.getId(), w.getStatus());
            return;
        }
        persister.completeFromWebhook(w.getId(), paymentRef, w);
    }

    /** Decision 4.4 + 6.2 — Paymob rejected; reverse the wallet movement. */
    public void onWithdrawalFailed(UUID withdrawalId, String paymentRef, String reason) {
        WithdrawalRequest w = locate(withdrawalId, paymentRef);
        if (w == null) {
            log.warn("WithdrawalFailed matched no request (id={}, paymentRef={})", withdrawalId, paymentRef);
            return;
        }
        if (w.getStatus() == WithdrawalStatus.FAILED || w.getStatus() == WithdrawalStatus.CANCELLED) {
            return;
        }
        persister.reverseAndFail(w.getId(), reason == null ? "payment_failed" : reason);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> listForUser(UUID userId) {
        return withdrawalRequestRepository.findByWalletUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(WithdrawalResponse::from)
                .toList();
    }

    private WithdrawalRequest locate(UUID withdrawalId, String paymentRef) {
        if (withdrawalId != null) {
            WithdrawalRequest w = withdrawalRequestRepository.findById(withdrawalId).orElse(null);
            if (w != null) return w;
        }
        if (paymentRef != null && !paymentRef.isBlank()) {
            return withdrawalRequestRepository.findByPaymentRef(paymentRef).orElse(null);
        }
        return null;
    }

    private String serializeDestination(WithdrawalRequestBody body) {
        try {
            return objectMapper.writeValueAsString(body.getDestination());
        } catch (JsonProcessingException ex) {
            throw new BusinessRuleViolationException("Invalid destination payload");
        }
    }
}
