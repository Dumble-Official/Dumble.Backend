package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.TableType;
import com.dumble.service.schedule.domain.Weekday;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Reorder one day's list. {@code itemIds} is the full new order of the items in
 * the (tableType, weekday) bucket — it must be exactly those items, just
 * permuted. Positions are re-stamped 0..n-1 in this order.
 */
public record ReorderRequest(
        @NotNull(message = "is required") TableType tableType,
        @NotNull(message = "is required") Weekday weekday,
        @NotEmpty(message = "is required") List<UUID> itemIds) {
}
