package com.example.DumbleSubscription.dto;

import com.example.DumbleSubscription.domain.ParticipantGymNote;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NoteResponse {
    private UUID id;
    private String note;
    private UUID authorStaffUserId;
    private Instant createdAt;

    public static NoteResponse from(ParticipantGymNote n) {
        return NoteResponse.builder()
                .id(n.getId())
                .note(n.getNote())
                .authorStaffUserId(n.getAuthorStaffUserId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
