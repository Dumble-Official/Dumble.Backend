package com.example.DumblePayment.domain;

import com.example.DumblePayment.domain.enums.PaymentMethodKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision 10.1 — Payment never sees raw card numbers. The frontend
 * tokenises directly with Paymob's SDK; we record the opaque handle so
 * Subscription can reuse it for renewals (subject to Decision 2.3 — wallet
 * methods cannot auto-renew).
 */
@Entity
@Table(name = "payment_method_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PaymentMethodToken {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private PaymentMethodKind methodType;

    @Column(length = 120)
    private String label;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(length = 4)
    private String last4;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
