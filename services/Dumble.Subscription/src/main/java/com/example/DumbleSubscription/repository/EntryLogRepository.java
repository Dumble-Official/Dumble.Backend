package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.EntryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EntryLogRepository extends JpaRepository<EntryLog, UUID> {
    List<EntryLog> findByGymIdAndScannedAtAfter(UUID gymId, Instant cutoff);
}
