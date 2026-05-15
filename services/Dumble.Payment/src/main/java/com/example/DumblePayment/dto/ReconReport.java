package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.ReconciliationRun;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Decision 7.3 — admin-facing reconciliation summary. Returned by
 * {@code GET /admin/payment/recon}.
 */
@Data
@Builder
public class ReconReport {

    private List<RunSummary> recentRuns;

    @Data
    @Builder
    public static class RunSummary {
        private UUID id;
        private Instant startedAt;
        private Instant finishedAt;
        private Instant windowFrom;
        private Instant windowTo;
        private int totalLocal;
        private int totalProvider;
        private int autoResolved;
        private int alerts;
        private String notes;

        public static RunSummary from(ReconciliationRun r) {
            return RunSummary.builder()
                    .id(r.getId())
                    .startedAt(r.getStartedAt())
                    .finishedAt(r.getFinishedAt())
                    .windowFrom(r.getWindowFrom())
                    .windowTo(r.getWindowTo())
                    .totalLocal(r.getTotalLocal())
                    .totalProvider(r.getTotalProvider())
                    .autoResolved(r.getAutoResolved())
                    .alerts(r.getAlerts())
                    .notes(r.getNotes())
                    .build();
        }
    }
}
