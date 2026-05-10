package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decisions 11.5 + 11.6. Bilingual (English + Arabic)
 * receipts owned by Subscription, delivered by NotificationService.
 */
@Entity
@Table(name = "receipts",
       indexes = {
           @Index(name = "ix_receipt_user", columnList = "user_id,issued_at")
       })
@Getter
@Setter
@NoArgsConstructor
public class Receipt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Reference to a Subscription / BundleSubscription / payment id. */
    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    /** JSON array of line items (description, qty, unit price, total). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(nullable = false)
    private Instant issuedAt;

    /** Optional pointer to the Subscription that triggered the receipt. */
    @Column(name = "subject_subscription_id")
    private UUID subjectSubscriptionId;

    @Column(name = "subject_type", length = 20)
    private String subjectType;        // BUNDLE | PLATFORM
}
