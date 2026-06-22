package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.RoleRequest;
import com.example.DumbleAuthentication.domain.RoleRequestStatus;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;
import com.example.DumbleAuthentication.dto.request.CreateRoleRequestRequest;
import com.example.DumbleAuthentication.dto.response.RoleRequestResponse;
import com.example.DumbleAuthentication.repository.RoleRequestRepository;
import com.example.DumbleAuthentication.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles role-promotion applications (TRAINER / GYM_OWNER): applicant submit /
 * list, and admin review (approve / request-changes / reject). On approval the
 * applicant's {@code userType} column is flipped atomically — the design doc
 * mandates a column flip over a row migration.
 */
@Service
public class RoleRequestService {

    // A user may only have one request in flight; these are the non-terminal states.
    private static final Set<RoleRequestStatus> OPEN_STATES =
            Set.of(RoleRequestStatus.PENDING, RoleRequestStatus.CHANGES_REQUESTED);

    private final RoleRequestRepository roleRequestRepository;
    private final UserRepository userRepository;

    public RoleRequestService(RoleRequestRepository roleRequestRepository,
                              UserRepository userRepository) {
        this.roleRequestRepository = roleRequestRepository;
        this.userRepository = userRepository;
    }

    // ── Applicant side ──────────────────────────────────────────────────

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
        request.setCertificateUrl(req.getCertificateUrl());
        request.setApplicantNote(req.getNote());

        // The exists() check above is a fast path but not race-safe; a partial
        // unique index on (user_id) WHERE status is open is the real guard (see
        // RoleRequestOpenIndexMigration). A second concurrent submit trips it —
        // surface that as the same 400 the pre-check gives, not a 500.
        try {
            return RoleRequestResponse.from(roleRequestRepository.saveAndFlush(request));
        } catch (DataIntegrityViolationException dup) {
            throw new IllegalArgumentException(
                    "You already have a role request in progress; edit it instead of opening a new one");
        }
    }

    /** A user's own requests, newest first. */
    @Transactional(readOnly = true)
    public List<RoleRequestResponse> listMine(UUID userId) {
        return roleRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(RoleRequestResponse::from)
                .toList();
    }

    /**
     * Edit and resubmit a request the admin sent back. Only the owner, and only
     * while it's in CHANGES_REQUESTED, can edit it. It keeps its id and flips
     * back to PENDING; the prior admin message and reviewer are kept on the row
     * so the admin can see this is a resubmission (a PENDING request with a
     * reviewer already set) and recall what they had asked for.
     */
    @Transactional
    public RoleRequestResponse editMine(UUID userId, UUID requestId, CreateRoleRequestRequest req) {
        RoleRequest request = roleRequestRepository.findById(requestId)
                .orElseThrow(() -> new UsernameNotFoundException("Role request not found"));
        if (!request.getUserId().equals(userId)) {
            throw new IllegalStateException("This role request does not belong to you");
        }
        if (request.getStatus() != RoleRequestStatus.CHANGES_REQUESTED) {
            throw new IllegalArgumentException(
                    "Only a request that was sent back for changes can be edited (current status: "
                            + request.getStatus() + ")");
        }

        request.setRequestedRole(req.getRequestedRole());
        // A null certificate URL means the applicant didn't re-upload — keep the existing one.
        if (req.getCertificateUrl() != null) {
            request.setCertificateUrl(req.getCertificateUrl());
        }
        request.setApplicantNote(req.getNote());
        request.setStatus(RoleRequestStatus.PENDING);
        return RoleRequestResponse.from(roleRequestRepository.save(request));
    }

    // ── Admin side ──────────────────────────────────────────────────────

    /** Admin queue — all requests, or only those in {@code status} when given. */
    @Transactional(readOnly = true)
    public Page<RoleRequestResponse> listForAdmin(RoleRequestStatus status, Pageable pageable) {
        Page<RoleRequest> page = (status == null)
                ? roleRequestRepository.findAll(pageable)
                : roleRequestRepository.findByStatus(status, pageable);
        return page.map(RoleRequestResponse::from);
    }

    /**
     * Approve a pending request: flip the applicant's userType to the requested
     * role and mark the request APPROVED. The role change and the status change
     * commit together.
     */
    @Transactional
    public RoleRequestResponse approve(UUID requestId, UUID adminId) {
        RoleRequest request = pendingOrThrow(requestId);

        User applicant = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("Applicant account no longer exists"));
        applicant.setUserType(request.getRequestedRole().toUserType());
        userRepository.save(applicant);

        request.setStatus(RoleRequestStatus.APPROVED);
        request.setAdminMessage(null);
        request.setReviewedBy(adminId);
        return RoleRequestResponse.from(roleRequestRepository.save(request));
    }

    /** Send a pending request back to the applicant with a message to fix. */
    @Transactional
    public RoleRequestResponse requestChanges(UUID requestId, UUID adminId, String message) {
        RoleRequest request = pendingOrThrow(requestId);
        request.setStatus(RoleRequestStatus.CHANGES_REQUESTED);
        request.setAdminMessage(message);
        request.setReviewedBy(adminId);
        return RoleRequestResponse.from(roleRequestRepository.save(request));
    }

    /** Reject a pending request, keeping the row + reason for the record. */
    @Transactional
    public RoleRequestResponse reject(UUID requestId, UUID adminId, String message) {
        RoleRequest request = pendingOrThrow(requestId);
        request.setStatus(RoleRequestStatus.REJECTED);
        request.setAdminMessage(message);
        request.setReviewedBy(adminId);
        return RoleRequestResponse.from(roleRequestRepository.save(request));
    }

    /**
     * Load a request that's reviewable. Admin actions only apply to a PENDING
     * request — a CHANGES_REQUESTED one is back with the applicant and must be
     * resubmitted before it can be reviewed again.
     */
    private RoleRequest pendingOrThrow(UUID requestId) {
        RoleRequest request = roleRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new UsernameNotFoundException("Role request not found"));
        if (request.getStatus() != RoleRequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is not pending review (current status: " + request.getStatus() + ")");
        }
        return request;
    }
}
