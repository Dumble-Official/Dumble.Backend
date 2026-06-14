package com.dumble.service.schedule.messaging;

import java.util.UUID;

/**
 * Emitted on routing key {@code schedule.reminder.due} for the Notification
 * service to deliver ("you still have {pendingCount} items to do today").
 */
public record ReminderEvent(UUID userId, String date, int pendingCount, String timezone) {
}
