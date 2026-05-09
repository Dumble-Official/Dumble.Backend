package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {
    List<Receipt> findByUserIdOrderByIssuedAtDesc(UUID userId);
}
