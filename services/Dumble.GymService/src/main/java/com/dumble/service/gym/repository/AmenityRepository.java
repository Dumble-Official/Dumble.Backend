package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.Amenity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {
    boolean existsByNameIgnoreCase(String name);

    List<Amenity> findByNameContainingIgnoreCase(String keyword);

    Page<Amenity> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<Amenity> findByIdIn(List<Long> ids);
}
