package com.example.DumbleSubscription.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class EntryTokenResponse {
    private String qrPayload;
    private Instant expiresAt;
}
