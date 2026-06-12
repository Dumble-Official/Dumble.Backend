package com.dumble.service.gym.repository;

import com.dumble.service.gym.domain.entity.GymRegistration;
import com.dumble.service.gym.domain.enumuration.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GymRegistrationRepository extends JpaRepository<GymRegistration, UUID> {

    List<GymRegistration> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId);

    boolean existsByApplicantIdAndStatusIn(UUID applicantId, Collection<RegistrationStatus> statuses);

    Page<GymRegistration> findByStatus(RegistrationStatus status, Pageable pageable);
}
