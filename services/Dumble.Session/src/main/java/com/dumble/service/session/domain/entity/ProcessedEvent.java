package com.dumble.service.session.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Dedup row for inbound RabbitMQ events. PK on the AMQP message id so a
 * redelivery (Rabbit's at-least-once contract) for an event we've already
 * processed becomes a no-op instead of a duplicate side-effect (e.g.
 * incrementing currentParticipants twice on the same booking).
 *
 * Cleanup of old rows is deferred — keep them indefinitely until a
 * scheduled cleanup job is added. The row count is bounded by the number
 * of inbound events the service has ever received, which is small for the
 * service's expected volume.
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(length = 255)
    private String messageId;

    @Column(nullable = false, length = 120)
    private String routingKey;

    @Column(nullable = false, length = 80)
    private String consumerType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;
}
