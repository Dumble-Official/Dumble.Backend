package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.PaymentMethodToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodTokenRepository extends JpaRepository<PaymentMethodToken, UUID> {

    Optional<PaymentMethodToken> findByToken(String token);

    @Query("""
        SELECT t FROM PaymentMethodToken t
        WHERE t.userId = :userId AND t.deletedAt IS NULL
        ORDER BY t.createdAt DESC
        """)
    List<PaymentMethodToken> findActiveByUser(@Param("userId") UUID userId);
}
