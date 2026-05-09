package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.PromoCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, UUID> {
}
