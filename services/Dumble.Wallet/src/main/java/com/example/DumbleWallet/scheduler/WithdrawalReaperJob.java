package com.example.DumbleWallet.scheduler;

import com.example.DumbleWallet.client.PaymentServiceClient;
import com.example.DumbleWallet.client.dto.PaymentWithdrawalLookupResponse;
import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import com.example.DumbleWallet.repository.WithdrawalRequestRepository;
import com.example.DumbleWallet.service.WithdrawalPersister;
import com.example.DumbleWallet.service.WithdrawalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Recovers withdrawals stuck before they reach a terminal state. After a
 * crash mid-flow, a partial Payment HTTP failure, or a lost
 * {@code WithdrawalCompleted}/{@code WithdrawalFailed} webhook, a row sits
 * in {@code PENDING}, {@code SUBMITTING}, or {@code SENT} with the user's
 * balance already deducted and no automated progression. This job queries
 * Payment by {@code callerReference} for each stuck row and either advances
 * it ({@code Sent}/{@code Completed}/{@code Failed}) or reverses the wallet
 * movement when Payment never received the request.
 *
 * Runs every minute; only acts on rows older than the configurable
 * {@code wallet.withdrawal.reaper.grace-seconds} (default 600 = 10 min) so a
 * legitimately in-flight HTTP call isn't second-guessed mid-roundtrip. No-op
 * branches (Payment says Pending, or already in sync) bump {@code updatedAt}
 * so the grace-window query drops the row for another tick instead of
 * polling Payment every minute.
 *
 * Two switches gate this bean (both must be "true"):
 * <ul>
 *   <li>{@code wallet.scheduling.enabled} — master switch for all schedulers
 *   <li>{@code wallet.withdrawal.reaper.enabled} — per-feature; flip OFF
 *       until Payment ships its {@code /by-caller-ref} lookup endpoint,
 *       otherwise the reaper would see "NotFound" every tick and start
 *       reversing stuck rows aggressively.
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = {"wallet.scheduling.enabled", "wallet.withdrawal.reaper.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class WithdrawalReaperJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalReaperJob.class);

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalPersister persister;
    private final WithdrawalService withdrawalService;
    private final PaymentServiceClient paymentServiceClient;
    private final long graceSeconds;
    private final boolean sandbox;

    public WithdrawalReaperJob(WithdrawalRequestRepository withdrawalRequestRepository,
                               WithdrawalPersister persister,
                               WithdrawalService withdrawalService,
                               PaymentServiceClient paymentServiceClient,
                               @Value("${wallet.withdrawal.reaper.grace-seconds:600}") long graceSeconds,
                               @Value("${wallet.withdrawal.sandbox:false}") boolean sandbox) {
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.persister = persister;
        this.withdrawalService = withdrawalService;
        this.paymentServiceClient = paymentServiceClient;
        this.graceSeconds = graceSeconds;
        this.sandbox = sandbox;
    }

    @Scheduled(fixedDelayString = "${wallet.withdrawal.reaper.delay-ms:60000}")
    public void run() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(graceSeconds));
        List<WithdrawalRequest> stuck = withdrawalRequestRepository.findStuckBefore(cutoff);
        if (stuck.isEmpty()) return;

        log.info("WithdrawalReaper: {} stuck row(s) older than {}s", stuck.size(), graceSeconds);

        for (WithdrawalRequest w : stuck) {
            try {
                reapOne(w);
            } catch (RuntimeException ex) {
                log.error("WithdrawalReaper: failed to reap {} (status={})", w.getId(), w.getStatus(), ex);
            }
        }
    }

    private void reapOne(WithdrawalRequest w) {
        // Sandbox: there's no real payout to reconcile against Payment, so just
        // finish any stuck row (e.g. one created before the sandbox was enabled).
        if (sandbox) {
            log.info("WithdrawalReaper (sandbox): completing stuck withdrawal {}", w.getId());
            withdrawalService.onWithdrawalCompleted(w.getId(), "sandbox-reaped-" + w.getId());
            return;
        }

        PaymentWithdrawalLookupResponse lookup;
        try {
            lookup = paymentServiceClient.lookupByCallerReference(w.getId().toString());
        } catch (RuntimeException ex) {
            // Payment unreachable — leave the row alone for the next tick. We
            // deliberately DON'T touch updatedAt here so the row stays in the
            // grace-window query and gets retried as soon as Payment is back.
            log.warn("WithdrawalReaper: Payment lookup failed for {}; leaving stuck", w.getId(), ex);
            return;
        }

        String status = lookup == null ? "NotFound" : lookup.getStatus();

        if ("NotFound".equalsIgnoreCase(status)) {
            if (w.getStatus() == WithdrawalStatus.SENT) {
                // We hold a paymentRef from the original ACK but Payment now
                // says it doesn't know this caller-ref. Most likely a data
                // mismatch on Payment's side — refuse to reverse a withdrawal
                // we know we successfully dispatched. Bump updatedAt so we
                // don't spam this row, and let admin investigate.
                log.error("WithdrawalReaper: {} is SENT locally but NotFound at Payment — admin investigate (paymentRef={})",
                        w.getId(), w.getPaymentRef());
                persister.touch(w.getId());
            } else {
                // PENDING / SUBMITTING — Payment never received it. Reverse so
                // the user gets their balance back; they can re-issue under a
                // fresh Idempotency-Key.
                log.info("WithdrawalReaper: {} not found at Payment — reversing", w.getId());
                persister.reverseAndFail(w.getId(), "payment_lookup_not_found");
            }
            return;
        }

        if ("Pending".equalsIgnoreCase(status)) {
            // Payment received it and is still working. Bump updatedAt so the
            // grace-window query drops this row for another tick — otherwise
            // we'd hammer Payment every 60s for a slow withdrawal.
            log.debug("WithdrawalReaper: {} still pending at Payment", w.getId());
            persister.touch(w.getId());
            return;
        }
        if ("Sent".equalsIgnoreCase(status)) {
            if (w.getStatus() == WithdrawalStatus.SENT) {
                // Already in sync — Payment dispatched and we recorded SENT.
                // The webhook just hasn't fired (yet). Bump updatedAt to drop
                // out of the grace window for another tick.
                persister.touch(w.getId());
            } else {
                // PENDING / SUBMITTING locally but Payment dispatched. Advance.
                persister.markSent(w.getId(), lookup.getWithdrawalId());
            }
            return;
        }
        if ("Completed".equalsIgnoreCase(status)) {
            // Webhook never reached us. Apply the COMPLETED transition now.
            withdrawalService.onWithdrawalCompleted(w.getId(), lookup.getWithdrawalId());
            return;
        }
        if ("Failed".equalsIgnoreCase(status)) {
            String reason = lookup.getFailureReason() == null ? "payment_failed_recovered" : lookup.getFailureReason();
            persister.reverseAndFail(w.getId(), reason);
            return;
        }
        log.warn("WithdrawalReaper: {} returned unknown Payment status '{}' — leaving stuck",
                w.getId(), status);
        // Bump updatedAt so an unrecognised status from Payment doesn't loop
        // every minute. Admin can investigate from the warn log.
        persister.touch(w.getId());
    }
}
