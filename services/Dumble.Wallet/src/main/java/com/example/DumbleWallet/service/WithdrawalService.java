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
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    private final boolean sandbox;

    public WithdrawalService(WithdrawalPersister persister,
                             WithdrawalRequestRepository withdrawalRequestRepository,
                             PaymentServiceClient paymentServiceClient,
                             ObjectMapper objectMapper,
                             @Value("${wallet.withdrawal.minimum-cents:5000}") long minimumCents,
                             @Value("${wallet.withdrawal.sandbox:false}") boolean sandbox) {
        this.persister = persister;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.objectMapper = objectMapper;
        this.minimumCents = minimumCents;
        this.sandbox = sandbox;
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

        // Phase 1b — flip PENDING → SUBMITTING in its own short tx so the
        // cancel endpoint can no longer race the in-flight HTTP call. If a
        // concurrent cancel slipped in between Phase 1 and now, the row is
        // CANCELLED already and we abort without ever calling Payment.
        if (!persister.tryMarkSubmitting(claimed.getId())) {
            log.info("Withdrawal {} cancelled before Payment dispatch", claimed.getId());
            return WithdrawalResponse.from(
                    withdrawalRequestRepository.findById(claimed.getId()).orElseThrow());
        }

        // Sandbox: Paymob disbursement isn't provisioned, so complete the
        // withdrawal locally right after debiting — the money-out equivalent of
        // the Accept charge sandbox. No external payout, no Paymob webhook.
        if (sandbox) {
            log.info("Sandbox payout: completing withdrawal {} without an external disbursement",
                    claimed.getId());
            persister.completeFromWebhook(claimed.getId(), "sandbox-" + claimed.getId(),
                    reload(claimed.getId()));
            return WithdrawalResponse.from(reload(claimed.getId()));
        }

        // Phase 2 — HTTP to Payment outside any tx.
        //
        // Failure-mode taxonomy:
        //   1. Definitive failure (Payment rejected): WebClientResponseException 4xx,
        //      or a Payment 200 body with status="Failed". Safe to reverse locally.
        //   2. Indeterminate (Payment may have processed): 5xx, read timeout, mid-
        //      response RST, generic RuntimeException. We DO NOT reverse — Payment
        //      may have queued the payout and a Paymob webhook will arrive later.
        //      Leaving the row in SUBMITTING lets WithdrawalReaperJob resolve via
        //      Payment's authoritative /by-caller-ref lookup after the grace
        //      window. Eagerly reversing here risks double-pay (wallet credited
        //      back AND Paymob also pays out the bank).
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
                // Empty body on a 2xx — treat as indeterminate; the row stays
                // SUBMITTING for the reaper to resolve.
                log.warn("Payment.requestWithdrawal returned null body for {} — leaving SUBMITTING", claimed.getId());
                return WithdrawalResponse.from(reload(claimed.getId()));
            }
            String status = paymentResponse.getStatus();
            if ("Failed".equalsIgnoreCase(status)) {
                String reason = paymentResponse.getFailureReason() == null
                        ? "payment_failed" : paymentResponse.getFailureReason();
                return WithdrawalResponse.from(persister.reverseAndFail(claimed.getId(), reason));
            }
            return WithdrawalResponse.from(persister.markSent(claimed.getId(), paymentResponse.getWithdrawalId()));
        } catch (WebClientResponseException ex) {
            HttpStatusCode code = ex.getStatusCode();
            if (code != null && code.is4xxClientError()) {
                // Payment definitively rejected (validation error, auth failure,
                // missing token, etc.) — reverse the wallet movement.
                log.warn("Payment.requestWithdrawal returned {} for {} (definitive): {}",
                        code, claimed.getId(), truncate(ex.getResponseBodyAsString()));
                return WithdrawalResponse.from(persister.reverseAndFail(claimed.getId(), "payment_rejected"));
            }
            // 5xx — Payment may or may not have queued it. Indeterminate.
            log.warn("Payment.requestWithdrawal returned {} for {} (indeterminate) — leaving SUBMITTING for reaper",
                    code, claimed.getId());
            return WithdrawalResponse.from(reload(claimed.getId()));
        } catch (RuntimeException ex) {
            // Connect-phase / timeout / unknown. We can't tell from here whether
            // Payment received the request body, so the safe move is to leave
            // SUBMITTING and let the reaper consult Payment's authoritative state.
            log.warn("Payment.requestWithdrawal threw for {} (indeterminate) — leaving SUBMITTING for reaper: {}",
                    claimed.getId(), ex.toString());
            return WithdrawalResponse.from(reload(claimed.getId()));
        }
    }

    private WithdrawalRequest reload(UUID id) {
        return withdrawalRequestRepository.findById(id).orElseThrow();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200);
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
        if (w.getStatus() != WithdrawalStatus.PENDING
                && w.getStatus() != WithdrawalStatus.SUBMITTING
                && w.getStatus() != WithdrawalStatus.SENT) {
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
