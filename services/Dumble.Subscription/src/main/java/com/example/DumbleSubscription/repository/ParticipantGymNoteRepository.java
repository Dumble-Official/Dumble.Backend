package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.ParticipantGymNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParticipantGymNoteRepository extends JpaRepository<ParticipantGymNote, UUID> {
    List<ParticipantGymNote> findByGymIdAndParticipantIdOrderByCreatedAtDesc(UUID gymId, UUID participantId);
}
