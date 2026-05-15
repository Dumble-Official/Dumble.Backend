package com.example.DumblePayment.scheduler;

import com.example.DumblePayment.domain.Charge;
import com.example.DumblePayment.domain.ReconciliationRun;
import com.example.DumblePayment.event.OutboxWriter;
import com.example.DumblePayment.repository.ChargeRepository;
import com.example.DumblePayment.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Decisions 7.1–7.3 — nightly diff between Paymob's records and ours.
 *
 * v1 ships an internal-only variant: it scans local Charges for stuck-
 * PENDING rows past the grace window, emits {@code ReconciliationDiscrepancy}
 * events for each, and records a run summary. The full Paymob-side fetch
 * loop (Decision 7.2 row 1) is the obvious next step once the SDK wiring
 * lives behind {@link com.example.DumblePayment.provider.IPaymentProvider};
 * extending this job is then a matter of adding one more diff pass.
 */
@Component
@ConditionalOnProperty(
        name = {"payment.scheduling.enabled", "payment.reconciliation.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);
    private static final Duration STUCK_THRESHOLD = Duration.ofHours(1);

    private final ChargeRepository chargeRepository;
    private final ReconciliationRunRepository runRepository;
    private final OutboxWriter outboxWriter;

    public ReconciliationJob(ChargeRepository chargeRepository,
                             ReconciliationRunRepository runRepository,
                             OutboxWriter outboxWriter) {
        this.chargeRepository = chargeRepository;
        this.runRepository = runRepository;
        this.outboxWriter = outboxWriter;
    }

    @Scheduled(cron = "${payment.reconciliation.cron:0 30 3 * * *}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        Instant windowFrom = now.minus(Duration.ofHours(24));
        Instant cutoff = now.minus(STUCK_THRESHOLD);

        ReconciliationRun runRow = new ReconciliationRun();
        runRow.setStartedAt(now);
        runRow.setWindowFrom(windowFrom);
        runRow.setWindowTo(now);

        List<Charge> stuck = chargeRepository.findStuckPending(cutoff);
        int alerts = 0;
        for (Charge c : stuck) {
            log.warn("Reconciliation: Charge {} stuck PENDING since {}", c.getId(), c.getCreatedAt());
            outboxWriter.write("ReconciliationDiscrepancy", "payment.recon.discrepancy",
                    Map.of("kind", "stuck_pending_charge",
                            "chargeId", c.getId(),
                            "userId", c.getUserId(),
                            "amountCents", c.getAmountCents(),
                            "callerReference", c.getCallerReference() == null ? "" : c.getCallerReference(),
                            "createdAt", c.getCreatedAt()));
            alerts++;
        }

        List<Charge> windowed = chargeRepository.findByCreatedAtBetween(windowFrom, now);
        runRow.setTotalLocal(windowed.size());
        runRow.setTotalProvider(0);   // populated when the Paymob fetch lands
        runRow.setAutoResolved(0);
        runRow.setAlerts(alerts);
        runRow.setFinishedAt(Instant.now());
        runRow.setNotes("v1 scope: stuck-PENDING detection only; Paymob fetch loop pending");
        runRepository.save(runRow);

        log.info("Reconciliation: window={}..{} local={} alerts={}",
                windowFrom, now, windowed.size(), alerts);
    }
}
