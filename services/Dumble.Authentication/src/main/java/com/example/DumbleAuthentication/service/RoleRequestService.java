package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.RoleRequest;
import com.example.DumbleAuthentication.domain.RoleRequestStatus;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;
import com.example.DumbleAuthentication.dto.request.CreateRoleRequestRequest;
import com.example.DumbleAuthentication.dto.response.RoleRequestResponse;
import com.example.DumbleAuthentication.repository.RoleRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles role-promotion applications (TRAINER / GYM_OWNER). Applicant-side
 * operations here; admin review lands in a follow-up slice.
 */
@Service
public class RoleRequestService {

    // A user may only have one request in flight; these are the non-terminal states.
    private static final Set<RoleRequestStatus> OPEN_STATES =
            Set.of(RoleRequestStatus.PENDING, RoleRequestStatus.CHANGES_REQUESTED);

    private final RoleRequestRepository roleRequestRepository;

    public RoleRequestService(RoleRequestRepository roleRequestRepository) {
        this.roleRequestRepository = roleRequestRepository;
    }

    /**
     * Submit a new promotion request. Only a PARTICIPANT can apply, and only one
     * request may be open at a time — a rejected one can always be followed by a
     * fresh submission.
     */
    @Transactional
    public RoleRequestResponse submit(User applicant, CreateRoleRequestRequest req) {
        if (applicant.getUserType() != UserType.PARTICIPANT) {
            throw new IllegalArgumentException(
                    "Only participants can request a role promotion; your account is already "
                            + applicant.getUserType().name());
        }
        if (roleRequestRepository.existsByUserIdAndStatusIn(applicant.getId(), OPEN_STATES)) {
            throw new IllegalArgumentException(
                    "You already have a role request in progress; edit it instead of opening a new one");
        }

        RoleRequest request = new RoleRequest();
        request.setUserId(applicant.getId());
        request.setRequestedRole(req.getRequestedRole());
        request.setStatus(RoleRequestStatus.PENDING);
        request.setDocumentUrls(req.getDocumentUrls());
        request.setApplicantNote(req.getNote());

        return RoleRequestResponse.from(roleRequestRepository.save(request));
    }

    /** A user's own requests, newest first. */
    @Transactional(readOnly = true)
    public List<RoleRequestResponse> listMine(UUID userId) {
        return roleRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(RoleRequestResponse::from)
                .toList();
    }
}
