package com.dumble.service.gym.service;

import com.dumble.service.gym.domain.dto.AddGymStaffRequest;
import com.dumble.service.gym.domain.dto.StaffResponse;
import com.dumble.service.gym.domain.enumuration.StaffRole;

import java.util.List;
import java.util.UUID;

public interface GymStaffService {
    StaffResponse addGymStaff(UUID gymId, AddGymStaffRequest addGymStaffRequest, String token);

    List<StaffResponse> getGymStaff(UUID gymId);

    void removeGymStaff(UUID gymId, UUID userId, String token);

    boolean isUserHasRole(UUID gymId, UUID userId, StaffRole role);
}
