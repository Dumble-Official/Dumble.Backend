package com.dumble.service.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Internal: the chatbot pushes generated items for a pro client. replace=true
 * clears the chatbot's prior items first (regeneration) — trainer/client items
 * are untouched.
 */
public record ChatbotApplyRequest(
        boolean replace,
        @NotEmpty(message = "must not be empty") @Valid List<AddItemRequest> items) {
}
