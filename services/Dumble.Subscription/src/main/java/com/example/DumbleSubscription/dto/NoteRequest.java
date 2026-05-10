package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoteRequest {
    @NotBlank
    @Size(max = 2000)
    private String note;
}
