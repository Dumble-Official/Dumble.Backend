package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.GymStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GymStaffRepository extends JpaRepository<GymStaff, UUID> {

    List<GymStaff> findByGymId(UUID gymId);

    Optional<GymStaff> findByGymIdAndUserId(UUID gymId, UUID userId);

    void deleteByGymIdAndUserId(UUID gymId, UUID userId);
}
