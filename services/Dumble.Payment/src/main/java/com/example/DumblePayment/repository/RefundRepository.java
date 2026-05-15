package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByChargeId(UUID chargeId);
    Optional<Refund> findByProviderRef(String providerRef);
}
