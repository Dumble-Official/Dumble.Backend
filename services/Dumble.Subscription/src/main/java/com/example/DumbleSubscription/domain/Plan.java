package com.example.DumbleSubscription.domain;

import com.example.DumbleSubscription.domain.enums.PlatformPlanCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Plan catalog (FREE, PRO). Seeded by V1 migration. Per PDF Decision 3.1
 * the entitlement booleans live here so the entitlement endpoint can read
 * them directly without hard-coded logic.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlatformPlanCode code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long priceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean canUseChatbot;

    /** null = unlimited; 0 = hard wall (FREE). */
    private Integer chatbotMessagesPerDay;

    @Column(nullable = false)
    private boolean canDmAnyone;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;
}
