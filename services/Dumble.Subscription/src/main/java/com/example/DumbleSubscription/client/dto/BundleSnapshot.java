package com.example.DumbleSubscription.client.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shape Subscription expects from BundleManagement. Only the fields we
 * snapshot onto BundleSubscription rows + need for checkout decisions.
 */
@Data
public class BundleSnapshot {
    private UUID id;
    private UUID sellerId;
    private String sellerType;          // GYM | TRAINER
    private String name;
    private long priceCents;
    private String currency;
    private int durationDays;
    private Instant expiresOn;          // null = evergreen
    private boolean active;
    /** Amenities / permissions this bundle grants — surfaced in scan response per Decision 21.4. */
    private List<String> amenities;
}
