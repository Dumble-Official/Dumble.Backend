package com.dumble.service.schedule.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Per-day (Sun..Sat) nutrition target for the Meals table. Persists until edited. */
@Entity
@Table(name = "meal_day_target",
        uniqueConstraints = @UniqueConstraint(name = "ux_meal_target", columnNames = {"schedule_id", "weekday"}))
@Getter
@Setter
public class MealDayTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Weekday weekday;

    private Integer calories;

    @Column(name = "protein_g")
    private Integer proteinG;

    @Column(name = "carbs_g")
    private Integer carbsG;

    @Column(name = "fat_g")
    private Integer fatG;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
