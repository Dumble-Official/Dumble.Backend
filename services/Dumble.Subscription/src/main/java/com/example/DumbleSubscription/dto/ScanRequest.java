package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ScanRequest {
    @NotBlank
    private String qrPayload;

    @NotNull
    private UUID gymId;
}
