package com.example.DumblePayment.domain;

import com.example.DumblePayment.domain.enums.PayoutStatus;
import com.example.DumblePayment.domain.enums.PayoutType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Single table for both money-out flows (Decisions 6.1, 6.2). The
 * {@link PayoutType} tag distinguishes Wallet's user-initiated withdrawals
 * from Subscription's system-initiated cohort payouts. Lifecycle is shared
 * (PENDING → SENT → COMPLETED|FAILED, all driven by Paymob webhooks per
 * Decision 6.3); only the routing of outbound events differs.
 *
 * {@code subjectId} carries the user id for withdrawals and the seller id
 * for cohort payouts. {@code callerReference} carries Wallet's withdrawal
 * id or Subscription's escrow batch id — used by the {@code by-caller-ref}
 * lookup endpoint that backs Wallet's reaper.
 */
@Entity
@Table(name = "payouts",
        indexes = {
                @Index(name = "ix_payout_caller_ref", columnList = "caller_reference"),
                @Index(name = "ix_payout_subject", columnList = "subject_id, created_at DESC"),
                @Index(name = "ix_payout_provider_ref", columnList = "provider_ref"),
                @Index(name = "ix_payout_status_created", columnList = "status, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Payout {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutType type;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "destination_json", nullable = false, columnDefinition = "TEXT")
    private String destinationJson;

    @Column(name = "destination_type", length = 30)
    private String destinationType;

    @Column(name = "caller_reference", nullable = false, length = 255)
    private String callerReference;

    /** Cohort payouts only (Decision 6.2). */
    @Column(name = "cohort_key", length = 20)
    private String cohortKey;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
