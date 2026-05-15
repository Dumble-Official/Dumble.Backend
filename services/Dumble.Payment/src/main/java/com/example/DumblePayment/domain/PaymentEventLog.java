package com.example.DumblePayment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log for forensic reconstruction during disputes —
 * admin reads, manual interventions, lifecycle transitions outside the
 * normal flow. Distinct from {@link OutboxEvent}; never UPDATE / DELETE.
 */
@Entity
@Table(name = "payment_event_log",
        indexes = @Index(name = "ix_payment_log_subject_ts",
                columnList = "subject_type, subject_id, timestamp DESC"))
@Getter
@Setter
@NoArgsConstructor
public class PaymentEventLog {

    @Id
    @GeneratedValue
    private UUID id;

    /** CHARGE | REFUND | PAYOUT | WEBHOOK | RECON. */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false)
    private Instant timestamp;

    /** USER | ADMIN | SYSTEM | WEBHOOK | PROVIDER. */
    @Column(nullable = false, length = 20)
    private String actor;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(length = 500)
    private String reason;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
}
