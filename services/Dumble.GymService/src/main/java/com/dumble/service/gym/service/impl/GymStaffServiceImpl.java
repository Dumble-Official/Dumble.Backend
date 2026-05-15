package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.util.TokenExtractor;
import com.dumble.service.gym.domain.dto.AddGymStaffRequest;
import com.dumble.service.gym.domain.dto.StaffResponse;
import com.dumble.service.gym.domain.dto.UserResponse;
import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.entity.GymStaff;
import com.dumble.service.gym.domain.enumuration.StaffRole;
import com.dumble.service.gym.domain.mapper.StaffMapper;
import com.dumble.service.gym.exception.BadRequestException;
import com.dumble.service.gym.exception.DuplicateResourceException;
import com.dumble.service.gym.exception.ResourceNotFoundException;
import com.dumble.service.gym.exception.UnauthorizedAccessException;
import com.dumble.service.gym.repository.GymRepository;
import com.dumble.service.gym.repository.GymStaffRepository;
import com.dumble.service.gym.service.GymStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GymStaffServiceImpl implements GymStaffService {

    private final GymStaffRepository gymStaffRepository;
    private final GymRepository gymRepository;
    private final StaffMapper staffMapper;
    private final TokenExtractor tokenExtractor;


    @Override
    @Transactional
    public StaffResponse addGymStaff(UUID gymId, AddGymStaffRequest request, String token) {

        UserResponse currentUser = tokenExtractor.extractUser(token);
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));

        if (!gym.getOwnerId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("Only the gym owner can add staff members.");
        }

        if (request.getRole() == StaffRole.GYM) {
            boolean hasOwner = gymStaffRepository.findByGymId(gymId).stream()
                    .anyMatch(s -> s.getRole() == StaffRole.GYM);
            if (hasOwner) {
                throw new BadRequestException("Cannot add another owner manually.");
            }
        }

        gymStaffRepository.findByGymIdAndUserId(gymId, request.getUserId())
                .ifPresent(s -> {
                    throw new DuplicateResourceException("User is already a staff member in this gym.");
                });

        GymStaff gymStaff = new GymStaff();
        gymStaff.setGym(gym);
        gymStaff.setUserId(request.getUserId());
        gymStaff.setRole(request.getRole());

        return staffMapper.toResponse(gymStaffRepository.save(gymStaff));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getGymStaff(UUID gymId) {
        return gymStaffRepository.findByGymId(gymId).stream()
                .map(staffMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeGymStaff(UUID gymId, UUID userId,String token) {

        UserResponse currentUser = tokenExtractor.extractUser(token);

        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found"));

        if (!gym.getOwnerId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("Only the gym owner can remove staff members.");
        }

        GymStaff staff = gymStaffRepository.findByGymIdAndUserId(gymId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with userId: " + userId));

        if (staff.getRole() == StaffRole.GYM) {
            throw new BadRequestException("Security Risk: Cannot remove the gym owner from the staff list.");
        }

        gymStaffRepository.deleteByGymIdAndUserId(gymId, userId);
    }

    @Override
    public boolean isUserHasRole(UUID gymId, UUID userId, StaffRole role) {
        Optional<GymStaff> staff = gymStaffRepository.findByGymIdAndUserId(gymId, userId);

        if (staff.isEmpty()){
            return false;
        }
        if (staff.get().getRole() == role) {
            return true;
        }
        else {
            return false;
        }
    }
}
