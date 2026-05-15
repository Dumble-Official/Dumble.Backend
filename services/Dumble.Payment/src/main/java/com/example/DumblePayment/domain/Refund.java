package com.example.DumblePayment.domain;

import com.example.DumblePayment.domain.enums.RefundDestination;
import com.example.DumblePayment.domain.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds",
        indexes = {
                @Index(name = "ix_refund_charge", columnList = "charge_id"),
                @Index(name = "ix_refund_provider_ref", columnList = "provider_ref")
        })
@Getter
@Setter
@NoArgsConstructor
public class Refund {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "charge_id", nullable = false)
    private UUID chargeId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    /**
     * Decision 5.2 — most v1 callers pass {@code WALLET}, where Payment
     * skips Paymob and the caller credits the user wallet separately. The
     * {@code ORIGINAL_METHOD} path is reserved for chargebacks and admin
     * force-refunds.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundDestination destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "initiated_by", length = 60)
    private String initiatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
