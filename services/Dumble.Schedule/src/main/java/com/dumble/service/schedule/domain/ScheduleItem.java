package com.dumble.service.schedule.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One free-text entry in a day's list (e.g. "Bench 4x8, rest 90s" or
 * "200g chicken + 80g rice"), with an optional embedded YouTube video and the
 * author who wrote it. Keyed by (table, weekday) — persists until edited.
 */
@Entity
@Table(name = "schedule_item")
@Getter
@Setter
public class ScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "table_type", nullable = false, length = 16)
    private TableType tableType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Weekday weekday;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** Parsed id of an embedded YouTube video (played in-app); null if none. */
    @Column(name = "youtube_video_id", length = 32)
    private String youtubeVideoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 16)
    private AuthorType authorType;

    /** Author user id (the trainer for TRAINER items, the client for CLIENT; null for CHATBOT). */
    @Column(name = "author_id")
    private UUID authorId;

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
