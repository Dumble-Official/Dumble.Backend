package com.dumble.service.gym.domain.entity;

import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A participant's application to become a GYM_OWNER. Carries the shared
 * business documents once and one-or-more {@link RegistrationBranch branches},
 * each with its own location documents. An admin reviews it; on approval the
 * applicant is promoted to GYM_OWNER and a verified, ACTIVE Gym is created for
 * each branch.
 */
@Entity
@Table(name = "gym_registrations")
@Getter
@Setter
@NoArgsConstructor
public class GymRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The applicant (auth user id). */
    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    /** The gym page / brand name the owner operates under. */
    @Column(name = "page_name", nullable = false, length = 150)
    private String pageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    // ── Shared business documents (Cloudinary URLs), filled once ──────────
    @Column(name = "national_id_url", nullable = false, length = 512)
    private String nationalIdUrl;

    @Column(name = "commercial_register_url", nullable = false, length = 512)
    private String commercialRegisterUrl;

    @Column(name = "tax_card_url", nullable = false, length = 512)
    private String taxCardUrl;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegistrationBranch> branches = new ArrayList<>();

    @Column(name = "applicant_note", length = 1000)
    private String applicantNote;

    /** Admin's message — the reason on CHANGES_REQUESTED or REJECTED. */
    @Column(name = "admin_message", length = 1000)
    private String adminMessage;

    /** Admin who last reviewed it (null until first reviewed). */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Attach a branch and keep both sides of the relationship in sync. */
    public void addBranch(RegistrationBranch branch) {
        branch.setRegistration(this);
        this.branches.add(branch);
    }
}
