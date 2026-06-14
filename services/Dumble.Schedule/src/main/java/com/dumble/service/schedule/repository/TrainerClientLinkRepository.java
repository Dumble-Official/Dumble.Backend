package com.dumble.service.schedule.repository;

import com.dumble.service.schedule.domain.TrainerClientLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TrainerClientLinkRepository extends JpaRepository<TrainerClientLink, UUID> {

    Optional<TrainerClientLink> findByTrainerIdAndClientId(UUID trainerId, UUID clientId);

    boolean existsByTrainerIdAndClientIdAndActiveTrue(UUID trainerId, UUID clientId);
}
