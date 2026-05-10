package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.WalletEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletEventLogRepository extends JpaRepository<WalletEventLog, UUID> {
}
