package com.dumble.service.gym.domain.entity;

import com.dumble.service.gym.domain.enumuration.GenderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * One branch (physical location) inside a {@link GymRegistration}. Holds the
 * same data a Gym needs plus the per-branch documents. On approval each branch
 * becomes its own ACTIVE/verified Gym under the same owner.
 */
@Entity
@Table(name = "registration_branches")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private GymRegistration registration;

    // ── Gym/location fields (mirror what a Gym needs at creation) ─────────
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal lat;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_type", nullable = false)
    private GenderType genderType;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    /** The branch's operating-licence number. */
    @Column(name = "license_id", nullable = false, length = 100)
    private String licenseId;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    // ── Per-branch documents (Cloudinary URLs) ───────────────────────────
    /** Premises ownership / lease deed — proves the location is real. */
    @Column(name = "premises_proof_url", nullable = false, length = 512)
    private String premisesProofUrl;

    /** Operating / activity licence document. */
    @Column(name = "operating_license_url", nullable = false, length = 512)
    private String operatingLicenseUrl;

    /** Civil Defense / fire-safety approval. */
    @Column(name = "civil_defense_url", nullable = false, length = 512)
    private String civilDefenseUrl;
}
