package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.EscrowEntry;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.dto.EarningsCohort;
import com.example.DumbleSubscription.dto.EarningsSummary;
import com.example.DumbleSubscription.dto.PayoutItem;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seller dashboard — Decision 5.3 + 15.1. Sources entirely from Subscription's
 * escrow tables (Pending = HELD, Paid = PAID_OUT). No Wallet involvement.
 */
@Service
public class EarningsService {

    private final EscrowEntryRepository escrowEntryRepository;

    public EarningsService(EscrowEntryRepository escrowEntryRepository) {
        this.escrowEntryRepository = escrowEntryRepository;
    }

    public EarningsSummary summary(UUID sellerId) {
        long pending = sumStatus(sellerId, EscrowStatus.HELD)
                + sumStatus(sellerId, EscrowStatus.AVAILABLE);
        long paid = sumStatus(sellerId, EscrowStatus.PAID_OUT);
        return EarningsSummary.builder()
                .pendingCents(pending)
                .paidCents(paid)
                .lifetimeCents(paid)
                .build();
    }

    public List<EarningsCohort> cohorts(UUID sellerId) {
        List<EscrowEntry> entries = escrowEntryRepository.findBySellerIdAndStatus(sellerId, EscrowStatus.HELD);
        Map<String, List<EscrowEntry>> byCohort = entries.stream()
                .collect(Collectors.groupingBy(EscrowEntry::getCohortKey));
        return byCohort.entrySet().stream()
                .map(e -> EarningsCohort.builder()
                        .cohortKey(e.getKey())
                        .amountCents(e.getValue().stream().mapToLong(EscrowEntry::getAmountCents).sum())
                        .scheduledAt(e.getValue().stream()
                                .map(EscrowEntry::getOriginalScheduledAt)
                                .min(Comparator.naturalOrder()).orElse(null))
                        .deferredCount(e.getValue().stream().mapToInt(EscrowEntry::getDeferredCount).max().orElse(0))
                        .reason(e.getValue().stream().map(EscrowEntry::getDeferReason)
                                .filter(r -> r != null).findFirst().orElse(null))
                        .build())
                .sorted(Comparator.comparing(EarningsCohort::getCohortKey))
                .toList();
    }

    public List<PayoutItem> payouts(UUID sellerId) {
        return escrowEntryRepository.findBySellerIdAndStatus(sellerId, EscrowStatus.PAID_OUT).stream()
                .sorted(Comparator.comparing(EscrowEntry::getPaidOutAt).reversed())
                .map(e -> PayoutItem.builder()
                        .escrowEntryId(e.getId())
                        .amountCents(e.getAmountCents())
                        .currency(e.getCurrency())
                        .paidOutAt(e.getPaidOutAt())
                        .payoutRef(e.getPayoutRef())
                        .build())
                .toList();
    }

    private long sumStatus(UUID sellerId, EscrowStatus status) {
        return escrowEntryRepository.findBySellerIdAndStatus(sellerId, status).stream()
                .mapToLong(EscrowEntry::getAmountCents).sum();
    }
}
