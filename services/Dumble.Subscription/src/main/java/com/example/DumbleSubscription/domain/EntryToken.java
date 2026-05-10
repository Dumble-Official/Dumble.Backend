package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.EntryTokenStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decision 21.3. Generated fresh every time the
 * participant opens the entry screen; supersedes prior unused tokens for the
 * same BundleSubscription. Single-use, 5-minute TTL.
 */
@Entity
@Table(name = "entry_tokens",
       indexes = {
           @Index(name = "ix_entry_token_secret", columnList = "token_secret", unique = true),
           @Index(name = "ix_entry_token_subscription", columnList = "bundle_subscription_id"),
           @Index(name = "ix_entry_token_status", columnList = "status")
       })
@Getter
@Setter
@NoArgsConstructor
public class EntryToken {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "bundle_subscription_id", nullable = false)
    private UUID bundleSubscriptionId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "gym_id", nullable = false)
    private UUID gymId;

    /** Opaque random bytes (base64url) — what the QR encodes. */
    @Column(name = "token_secret", nullable = false, length = 64)
    private String tokenSecret;

    @Column(nullable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryTokenStatus status;
}
