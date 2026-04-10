package com.dumble.service.gym.service;

import com.dumble.service.gym.domain.dto.AmenityDto;
import com.dumble.service.gym.domain.entity.Amenity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface AmenityService {

    AmenityDto createAmenity(AmenityDto request, String token);
    AmenityDto updateAmenity(Long amenityId, AmenityDto request, String token);
    void deleteAmenity(Long amenityId, String token);
    Page<AmenityDto> getAllAmenities(Pageable pageable);
    AmenityDto getAmenityById(Long amenityId);
    Page<AmenityDto> searchAmenities(String keyword, Pageable pageable);
    AmenityDto toggleAmenityStatus(Long amenityId, String token);

}
