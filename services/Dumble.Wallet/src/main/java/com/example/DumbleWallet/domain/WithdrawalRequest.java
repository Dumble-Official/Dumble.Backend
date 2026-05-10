package com.example.DumbleWallet.domain;

import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet PDF Decisions 4.2 + 4.3 — manual, user-initiated withdrawal.
 *
 * Lifecycle: PENDING → SENT → COMPLETED | FAILED, plus CANCELLED for the
 * user-initiated cancel allowed only while still PENDING.
 *
 * On creation: balance moves Available → Pending; an event is published to
 * Payment which executes the actual transfer via Paymob (Wallet does not
 * integrate with Paymob, Decision 1.3). Wallet learns the outcome via
 * {@code WithdrawalCompleted} / {@code WithdrawalFailed} (Decision 6.2).
 */
@Entity
@Table(name = "withdrawal_requests",
       indexes = {
           @Index(name = "ix_withdrawal_user_created", columnList = "wallet_user_id, created_at DESC"),
           @Index(name = "ix_withdrawal_status", columnList = "status"),
           @Index(name = "ix_withdrawal_payment_ref", columnList = "payment_ref")
       })
@Getter
@Setter
@NoArgsConstructor
public class WithdrawalRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "wallet_user_id", nullable = false)
    private UUID walletUserId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Decision 2.2 — destination is JSON so different rails (bank account,
     * mobile-wallet number, etc.) can carry their own shape without a schema
     * change per rail. Caller is responsible for the contents; Payment service
     * interprets them.
     */
    @Column(name = "destination_json", nullable = false, columnDefinition = "TEXT")
    private String destinationJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status;

    /** Payment service withdrawal id — populated when status flips to SENT or beyond. */
    @Column(name = "payment_ref", length = 255)
    private String paymentRef;

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
