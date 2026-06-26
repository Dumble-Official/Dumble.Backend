package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.Gym;
import feign.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;


public interface GymRepository extends JpaRepository<Gym, UUID>, JpaSpecificationExecutor<Gym> {

    /** The owner's primary (oldest) gym, used to resolve the managed gym from an approved registration. */
    Optional<Gym> findFirstByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @Query(value = "SELECT * FROM gyms g WHERE " +
            "g.is_verified = true AND g.status = 'ACTIVE' AND " +
            "(6371 * acos(cos(radians(:lat)) * cos(radians(g.latitude)) * " +
            "cos(radians(g.longitude) - radians(:lng)) + sin(radians(:lat)) * " +
            "sin(radians(g.latitude)))) <= :distance",
            nativeQuery = true)
    Page<Gym> findNearbyGyms(@Param("lat") Double lat,
                             @Param("lng") Double lng,
                             @Param("distance") Double distance,
                             Pageable pageable);
}
