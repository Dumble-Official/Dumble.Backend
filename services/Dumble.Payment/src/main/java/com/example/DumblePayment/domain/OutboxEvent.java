package com.example.DumblePayment.domain;

import com.example.DumblePayment.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Decision 9.4 — outbox pattern. Event written in the same DB tx as the
 * state change; a background worker drains to RabbitMQ and marks
 * {@code PUBLISHED} only on broker ACK.
 */
@Entity
@Table(name = "outbox_events",
        indexes = @Index(name = "ix_outbox_status_created", columnList = "status, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 120)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
