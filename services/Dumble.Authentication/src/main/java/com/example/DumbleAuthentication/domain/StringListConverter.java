package com.example.DumbleAuthentication.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/**
 * Persists a {@code List<String>} into the existing {@code fitness_goals}
 * VARCHAR column as a comma-separated string, so the onboarding API can accept
 * multiple goals (e.g. ["MUSCLE_GAIN","ENDURANCE"]) without adding a join
 * table. Values are short uppercase codes with no commas, so splitting on ','
 * is safe. Null/blank round-trips to an empty list.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dbData.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
