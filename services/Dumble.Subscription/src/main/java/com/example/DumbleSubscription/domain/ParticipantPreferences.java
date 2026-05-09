package com.example.DumbleSubscription.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Per Subscription PDF Decision 10.1 — opt-out from gym subscriber lists. */
@Entity
@Table(name = "participant_preferences")
@Getter
@Setter
@NoArgsConstructor
public class ParticipantPreferences {

    @Id
    @Column(name = "participant_id")
    private UUID participantId;

    @Column(nullable = false)
    private boolean hideFromGymLists;

    @Column(nullable = false)
    private Instant updatedAt;
}
