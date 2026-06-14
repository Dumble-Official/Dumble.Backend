package com.dumble.service.schedule.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CompletionView(UUID itemId, LocalDate date, boolean done) {
}
