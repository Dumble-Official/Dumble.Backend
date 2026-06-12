package com.dumble.service.gym.domain.dto;

import com.dumble.service.gym.domain.entity.GymRegistration;
import com.dumble.service.gym.domain.entity.RegistrationBranch;
import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class GymRegistrationResponse {

    private UUID id;
    private UUID applicantId;
    private String pageName;
    private RegistrationStatus status;
    private String nationalIdUrl;
    private String commercialRegisterUrl;
    private String taxCardUrl;
    private String applicantNote;
    private String adminMessage;
    private List<BranchView> branches;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class BranchView {
        private Long id;
        private String name;
        private String bio;
        private String address;
        private BigDecimal lat;
        private BigDecimal lng;
        private GenderType genderType;
        private String email;
        private String phone;
        private String licenseId;
        private LocalTime openTime;
        private LocalTime closeTime;
        private String premisesProofUrl;
        private String operatingLicenseUrl;
        private String civilDefenseUrl;
    }

    public static GymRegistrationResponse from(GymRegistration r) {
        return GymRegistrationResponse.builder()
                .id(r.getId())
                .applicantId(r.getApplicantId())
                .pageName(r.getPageName())
                .status(r.getStatus())
                .nationalIdUrl(r.getNationalIdUrl())
                .commercialRegisterUrl(r.getCommercialRegisterUrl())
                .taxCardUrl(r.getTaxCardUrl())
                .applicantNote(r.getApplicantNote())
                .adminMessage(r.getAdminMessage())
                .branches(r.getBranches().stream().map(GymRegistrationResponse::branchView).toList())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private static BranchView branchView(RegistrationBranch b) {
        return BranchView.builder()
                .id(b.getId())
                .name(b.getName())
                .bio(b.getBio())
                .address(b.getAddress())
                .lat(b.getLat())
                .lng(b.getLng())
                .genderType(b.getGenderType())
                .email(b.getEmail())
                .phone(b.getPhone())
                .licenseId(b.getLicenseId())
                .openTime(b.getOpenTime())
                .closeTime(b.getCloseTime())
                .premisesProofUrl(b.getPremisesProofUrl())
                .operatingLicenseUrl(b.getOperatingLicenseUrl())
                .civilDefenseUrl(b.getCivilDefenseUrl())
                .build();
    }
}
