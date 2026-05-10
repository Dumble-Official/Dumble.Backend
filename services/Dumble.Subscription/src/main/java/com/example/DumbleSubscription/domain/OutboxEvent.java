package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decision 8.5. Events are written to this table in the
 * same DB transaction as the state change; a background worker reads PENDING
 * rows and publishes to RabbitMQ, marking PUBLISHED on broker ACK. Guarantees
 * no event for a rolled-back transaction and no lost event for a committed one.
 */
@Entity
@Table(name = "outbox_events",
       indexes = {
           @Index(name = "ix_outbox_status_created", columnList = "status,created_at")
       })
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String eventType;       // e.g. "BundleSubscriptionActivated"

    @Column(nullable = false, length = 100)
    private String routingKey;      // RabbitMQ topic routing key

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;
}
