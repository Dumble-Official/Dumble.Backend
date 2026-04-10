package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.GymImage;
import com.dumble.service.gym.domain.enumuration.GymImageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


public interface GymImageRepository extends JpaRepository<GymImage, Long> {

    List<GymImage> findByGymId(UUID gymId);

    List<GymImage> findByGymIdAndType(UUID gymId, GymImageType type);

    void deleteByGymId(UUID gymId);
}
