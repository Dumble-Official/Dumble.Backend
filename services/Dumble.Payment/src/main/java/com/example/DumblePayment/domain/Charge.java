package com.example.DumblePayment.domain;

import com.example.DumblePayment.domain.enums.ChargeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment PDF Decision 3.3 — every charge persisted in {@code PENDING}
 * BEFORE the Paymob call. If Paymob succeeds but the response is lost,
 * the row is still here for reconciliation to find.
 */
@Entity
@Table(name = "charges",
        indexes = {
                @Index(name = "ix_charge_user", columnList = "user_id, created_at DESC"),
                @Index(name = "ix_charge_caller_ref", columnList = "caller_reference"),
                @Index(name = "ix_charge_provider_ref", columnList = "provider_ref"),
                @Index(name = "ix_charge_status_created", columnList = "status, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Charge {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method_token", length = 255)
    private String paymentMethodToken;

    @Column(length = 500)
    private String description;

    /**
     * Caller's stable reference (e.g. Subscription's checkout-intent id) —
     * Decision 3.1. Used by reconciliation to map Paymob transactions back
     * to the originating logical operation.
     */
    @Column(name = "caller_reference", length = 255)
    private String callerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status;

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
}
