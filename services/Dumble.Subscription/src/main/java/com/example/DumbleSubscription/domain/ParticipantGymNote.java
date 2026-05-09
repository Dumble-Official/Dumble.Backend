package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per Subscription PDF Decision 21.7 — gym staff leave free-text notes on
 * individual participants. Visible to all staff at that gym; hidden from the
 * participant. Surfaced on every successful entry scan.
 */
@Entity
@Table(name = "participant_gym_notes",
       indexes = {
           @Index(name = "ix_pgn_gym_participant", columnList = "gym_id,participant_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class ParticipantGymNote {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "gym_id", nullable = false)
    private UUID gymId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(nullable = false, length = 2000)
    private String note;

    @Column(name = "author_staff_user_id", nullable = false)
    private UUID authorStaffUserId;

    @Column(nullable = false)
    private Instant createdAt;
}
