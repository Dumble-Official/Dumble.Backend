package com.dumble.service.gym.service;

import com.dumble.service.gym.domain.dto.CreateGymRegistrationRequest;
import com.dumble.service.gym.domain.dto.GymRegistrationResponse;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface GymRegistrationService {

    // ── Applicant ──
    GymRegistrationResponse submit(CreateGymRegistrationRequest request, String token);

    List<GymRegistrationResponse> listMine(String token);

    GymRegistrationResponse editMine(String token, UUID registrationId, CreateGymRegistrationRequest request);

    // ── Admin ──
    Page<GymRegistrationResponse> listForAdmin(String token, RegistrationStatus status, Pageable pageable);

    GymRegistrationResponse approve(String token, UUID registrationId);

    GymRegistrationResponse requestChanges(String token, UUID registrationId, String message);

    GymRegistrationResponse reject(String token, UUID registrationId, String message);
}
