package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.ParticipantPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParticipantPreferencesRepository extends JpaRepository<ParticipantPreferences, UUID> {
}
