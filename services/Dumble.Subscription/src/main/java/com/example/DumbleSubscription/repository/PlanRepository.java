package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.Plan;
import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByCode(PlatformPlanCode code);
    List<Plan> findByActiveTrue();
}
