package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.GymDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GymDocumentRepository extends JpaRepository<GymDocument, Long> {
    List<GymDocument> findByGymId(UUID gymId);
    void deleteByGymId(UUID gymId);
}
