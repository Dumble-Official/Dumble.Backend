package com.example.DumbleWallet.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response shape for credit / debit / admin-adjust — same fields per
 * Wallet PDF Decision 3.1 (and consumed unchanged by Subscription's
 * WalletServiceClient).
 */
@Data
@Builder
public class WalletWriteResponse {
    private UUID walletEntryId;
    private long newBalanceCents;
    private long pendingCents;
    private String currency;
}
