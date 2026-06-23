package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.domain.RequestableRole;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.dto.request.CreateRoleRequestRequest;
import com.example.DumbleAuthentication.dto.response.RoleRequestResponse;
import com.example.DumbleAuthentication.repository.UserRepository;
import com.example.DumbleAuthentication.service.CloudinaryService;
import com.example.DumbleAuthentication.service.RoleRequestService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Applicant-facing role-promotion endpoints. A PARTICIPANT submits and tracks
 * their request to become TRAINER or GYM_OWNER. Admin review lives in
 * {@code AdminRoleRequestController}.
 */
@RestController
@RequestMapping("/api/users/me/role-requests")
public class RoleRequestController {

    private final RoleRequestService roleRequestService;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    public RoleRequestController(RoleRequestService roleRequestService,
                                 UserRepository userRepository,
                                 CloudinaryService cloudinaryService) {
        this.roleRequestService = roleRequestService;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    /**
     * Submit a TRAINER application. The certificate file is uploaded to
     * Cloudinary server-side; only the resulting URL is persisted.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RoleRequestResponse> submit(
            @RequestParam("requestedRole") RequestableRole requestedRole,
            @RequestParam(value = "note", required = false) String note,
            @RequestPart("certificate") MultipartFile certificate) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (certificate == null || certificate.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        CreateRoleRequestRequest request = new CreateRoleRequestRequest();
        request.setRequestedRole(requestedRole);
        request.setNote(note);
        request.setCertificateUrl(cloudinaryService.uploadFile(certificate));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleRequestService.submit(user, request));
    }

    @GetMapping
    public ResponseEntity<List<RoleRequestResponse>> listMine() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(roleRequestService.listMine(user.getId()));
    }

    /**
     * Edit a request the admin sent back for changes, then resubmit it (same id).
     * A new certificate file is optional — when omitted the existing one is kept.
     */
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RoleRequestResponse> edit(
            @PathVariable java.util.UUID id,
            @RequestParam("requestedRole") RequestableRole requestedRole,
            @RequestParam(value = "note", required = false) String note,
            @RequestPart(value = "certificate", required = false) MultipartFile certificate) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        CreateRoleRequestRequest request = new CreateRoleRequestRequest();
        request.setRequestedRole(requestedRole);
        request.setNote(note);
        // Null URL → service keeps the existing certificate.
        if (certificate != null && !certificate.isEmpty()) {
            request.setCertificateUrl(cloudinaryService.uploadFile(certificate));
        }
        return ResponseEntity.ok(roleRequestService.editMine(user.getId(), id, request));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
