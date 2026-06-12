package com.example.DumbleAuthentication.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A participant's application to be promoted to TRAINER or GYM_OWNER. Reviewed
 * by an admin, who verifies the attached documents (certificate / business
 * licence) before approving. On approval the applicant's {@code userType} is
 * flipped atomically (see RoleRequestService) — the design doc mandates a role
 * column flip, not a row migration.
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
     * Cloudinary URLs of the supporting documents (cert / licence). Stored as a
     * comma-joined string in one column via StringListConverter — the same
     * pattern fitness_goals uses; the client uploads to Cloudinary and passes
     * the URLs, matching how the rest of auth handles images (e.g. pfp).
     */
    @Column(name = "document_urls", length = 2000)
    @Convert(converter = StringListConverter.class)
    private List<String> documentUrls;

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

    public List<String> getDocumentUrls() { return documentUrls; }
    public void setDocumentUrls(List<String> documentUrls) { this.documentUrls = documentUrls; }

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
