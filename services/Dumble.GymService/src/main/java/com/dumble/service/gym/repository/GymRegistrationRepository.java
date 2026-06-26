package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.GymRegistration;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GymRegistrationRepository extends JpaRepository<GymRegistration, UUID> {

    List<GymRegistration> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId);

    boolean existsByApplicantIdAndStatusIn(UUID applicantId, Collection<RegistrationStatus> statuses);

    /** The applicant's current open (PENDING / CHANGES_REQUESTED) registration, if any. */
    Optional<GymRegistration> findFirstByApplicantIdAndStatusInOrderByCreatedAtDesc(
            UUID applicantId, Collection<RegistrationStatus> statuses);

    Page<GymRegistration> findByStatus(RegistrationStatus status, Pageable pageable);

    /**
     * Atomically move a registration from one status to another, returning the
     * number of rows changed (1 if it was still in {@code from}, else 0). The
     * UPDATE takes the row write-lock, so two admins reviewing the same
     * registration serialize and only one terminal decision can win.
     */
    @Modifying(clearAutomatically = true)
    @Query("update GymRegistration r set r.status = :to where r.id = :id and r.status = :from")
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("from") RegistrationStatus from,
                            @Param("to") RegistrationStatus to);
}
