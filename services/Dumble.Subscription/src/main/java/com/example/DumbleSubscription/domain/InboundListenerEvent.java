package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency for the @RabbitListener path on subscription.inbound. Mirrors
 * webhook_events_inbound but for AMQP — without it, redelivered chargeback
 * messages re-enter handleChargebackFiled and lock another tranche-worth of
 * escrow each time (merged_bug_011, bug 2).
 */
@Entity
@Table(name = "inbound_listener_events")
@Getter
@Setter
@NoArgsConstructor
public class InboundListenerEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "routing_key", nullable = false, length = 120)
    private String routingKey;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "payload_summary", length = 2000)
    private String payloadSummary;
}
