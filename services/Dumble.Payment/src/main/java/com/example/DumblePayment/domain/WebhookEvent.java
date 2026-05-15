package com.example.DumblePayment.domain;

import com.example.DumblePayment.domain.enums.WebhookProcessingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Decisions 4.1 / 4.2 / 4.3 — Paymob inbound. PK is the Paymob event id so
 * a redelivered event (which Paymob does aggressively on transient failures)
 * is rejected at the door before any side-effects run. Two-phase: this row
 * is inserted in a synchronous receive path that ACKs Paymob immediately;
 * the async {@code WebhookProcessingJob} drains rows and applies state
 * changes (Charge / Refund / Payout) + emits domain events.
 */
@Entity
@Table(name = "webhook_events",
        indexes = @Index(name = "ix_webhook_status_received", columnList = "processing_status, received_at"))
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private WebhookProcessingStatus processingStatus;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "processed_at")
    private Instant processedAt;
}
