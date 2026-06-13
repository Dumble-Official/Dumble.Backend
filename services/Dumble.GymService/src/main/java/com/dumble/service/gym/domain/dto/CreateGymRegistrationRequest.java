package com.dumble.service.gym.domain.dto;

import com.dumble.service.gym.domain.enumuration.GenderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Body for submitting / editing a gym-owner registration: the gym page name,
 * the shared business documents once, and one-or-more branches (each with its
 * own location data + documents). All document fields are Cloudinary URLs.
 */
@Getter
@Setter
public class CreateGymRegistrationRequest {

    @NotBlank
    @Size(max = 150)
    private String pageName;

    @NotBlank(message = "nationalIdUrl is required")
    @Size(max = 512)
    private String nationalIdUrl;

    @NotBlank(message = "commercialRegisterUrl is required")
    @Size(max = 512)
    private String commercialRegisterUrl;

    @NotBlank(message = "taxCardUrl is required")
    @Size(max = 512)
    private String taxCardUrl;

    @Size(max = 1000)
    private String note;

    @NotEmpty(message = "At least one branch is required")
    @Size(max = 50, message = "At most 50 branches")
    @Valid
    private List<BranchInput> branches;

    @Getter
    @Setter
    public static class BranchInput {
        @NotBlank @Size(max = 150) private String name;
        @Size(max = 5000) private String bio;
        @NotBlank @Size(max = 300) private String address;
        @NotNull private BigDecimal lat;
        @NotNull private BigDecimal lng;
        @NotNull private GenderType genderType;
        @Size(max = 150) private String email;
        @Size(max = 30) private String phone;
        @NotBlank @Size(max = 100) private String licenseId;
        @NotNull private LocalTime openTime;
        @NotNull private LocalTime closeTime;

        @NotBlank(message = "premisesProofUrl is required") @Size(max = 512) private String premisesProofUrl;
        @NotBlank(message = "operatingLicenseUrl is required") @Size(max = 512) private String operatingLicenseUrl;
        @NotBlank(message = "civilDefenseUrl is required") @Size(max = 512) private String civilDefenseUrl;
    }
}
