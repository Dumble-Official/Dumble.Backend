package com.dumble.service.schedule.dto;

import com.dumble.service.schedule.domain.MealDayTarget;

public record MealTargetView(Integer calories, Integer proteinG, Integer carbsG, Integer fatG) {

    public static MealTargetView from(MealDayTarget t) {
        if (t == null) return null;
        return new MealTargetView(t.getCalories(), t.getProteinG(), t.getCarbsG(), t.getFatG());
    }
}
