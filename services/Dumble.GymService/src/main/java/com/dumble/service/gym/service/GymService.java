package com.dumble.service.gym.service;

import com.dumble.service.gym.domain.dto.GymCreateRequest;
import com.dumble.service.gym.domain.dto.GymResponse;
import com.dumble.service.gym.domain.dto.GymUpdateRequest;
import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;


public interface GymService {

    GymResponse createGym(GymCreateRequest request, String token);
    GymResponse updateGym(UUID gymId, GymUpdateRequest request, String token);
    void deleteGym(UUID gymId, String token);
    GymResponse getGymById(UUID gymId);
    Page<GymResponse> getAllGyms(String name, GenderType genderType, Boolean verified, GymStatus status,Pageable pageable);
    Page<GymResponse> findNearbyGyms(Double lat, Double lng, Double distance, Pageable pageable);
}
