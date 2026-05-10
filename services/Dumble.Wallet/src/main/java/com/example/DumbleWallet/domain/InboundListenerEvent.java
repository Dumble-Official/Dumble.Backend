package com.example.DumbleWallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency for the {@code @RabbitListener} path on {@code wallet.inbound}.
 * Without this, redelivered {@code WithdrawalCompleted} / {@code WithdrawalFailed}
 * events from Payment would re-process and either double-debit a completed
 * withdrawal or double-credit a reversed one.
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
