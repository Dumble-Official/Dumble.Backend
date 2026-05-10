package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Inbound HTTP webhook idempotency. Prevents double-processing when Payment /
 * Authentication retry their webhook delivery to {@code /webhooks/...}.
 */
@Entity
@Table(name = "webhook_events_inbound")
@Getter
@Setter
@NoArgsConstructor
public class InboundWebhookEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(length = 2000)
    private String payloadSummary;
}
