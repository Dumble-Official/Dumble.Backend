package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.EntryDenialReason;
import com.example.DumbleSubscription.domain.enums.EntryResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only — see PDF Decision 21.5. */
@Entity
@Table(name = "entry_logs",
       indexes = {
           @Index(name = "ix_entry_log_gym_scanned", columnList = "gym_id,scanned_at"),
           @Index(name = "ix_entry_log_participant", columnList = "participant_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class EntryLog {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID entryTokenId;     // null if scan was for invalid/no token

    @Column(name = "gym_id", nullable = false)
    private UUID gymId;

    @Column(name = "participant_id")
    private UUID participantId;    // null if scan failed to identify

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryResult result;

    @Enumerated(EnumType.STRING)
    private EntryDenialReason denialReason;

    @Column(nullable = false)
    private Instant scannedAt;
}
