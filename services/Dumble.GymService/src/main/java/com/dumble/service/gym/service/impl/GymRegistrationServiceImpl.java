package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.client.AuthPromotionClient;
import com.dumble.service.gym.domain.dto.CreateGymRegistrationRequest;
import com.dumble.service.gym.domain.dto.GymRegistrationResponse;
import com.dumble.service.gym.domain.dto.UserResponse;
import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.entity.GymRegistration;
import com.dumble.service.gym.domain.entity.GymStaff;
import com.dumble.service.gym.domain.entity.RegistrationBranch;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import com.dumble.service.gym.domain.enumuration.StaffRole;
import com.dumble.service.gym.exception.BadRequestException;
import com.dumble.service.gym.exception.ResourceNotFoundException;
import com.dumble.service.gym.exception.UnauthorizedAccessException;
import com.dumble.service.gym.exception.UpstreamServiceException;
import com.dumble.service.gym.repository.GymRegistrationRepository;
import com.dumble.service.gym.repository.GymRepository;
import com.dumble.service.gym.repository.GymStaffRepository;
import com.dumble.service.gym.service.GymRegistrationService;
import com.dumble.service.gym.util.TokenExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final GymRepository gymRepository;
    private final GymStaffRepository gymStaffRepository;
    private final AuthPromotionClient authPromotionClient;
    private final TokenExtractor tokenExtractor;
    private final PlatformTransactionManager txManager;

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
        // Idempotent: if an open registration already exists, return it instead
        // of erroring. This is the common case when the gateway timed out on a
        // slow multi-file upload but the server actually persisted the row — the
        // client's retry then resolves cleanly to the pending registration.
        var open = registrationRepository
                .findFirstByApplicantIdAndStatusInOrderByCreatedAtDesc(user.getId(), OPEN_STATES);
        if (open.isPresent()) {
            return GymRegistrationResponse.from(open.get());
        }

        GymRegistration registration = new GymRegistration();
        registration.setApplicantId(user.getId());
        registration.setPageName(request.getPageName());
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setNationalIdUrl(request.getNationalIdUrl());
        registration.setCommercialRegisterUrl(request.getCommercialRegisterUrl());
        registration.setTaxCardUrl(request.getTaxCardUrl());
        registration.setApplicantNote(request.getNote());
        if (request.getSupportingDocumentUrls() != null) {
            registration.getSupportingDocumentUrls().addAll(request.getSupportingDocumentUrls());
        }
        request.getBranches().forEach(b -> registration.addBranch(toBranch(b)));

        // The exists() check above is a fast path but not race-safe; a unique key
        // on the open-state applicant (see GymRegistrationOpenIndexMigration) is
        // the real guard. A second concurrent submit trips it — surface that as
        // the same 400 the pre-check gives, not a 500.
        try {
            return GymRegistrationResponse.from(registrationRepository.saveAndFlush(registration));
        } catch (DataIntegrityViolationException dup) {
            // Concurrent submit won the open-state unique key. Return that row so
            // the loser is idempotent too, not a confusing 400.
            return registrationRepository
                    .findFirstByApplicantIdAndStatusInOrderByCreatedAtDesc(user.getId(), OPEN_STATES)
                    .map(GymRegistrationResponse::from)
                    .orElseThrow(() -> new BadRequestException(
                            "You already have a gym registration in progress; edit it instead of opening a new one"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymRegistrationResponse> listMine(String token) {
        UUID userId = tokenExtractor.extractUser(token).getId();
        return registrationRepository.findByApplicantIdOrderByCreatedAtDesc(userId).stream()
                .map(GymRegistrationResponse::from)
                .toList();
    }

    /**
     * Edit and resubmit a registration the admin sent back. Owner-only, and only
     * while it's CHANGES_REQUESTED. It keeps its id and flips back to PENDING;
     * the prior admin message + reviewer stay so the admin can see it's a
     * resubmission and recall what was asked.
     */
    @Override
    @Transactional
    public GymRegistrationResponse editMine(String token, UUID registrationId, CreateGymRegistrationRequest request) {
        UUID userId = tokenExtractor.extractUser(token).getId();
        GymRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        if (!reg.getApplicantId().equals(userId)) {
            throw new UnauthorizedAccessException("This registration does not belong to you");
        }
        if (reg.getStatus() != RegistrationStatus.CHANGES_REQUESTED) {
            throw new BadRequestException(
                    "Only a registration sent back for changes can be edited (current status: "
                            + reg.getStatus() + ")");
        }

        reg.setPageName(request.getPageName());
        reg.setNationalIdUrl(request.getNationalIdUrl());
        reg.setCommercialRegisterUrl(request.getCommercialRegisterUrl());
        reg.setTaxCardUrl(request.getTaxCardUrl());
        reg.setApplicantNote(request.getNote());
        reg.getSupportingDocumentUrls().clear();
        if (request.getSupportingDocumentUrls() != null) {
            reg.getSupportingDocumentUrls().addAll(request.getSupportingDocumentUrls());
        }
        reg.getBranches().clear();
        request.getBranches().forEach(b -> reg.addBranch(toBranch(b)));
        reg.setStatus(RegistrationStatus.PENDING);
        return GymRegistrationResponse.from(registrationRepository.save(reg));
    }

    // ── Admin side ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<GymRegistrationResponse> listForAdmin(String token, RegistrationStatus status, Pageable pageable) {
        requireAdmin(token);
        Page<GymRegistration> page = (status == null)
                ? registrationRepository.findAll(pageable)
                : registrationRepository.findByStatus(status, pageable);
        return page.map(GymRegistrationResponse::from);
    }

    /**
     * Approve a pending registration: create one ACTIVE, verified Gym per branch
     * under the applicant (with the owner staff row, keeping Gym.ownerId and the
     * GYM staff row in agreement), then promote the applicant to GYM_OWNER in auth.
     *
     * The decision is claimed with a compare-and-set (PENDING → APPROVED), which
     * serializes concurrent reviews on the row and makes "only one terminal action
     * wins" hold without spanning a lock across the work. The auth promotion is a
     * cross-service HTTP call, so it runs AFTER the local commit — the DB
     * transaction never spans network I/O, and because the approval is already
     * durable a concurrent reject can never leave a promoted-but-rejected
     * applicant. Promote is idempotent and retried; if it ultimately fails the
     * registration is approved and the gyms exist but the applicant is not yet
     * GYM_OWNER — a clear 502, recoverable by re-running the (idempotent) promote.
     */
    @Override
    public GymRegistrationResponse approve(String token, UUID registrationId) {
        UserResponse admin = requireAdmin(token);

        // Create gyms + claim the approval atomically, off the network path.
        TransactionTemplate tx = new TransactionTemplate(txManager);
        GymRegistrationResponse response = tx.execute(status -> finalizeApproval(admin, registrationId));

        // Promote in auth only after the approval is committed.
        promoteWithRetry(response.getApplicantId());
        return response;
    }

    /** The DB side of an approval: one ACTIVE/verified Gym + owner staff row per branch. */
    private GymRegistrationResponse finalizeApproval(UserResponse admin, UUID registrationId) {
        GymRegistration reg = claim(registrationId, RegistrationStatus.APPROVED);

        for (RegistrationBranch br : reg.getBranches()) {
            Gym gym = new Gym();
            gym.setOwnerId(reg.getApplicantId());
            gym.setName(br.getName());
            gym.setBio(br.getBio());
            gym.setAddress(br.getAddress());
            gym.setLat(br.getLat());
            gym.setLng(br.getLng());
            gym.setGenderType(br.getGenderType());
            gym.setEmail(br.getEmail());
            gym.setPhone(br.getPhone());
            gym.setLicenseId(br.getLicenseId());
            gym.setOpenTime(br.getOpenTime());
            gym.setCloseTime(br.getCloseTime());
            // Owner already verified via this registration → the gym is live, no second review.
            gym.setStatus(GymStatus.ACTIVE);
            gym.setIsVerified(true);
            Gym saved = gymRepository.save(gym);

            GymStaff ownerStaff = new GymStaff();
            ownerStaff.setGym(saved);
            ownerStaff.setUserId(reg.getApplicantId());
            ownerStaff.setRole(StaffRole.GYM);
            gymStaffRepository.save(ownerStaff);
        }

        reg.setAdminMessage(null);
        reg.setReviewedBy(admin.getId());
        return GymRegistrationResponse.from(registrationRepository.save(reg));
    }

    @Override
    @Transactional
    public GymRegistrationResponse requestChanges(String token, UUID registrationId, String message) {
        UserResponse admin = requireAdmin(token);
        GymRegistration reg = claim(registrationId, RegistrationStatus.CHANGES_REQUESTED);
        reg.setAdminMessage(message);
        reg.setReviewedBy(admin.getId());
        return GymRegistrationResponse.from(registrationRepository.save(reg));
    }

    @Override
    @Transactional
    public GymRegistrationResponse reject(String token, UUID registrationId, String message) {
        UserResponse admin = requireAdmin(token);
        GymRegistration reg = claim(registrationId, RegistrationStatus.REJECTED);
        reg.setAdminMessage(message);
        reg.setReviewedBy(admin.getId());
        return GymRegistrationResponse.from(registrationRepository.save(reg));
    }

    private UserResponse requireAdmin(String token) {
        UserResponse user = tokenExtractor.extractUser(token);
        if (!"ADMIN".equals(user.getUserType())) {
            throw new UnauthorizedAccessException("Only system admins can review gym registrations.");
        }
        return user;
    }

    /**
     * Atomically move a PENDING registration to {@code to} and return the (now
     * updated) row. The compare-and-set UPDATE takes the row write-lock, so two
     * admins reviewing the same registration serialize: the second's update
     * matches zero rows (status is no longer PENDING) and is rejected, instead of
     * both proceeding. Admin actions only apply to a PENDING registration.
     */
    private GymRegistration claim(UUID registrationId, RegistrationStatus to) {
        int updated = registrationRepository.compareAndSetStatus(
                registrationId, RegistrationStatus.PENDING, to);
        if (updated == 0) {
            RegistrationStatus current = registrationRepository.findById(registrationId)
                    .map(GymRegistration::getStatus)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Registration not found: " + registrationId));
            throw new BadRequestException(
                    "Registration is not pending review (current status: " + current + ")");
        }
        return registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registration not found: " + registrationId));
    }

    /** Promote the applicant in auth, retrying transient failures a few times. */
    private void promoteWithRetry(UUID applicantId) {
        UpstreamServiceException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                authPromotionClient.promoteToGymOwner(applicantId);
                return;
            } catch (UpstreamServiceException e) {
                last = e;
            }
        }
        throw last;
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
