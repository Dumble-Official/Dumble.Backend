package com.example.DumbleAuthentication.event;

import com.example.DumbleAuthentication.config.RabbitMQConfig;
import com.example.DumbleAuthentication.domain.OutboxEvent;
import com.example.DumbleAuthentication.domain.OutboxStatus;
import com.example.DumbleAuthentication.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Drains PENDING {@link OutboxEvent} rows and publishes them to the shared dumble.events topic
 * exchange. The body is the already-serialized JSON sent as raw bytes with an application/json
 * content-type, so the .NET consumers (raw-JSON serializer) see the plain object — matching how the
 * Subscription service publishes. A row that fails to send stays PENDING and is retried on the next
 * tick; delivery is therefore at-least-once, which is safe because the consumers (DeleteUser,
 * soft-delete posts) are idempotent.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${auth.outbox.publish-delay-ms:2000}")
    public void drain() {
        List<OutboxEvent> batch = repository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            send(event);
        }
    }

    private void send(OutboxEvent event) {
        String correlationId = event.getId().toString();
        Message message = MessageBuilder
                .withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                .andProperties(MessagePropertiesBuilder.newInstance()
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setCorrelationId(correlationId)
                        .build())
                .build();
        try {
            rabbitTemplate.send(
                    RabbitMQConfig.DUMBLE_EVENTS_EXCHANGE,
                    event.getRoutingKey(),
                    message,
                    new CorrelationData(correlationId));
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            repository.save(event);
        } catch (AmqpException ex) {
            // Broker unreachable / channel error — leave PENDING and retry next tick.
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(ex.getMessage());
            repository.save(event);
            log.warn("Outbox event {} send failed (attempt {}): {}",
                    event.getId(), event.getAttempts(), ex.getMessage());
        }
    }
}
