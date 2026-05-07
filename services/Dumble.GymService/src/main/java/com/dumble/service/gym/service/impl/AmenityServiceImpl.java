package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.util.TokenExtractor;
import com.dumble.service.gym.domain.dto.AmenityDto;
import com.dumble.service.gym.domain.dto.UserResponse;
import com.dumble.service.gym.domain.entity.Amenity;
import com.dumble.service.gym.domain.mapper.AmenityMapper;
import com.dumble.service.gym.exception.DuplicateResourceException;
import com.dumble.service.gym.exception.ResourceNotFoundException;
import com.dumble.service.gym.exception.UnauthorizedAccessException;
import com.dumble.service.gym.repository.AmenityRepository;
import com.dumble.service.gym.service.AmenityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final AmenityMapper amenityMapper;
    private final TokenExtractor tokenExtractor;


    private void validateAdmin(String token) {
        UserResponse user = tokenExtractor.extractUser(token);
        if (!"ADMIN".equals(user.getUserType())) {
            throw new UnauthorizedAccessException("Only system admins can manage amenities.");
        }
    }

    @Override
    @Transactional
    public AmenityDto createAmenity(AmenityDto request, String token) {
        validateAdmin(token);

        if(amenityRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Amenity with name '" + request.getName() + "' already exists.");
        }
        Amenity amenity = amenityMapper.toEntity(request);
        amenity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        return amenityMapper.toDto(amenityRepository.save(amenity));
    }

    @Override
    @Transactional
    public AmenityDto updateAmenity(Long amenityId, AmenityDto request, String token) {
        validateAdmin(token);

        Amenity amenity = amenityRepository.findById(amenityId)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found with id: " + amenityId));

        if(!amenity.getName().equalsIgnoreCase(request.getName())
                && amenityRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Amenity with name '" + request.getName() + "' already exists.");
        }

        amenity.setName(request.getName());
        amenity.setDescription(request.getDescription());
        amenity.setIsActive(request.getIsActive());

        return amenityMapper.toDto(amenityRepository.save(amenity));
    }

    @Override
    @Transactional
    public void deleteAmenity(Long amenityId, String token) {
        validateAdmin(token);

        Amenity amenity = amenityRepository.findById(amenityId)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found with id: " + amenityId));

        amenityRepository.delete(amenity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AmenityDto> getAllAmenities(Pageable pageable) {
        return amenityRepository.findAll(pageable)
                .map(amenityMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AmenityDto getAmenityById(Long amenityId) {
        return amenityRepository.findById(amenityId)
                .map(amenityMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found with id: " + amenityId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AmenityDto> searchAmenities(String keyword, Pageable pageable){
    Page<Amenity> amenities = amenityRepository.findByNameContainingIgnoreCase(keyword, pageable);
    return amenities.map(amenityMapper::toDto);
}

    @Override
    public AmenityDto toggleAmenityStatus(Long amenityId, String token) {
        validateAdmin(token);

    Amenity amenity = amenityRepository.findById(amenityId)
            .orElseThrow(() -> new ResourceNotFoundException("Amenity not found with id: " + amenityId));
    amenity.setIsActive(!amenity.getIsActive());
    return amenityMapper.toDto(amenityRepository.save(amenity));
}
}
