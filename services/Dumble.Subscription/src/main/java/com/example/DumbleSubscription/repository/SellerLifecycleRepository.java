package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.SellerLifecycle;
import com.example.DumbleSubscription.domain.enums.SellerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SellerLifecycleRepository extends JpaRepository<SellerLifecycle, UUID> {
    List<SellerLifecycle> findByStatusAndFrozenUntilLessThanEqual(SellerStatus status, Instant cutoff);
    List<SellerLifecycle> findByStatus(SellerStatus status);
}
