package com.example.DumbleWallet.dto;

import com.example.DumbleWallet.domain.Wallet;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Wallet PDF Decision 7.1 — user-facing summary view. Subscription's
 * WalletServiceClient consumes only {@code availableCents / pendingCents /
 * currency}, so those fields stay first-class; the recent-activity section is
 * a UI nicety.
 */
@Data
@Builder
public class WalletSummaryResponse {

    private long availableCents;
    private long pendingCents;
    private String currency;

    /** Last 30 days of ledger entries — the "Recent activity" panel. */
    private List<WalletEntryResponse> recentActivity;

    public static WalletSummaryResponse from(Wallet w, List<WalletEntryResponse> recent) {
        return WalletSummaryResponse.builder()
                .availableCents(w.getAvailableCents())
                .pendingCents(w.getPendingCents())
                .currency(w.getCurrency())
                .recentActivity(recent)
                .build();
    }
}
