package com.example.DumblePayment.event;

import com.example.DumblePayment.config.RabbitMQConfig;
import com.example.DumblePayment.domain.OutboxEvent;
import com.example.DumblePayment.domain.enums.OutboxStatus;
import com.example.DumblePayment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Drains pending OutboxEvent rows and publishes to RabbitMQ. Runs every
 * 2 seconds; bounded batch size to avoid long transactions.
 *
 * Sends raw UTF-8 bytes with explicit {@code application/json} content-type —
 * the Jackson MessageConverter would otherwise re-encode the already-
 * serialized payload as a quoted string literal, breaking every consumer.
 *
 * <p>Confirm-aware lifecycle (Decision 9.4 / bug_023): a row moves
 * PENDING → IN_FLIGHT in {@link OutboxPublishingPersister#claim} BEFORE the
 * broker call so the asynchronous {@link OutboxConfirmCoordinator} can find
 * it when the ack lands.
 */
@Component
@ConditionalOnProperty(name = "payment.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final OutboxPublishingPersister persister;
    private final RabbitTemplate rabbitTemplate;
    private final long inFlightGraceSeconds;

    public OutboxPublisher(OutboxEventRepository repository,
                           OutboxPublishingPersister persister,
                           RabbitTemplate rabbitTemplate,
                           @Value("${payment.outbox.in-flight-grace-seconds:60}") long inFlightGraceSeconds) {
        this.repository = repository;
        this.persister = persister;
        this.rabbitTemplate = rabbitTemplate;
        this.inFlightGraceSeconds = inFlightGraceSeconds;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay-ms:2000}")
    public void drain() {
        // Recovery sweep first — any IN_FLIGHT row that hasn't seen a confirm
        // within the grace window is reset to PENDING and re-published below.
        persister.recoverStuckInFlight(Instant.now().minus(Duration.ofSeconds(inFlightGraceSeconds)));

        List<OutboxEvent> batch = repository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            Optional<OutboxEvent> claimed = persister.claim(event.getId());
            if (claimed.isEmpty()) continue;
            send(claimed.get());
        }
    }

    private void send(OutboxEvent event) {
        String correlationId = event.getId().toString();
        Message msg = MessageBuilder
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
                    msg,
                    new CorrelationData(correlationId));
        } catch (AmqpException ex) {
            log.warn("Outbox event {} send failed synchronously: {}", event.getId(), ex.getMessage());
            persister.onSendFailed(event.getId(), ex.getMessage());
        }
    }
}
