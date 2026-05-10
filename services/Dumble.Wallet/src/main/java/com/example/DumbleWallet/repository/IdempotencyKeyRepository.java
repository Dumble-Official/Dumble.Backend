package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    long deleteByExpiresAtBefore(Instant cutoff);
}
