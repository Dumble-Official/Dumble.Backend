package com.example.DumbleAuthentication.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A participant's application to become a TRAINER. Reviewed by an admin, who
 * verifies the attached certificate before approving. On approval the
 * applicant's {@code userType} is flipped atomically (see RoleRequestService) —
 * the design doc mandates a role column flip, not a row migration.
 *
 * (Becoming a GYM_OWNER goes through the gym service's gym-registration flow,
 * not this one — that one carries the business + branch documents.)
 */
@Entity
@Table(name = "role_requests")
public class RoleRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The applicant (auth user id). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 20)
    private RequestableRole requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleRequestStatus status = RoleRequestStatus.PENDING;

    /**
     * Cloudinary URL of the applicant's training certificate. The client uploads
     * to Cloudinary and passes the URL, matching how the rest of auth handles
     * images (e.g. pfp).
     */
    @Column(name = "certificate_url", length = 512)
    private String certificateUrl;

    /** Optional note from the applicant. */
    @Column(name = "applicant_note", length = 1000)
    private String applicantNote;

    /**
     * Admin's message — the reason on CHANGES_REQUESTED or REJECTED. Shown back
     * to the applicant so they know what to fix.
     */
    @Column(name = "admin_message", length = 1000)
    private String adminMessage;

    /** Admin who last reviewed it (null until first reviewed). */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public RequestableRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(RequestableRole requestedRole) { this.requestedRole = requestedRole; }

    public RoleRequestStatus getStatus() { return status; }
    public void setStatus(RoleRequestStatus status) { this.status = status; }

    public String getCertificateUrl() { return certificateUrl; }
    public void setCertificateUrl(String certificateUrl) { this.certificateUrl = certificateUrl; }

    public String getApplicantNote() { return applicantNote; }
    public void setApplicantNote(String applicantNote) { this.applicantNote = applicantNote; }

    public String getAdminMessage() { return adminMessage; }
    public void setAdminMessage(String adminMessage) { this.adminMessage = adminMessage; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
