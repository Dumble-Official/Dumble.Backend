package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.Weekday;

import java.util.List;

/** One day's slot in a table: its ordered items, plus the meal target (meals only). */
public record DayView(
        Weekday weekday,
        List<ItemView> items,
        MealTargetView target) {
}
