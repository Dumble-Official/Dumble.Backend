package com.dumble.service.gym.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class GymDocumentResponse {
    private Long id;
    private String documentUrl;
    private LocalDateTime uploadedAt;
}
