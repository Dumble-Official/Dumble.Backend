package com.dumble.service.gym.service;

import com.dumble.service.gym.domain.dto.CreateGymRegistrationRequest;
import com.dumble.service.gym.domain.dto.GymRegistrationResponse;

import java.util.List;

public interface GymRegistrationService {

    GymRegistrationResponse submit(CreateGymRegistrationRequest request, String token);

    List<GymRegistrationResponse> listMine(String token);
}
