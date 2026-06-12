package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.domain.dto.CreateGymRegistrationRequest;
import com.dumble.service.gym.domain.dto.GymRegistrationResponse;
import com.dumble.service.gym.domain.dto.UserResponse;
import com.dumble.service.gym.domain.entity.GymRegistration;
import com.dumble.service.gym.domain.entity.RegistrationBranch;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import com.dumble.service.gym.exception.BadRequestException;
import com.dumble.service.gym.repository.GymRegistrationRepository;
import com.dumble.service.gym.service.GymRegistrationService;
import com.dumble.service.gym.util.TokenExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GymRegistrationServiceImpl implements GymRegistrationService {

    // One application in flight at a time; these are the non-terminal states.
    private static final Set<RegistrationStatus> OPEN_STATES =
            Set.of(RegistrationStatus.PENDING, RegistrationStatus.CHANGES_REQUESTED);

    private final GymRegistrationRepository registrationRepository;
    private final TokenExtractor tokenExtractor;

    @Override
    @Transactional
    public GymRegistrationResponse submit(CreateGymRegistrationRequest request, String token) {
        UserResponse user = tokenExtractor.extractUser(token);

        // Only a participant applies. A GYM_OWNER already has a page (one page
        // per owner), so re-registering is blocked here.
        if (!"PARTICIPANT".equals(user.getUserType())) {
            throw new BadRequestException(
                    "Only participants can register as a gym owner; your account is already "
                            + user.getUserType());
        }
        if (registrationRepository.existsByApplicantIdAndStatusIn(user.getId(), OPEN_STATES)) {
            throw new BadRequestException(
                    "You already have a gym registration in progress; edit it instead of opening a new one");
        }

        GymRegistration registration = new GymRegistration();
        registration.setApplicantId(user.getId());
        registration.setPageName(request.getPageName());
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setNationalIdUrl(request.getNationalIdUrl());
        registration.setCommercialRegisterUrl(request.getCommercialRegisterUrl());
        registration.setTaxCardUrl(request.getTaxCardUrl());
        registration.setApplicantNote(request.getNote());
        request.getBranches().forEach(b -> registration.addBranch(toBranch(b)));

        return GymRegistrationResponse.from(registrationRepository.save(registration));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymRegistrationResponse> listMine(String token) {
        UUID userId = tokenExtractor.extractUser(token).getId();
        return registrationRepository.findByApplicantIdOrderByCreatedAtDesc(userId).stream()
                .map(GymRegistrationResponse::from)
                .toList();
    }

    private RegistrationBranch toBranch(CreateGymRegistrationRequest.BranchInput in) {
        RegistrationBranch b = new RegistrationBranch();
        b.setName(in.getName());
        b.setBio(in.getBio());
        b.setAddress(in.getAddress());
        b.setLat(in.getLat());
        b.setLng(in.getLng());
        b.setGenderType(in.getGenderType());
        b.setEmail(in.getEmail());
        b.setPhone(in.getPhone());
        b.setLicenseId(in.getLicenseId());
        b.setOpenTime(in.getOpenTime());
        b.setCloseTime(in.getCloseTime());
        b.setPremisesProofUrl(in.getPremisesProofUrl());
        b.setOperatingLicenseUrl(in.getOperatingLicenseUrl());
        b.setCivilDefenseUrl(in.getCivilDefenseUrl());
        return b;
    }
}
