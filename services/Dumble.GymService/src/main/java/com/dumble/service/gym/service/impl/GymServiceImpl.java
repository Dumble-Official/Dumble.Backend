package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.util.TokenExtractor;
import com.dumble.service.gym.domain.dto.*;
import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import com.dumble.service.gym.domain.enumuration.StaffRole;
import com.dumble.service.gym.domain.mapper.GymMapper;
import com.dumble.service.gym.domain.specification.GymSpecifications;
import com.dumble.service.gym.exception.BadRequestException;
import com.dumble.service.gym.exception.ResourceNotFoundException;
import com.dumble.service.gym.exception.UnauthorizedAccessException;
import com.dumble.service.gym.repository.AmenityRepository;
import com.dumble.service.gym.repository.GymRepository;
import com.dumble.service.gym.service.GymService;
import com.dumble.service.gym.service.GymStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GymServiceImpl implements GymService {

    private final GymRepository gymRepository;
    private final GymMapper gymMapper;
    private final AmenityRepository amenityRepository;
    private final GymStaffService gymStaffService;
    private final TokenExtractor tokenExtractor;

    @Override
    @Transactional
    public GymResponse createGym(GymCreateRequest request, String token) {

        UserResponse user = tokenExtractor.extractUser(token);

        // Only GYM_OWNER (the human who owns gyms) can create a gym profile.
        // GYM is the role for the gym page itself, not for creating new ones.
        if (!"GYM_OWNER".equals(user.getUserType())) {
            throw new BadRequestException("Only users with the GYM_OWNER role can create a gym profile.");
        }

        Gym gym = gymMapper.toEntity(request);
        gym.setOwnerId(user.getId());
        gym.setStatus(GymStatus.PENDING);
        gym.setIsVerified(false);

        if (request.getAmenityIds() != null && !request.getAmenityIds().isEmpty()) {
            gym.setAmenities(new HashSet<>(amenityRepository.findAllById(request.getAmenityIds())));
        }

        Gym savedGym = gymRepository.save(gym);

        AddGymStaffRequest staffRequest = new AddGymStaffRequest();
        staffRequest.setUserId(user.getId());
        staffRequest.setRole(StaffRole.GYM);
        gymStaffService.addGymStaff(savedGym.getId(), staffRequest, token);

        return gymMapper.toDto(savedGym);
    }

    @Override
    @Transactional
    public GymResponse updateGym(UUID gymId, GymUpdateRequest request, String token) {

        UserResponse user = tokenExtractor.extractUser(token);

        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));

        boolean hasPermission = gymStaffService.isUserHasRole(gymId, user.getId(), StaffRole.GYM)
                || gymStaffService.isUserHasRole(gymId, user.getId(), StaffRole.MODERATOR);

        if (!hasPermission) {
            throw new UnauthorizedAccessException("You do not have permission to update this gym.");
        }

        gymMapper.updateEntityFromDto(request, gym);
        return gymMapper.toDto(gymRepository.save(gym));
    }

    @Override
    @Transactional
    public void deleteGym(UUID gymId, String token) {

        UserResponse user = tokenExtractor.extractUser(token);

        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));

        if (!gym.getOwnerId().equals(user.getId())) {
            throw new UnauthorizedAccessException("Only the owner is authorized to delete this gym.");
        }

        gymRepository.delete(gym);
    }

    @Override
    @Transactional(readOnly = true)
    public GymResponse getGymById(UUID gymId) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));
        return gymMapper.toDto(gym);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GymResponse> getAllGyms(String name, GenderType genderType, Boolean verified, GymStatus status,
            Pageable pageable) {

        Specification<Gym> spec = Specification.allOf(GymSpecifications.hasName(name))
                .and(GymSpecifications.hasGender(genderType))
                .and(GymSpecifications.isVerified(verified))
                .and(GymSpecifications.hasStatus(status));

        return gymRepository.findAll(spec, pageable).map(gymMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GymResponse> findNearbyGyms(Double lat, Double lng, Double distance, Pageable pageable) {
        return gymRepository.findNearbyGyms(lat, lng, distance, pageable).map(gymMapper::toDto);
    }

    @Override
    @Transactional
    public GymResponse verifyGym(UUID gymId, String token) {
        requireAdminOrModerator(token);
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));
        gym.setStatus(GymStatus.ACTIVE);
        gym.setIsVerified(true);
        return gymMapper.toDto(gymRepository.save(gym));
    }

    @Override
    @Transactional
    public GymResponse setGymStatus(UUID gymId, GymStatus status, String token) {
        requireAdminOrModerator(token);
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));
        gym.setStatus(status);
        return gymMapper.toDto(gymRepository.save(gym));
    }

    private void requireAdminOrModerator(String token) {
        UserResponse user = tokenExtractor.extractUser(token);
        if (!"ADMIN".equals(user.getUserType()) && !"MODERATOR".equals(user.getUserType())) {
            throw new UnauthorizedAccessException("Only an admin or moderator can perform this action.");
        }
    }
}
