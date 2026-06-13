package com.dumble.service.gym.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Admin's message when sending a registration back for changes or rejecting it.
 */
@Getter
@Setter
public class ReviewMessageRequest {

    @NotBlank(message = "A message is required")
    @Size(max = 1000)
    private String message;
}
