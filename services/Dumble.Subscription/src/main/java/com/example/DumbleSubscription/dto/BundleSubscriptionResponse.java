package com.example.DumbleSubscription.dto;

import com.example.DumbleSubscription.domain.BundleSubscription;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BundleSubscriptionResponse {
    private UUID id;
    private UUID participantId;
    private UUID sellerId;
    private String sellerType;
    private UUID bundleId;
    private String bundleName;
    private long pricePaidCents;
    private String currency;
    private int durationDays;
    private String status;
    private Instant startedAt;
    private Instant endsAt;
    private boolean autoRenew;

    public static BundleSubscriptionResponse from(BundleSubscription s) {
        return BundleSubscriptionResponse.builder()
                .id(s.getId())
                .participantId(s.getParticipantId())
                .sellerId(s.getSellerId())
                .sellerType(s.getSellerType().name())
                .bundleId(s.getBundleId())
                .bundleName(s.getBundleName())
                .pricePaidCents(s.getPricePaidCents())
                .currency(s.getCurrency())
                .durationDays(s.getDurationDays())
                .status(s.getStatus().name())
                .startedAt(s.getStartedAt())
                .endsAt(s.getEndsAt())
                .autoRenew(s.isAutoRenew())
                .build();
    }
}
