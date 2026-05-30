package com.example.DumbleAuthentication.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox. An event row is written in the same DB transaction as the state change it
 * describes (e.g. an account deletion); a scheduled worker later publishes PENDING rows to RabbitMQ
 * and marks them PUBLISHED. This guarantees a rolled-back deletion emits nothing, and a committed
 * deletion's event is never lost to a transient broker outage.
 */
@Entity
@Table(name = "outbox_events",
       indexes = {
           @Index(name = "ix_auth_outbox_status_created", columnList = "status,created_at")
       })
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String eventType;       // e.g. "AccountDeleted"

    @Column(nullable = false, length = 100)
    private String routingKey;      // RabbitMQ topic routing key, e.g. "account.deleted"

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
