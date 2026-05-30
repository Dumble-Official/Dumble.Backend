package com.example.DumbleAuthentication.event;

import com.example.DumbleAuthentication.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes account lifecycle events to the {@code dumble.events} topic exchange. The body is the
 * already-serialized JSON sent as raw bytes with an explicit application/json content-type, so the
 * .NET consumers (which read the message off the topic exchange with a raw-JSON serializer) see the
 * plain object — matching how the Subscription service publishes.
 */
@Component
public class AccountEventPublisher {

    public static final String ACCOUNT_DELETED_ROUTING_KEY = "account.deleted";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AccountEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishAccountDeleted(UUID userId, Instant deletedAt) {
        // LinkedHashMap to keep the wire field order stable; camelCase keys match the .NET
        // AccountDeletedEvent's [JsonPropertyName] mapping.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put("deletedAt", deletedAt.toString());

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AccountDeleted event", e);
        }

        String correlationId = userId.toString();
        Message message = MessageBuilder
                .withBody(body)
                .andProperties(MessagePropertiesBuilder.newInstance()
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setCorrelationId(correlationId)
                        .build())
                .build();

        rabbitTemplate.send(
                RabbitMQConfig.DUMBLE_EVENTS_EXCHANGE,
                ACCOUNT_DELETED_ROUTING_KEY,
                message,
                new CorrelationData(correlationId));
    }
}
