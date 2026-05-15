package com.example.DumblePayment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per nightly reconciliation pass (Decisions 7.1–7.3). The rendered
 * summary (totals + counts of mismatches by class) is plumbed into the admin
 * dashboard via {@code GET /admin/payment/recon}.
 */
@Entity
@Table(name = "reconciliation_runs",
        indexes = @Index(name = "ix_recon_started", columnList = "started_at DESC"))
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationRun {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "window_from", nullable = false)
    private Instant windowFrom;

    @Column(name = "window_to", nullable = false)
    private Instant windowTo;

    @Column(name = "total_local", nullable = false)
    private int totalLocal;

    @Column(name = "total_provider", nullable = false)
    private int totalProvider;

    @Column(name = "auto_resolved", nullable = false)
    private int autoResolved;

    @Column(nullable = false)
    private int alerts;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
