package com.dumble.service.gym.domain.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the optional supporting-document URLs as a JSON array in a single
 * TEXT column on {@code gym_registrations}, instead of a separate join table.
 *
 * <p>Aiven managed MySQL runs with {@code sql_require_primary_key = ON}, which
 * rejects the primary-key-less table a JPA {@code @ElementCollection} generates.
 * Keeping the list inline on the parent row avoids that and also drops the eager
 * extra fetch that loaded the collection on every registration read.
 */
@Converter
public class SupportingDocumentUrlsConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize supporting document URLs", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not read supporting document URLs", e);
        }
    }
}
