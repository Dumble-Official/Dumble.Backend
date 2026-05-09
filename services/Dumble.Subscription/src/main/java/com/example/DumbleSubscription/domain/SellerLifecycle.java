package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.SellerStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-seller status track. New sellers don't have a row until their first
 * lifecycle event (freeze, winding-down, ban). Absence of a row = ACTIVE.
 */
@Entity
@Table(name = "seller_lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class SellerLifecycle {

    @Id
    @Column(name = "seller_id")
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    private Instant frozenAt;
    private String frozenReason;
    private Instant frozenUntil;       // 7-day auto-ban deadline (Decision 16.2)

    private Instant windingDownAt;
    private String windingDownReason;

    private Instant bannedAt;
    private String banReason;

    private Instant closedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
