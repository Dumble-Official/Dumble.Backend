package com.dumble.service.schedule.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Marks that a schedule item was done on a specific calendar date. */
@Entity
@Table(name = "item_completion",
        uniqueConstraints = @UniqueConstraint(name = "ux_item_completion", columnNames = {"item_id", "completed_on"}))
@Getter
@Setter
public class ItemCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
