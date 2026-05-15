package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.PaymentEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentEventLogRepository extends JpaRepository<PaymentEventLog, UUID> {
}
