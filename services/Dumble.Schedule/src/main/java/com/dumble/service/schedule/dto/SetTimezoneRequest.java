package com.dumble.service.schedule.dto;

import jakarta.validation.constraints.NotBlank;

/** Sets the schedule's IANA timezone (e.g. "Africa/Cairo"), used for per-user-local reminders. */
public record SetTimezoneRequest(
        @NotBlank(message = "timezone is required") String timezone) {
}
