package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.CreateGymRegistrationRequest;
import com.dumble.service.gym.domain.dto.GymRegistrationResponse;
import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.service.CloudinaryService;
import com.dumble.service.gym.service.GymRegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Applicant-facing gym-owner registration. A participant submits and tracks
 * their application; admin review lives in {@code AdminGymRegistrationController}.
 *
 * Documents are sent as files and uploaded to Cloudinary server-side. The form
 * describes a single gym (one branch) — the shape the app submits.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/gym-registrations")
@Tag(name = "gym registration")
public class GymRegistrationController {

    private final GymRegistrationService gymRegistrationService;
    private final CloudinaryService cloudinaryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GymRegistrationResponse> submit(
            @ModelAttribute GymRegistrationForm form,
            @RequestHeader("Authorization") String token) {
        CreateGymRegistrationRequest request = buildRequest(form);
        return new ResponseEntity<>(gymRegistrationService.submit(request, token), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<GymRegistrationResponse>> listMine(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(gymRegistrationService.listMine(token));
    }

    /** Edit a registration the admin sent back for changes, then resubmit (same id). */
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GymRegistrationResponse> edit(
            @PathVariable java.util.UUID id,
            @ModelAttribute GymRegistrationForm form,
            @RequestHeader("Authorization") String token) {
        CreateGymRegistrationRequest request = buildRequest(form);
        return ResponseEntity.ok(gymRegistrationService.editMine(token, id, request));
    }

    /** Uploads every document and maps the flat form into the service request. */
    private CreateGymRegistrationRequest buildRequest(GymRegistrationForm f) {
        CreateGymRegistrationRequest req = new CreateGymRegistrationRequest();
        req.setPageName(f.getPageName());
        req.setNote(f.getNote());
        req.setNationalIdUrl(cloudinaryService.uploadFile(f.getNationalId()));
        req.setCommercialRegisterUrl(cloudinaryService.uploadFile(f.getCommercialRegister()));
        req.setTaxCardUrl(cloudinaryService.uploadFile(f.getTaxCard()));

        if (f.getSupportingDocuments() != null && !f.getSupportingDocuments().isEmpty()) {
            req.setSupportingDocumentUrls(
                    f.getSupportingDocuments().stream()
                            .filter(d -> d != null && !d.isEmpty())
                            .map(cloudinaryService::uploadFile)
                            .toList());
        }

        CreateGymRegistrationRequest.BranchInput branch = new CreateGymRegistrationRequest.BranchInput();
        branch.setName(f.getName());
        branch.setBio(f.getBio());
        branch.setAddress(f.getAddress());
        branch.setLat(f.getLat());
        branch.setLng(f.getLng());
        branch.setGenderType(f.getGenderType());
        branch.setEmail(f.getEmail());
        branch.setPhone(f.getPhone());
        branch.setLicenseId(f.getLicenseId());
        branch.setOpenTime(parseTime(f.getOpenTime()));
        branch.setCloseTime(parseTime(f.getCloseTime()));
        branch.setPremisesProofUrl(cloudinaryService.uploadFile(f.getPremisesProof()));
        branch.setOperatingLicenseUrl(cloudinaryService.uploadFile(f.getOperatingLicense()));
        branch.setCivilDefenseUrl(cloudinaryService.uploadFile(f.getCivilDefense()));
        req.setBranches(List.of(branch));
        return req;
    }

    /** Accepts "HH:mm" or "HH:mm:ss". */
    private static LocalTime parseTime(String t) {
        if (t == null || t.isBlank()) return null;
        String v = t.trim();
        return LocalTime.parse(v.chars().filter(c -> c == ':').count() == 1 ? v + ":00" : v);
    }

    /**
     * Flat multipart form for a single-gym registration: scalar fields bound by
     * name + the document files. Bound via {@code @ModelAttribute} so Spring
     * maps the multipart parts onto the setters.
     */
    @lombok.Getter
    @lombok.Setter
    public static class GymRegistrationForm {
        private String pageName;
        private String note;
        // Branch fields
        private String name;
        private String bio;
        private String address;
        private BigDecimal lat;
        private BigDecimal lng;
        private GenderType genderType;
        private String email;
        private String phone;
        private String licenseId;
        private String openTime;
        private String closeTime;
        // Required documents
        private MultipartFile nationalId;
        private MultipartFile commercialRegister;
        private MultipartFile taxCard;
        private MultipartFile premisesProof;
        private MultipartFile operatingLicense;
        private MultipartFile civilDefense;
        // Optional extras
        private List<MultipartFile> supportingDocuments;
    }
}
