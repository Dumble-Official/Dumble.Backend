package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Section 14 — append-only forensic history. Different
 * from OutboxEvent (which is for publishing to other services). This log is
 * for reconstructing "what happened" during billing disputes.
 */
@Entity
@Table(name = "subscription_event_log",
       indexes = {
           @Index(name = "ix_sublog_subscription_ts", columnList = "subscription_id,timestamp")
       })
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionEventLog {

    @Id
    @GeneratedValue
    private UUID id;

    /** PlatformSubscription.id or BundleSubscription.id. */
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false, length = 60)
    private String eventType;       // Created | Renewed | Cancelled | EscrowReleased | RefundIssued | PaymentFailed | Frozen | Banned | etc.

    @Column(nullable = false)
    private Instant timestamp;

    /** USER | ADMIN | SYSTEM | WEBHOOK */
    @Column(nullable = false, length = 20)
    private String actor;

    /** UUID of admin user, system job name, etc. */
    private String actorId;

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;
}
