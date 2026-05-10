package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.SellerBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SellerBankAccountRepository extends JpaRepository<SellerBankAccount, UUID> {
}
