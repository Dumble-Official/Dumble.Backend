package com.example.DumbleWallet.event;

import com.example.DumbleWallet.config.RabbitMQConfig;
import com.example.DumbleWallet.domain.OutboxEvent;
import com.example.DumbleWallet.domain.enums.OutboxStatus;
import com.example.DumbleWallet.repository.OutboxEventRepository;
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
 * Drains pending {@link OutboxEvent} rows and publishes them to RabbitMQ.
 *
 * The payload is sent as raw bytes with explicit JSON content-type so the
 * Jackson converter on the {@link RabbitTemplate} doesn't re-encode the
 * already-serialized JSON string into an escaped string literal.
 *
 * <p>Confirm-aware lifecycle (Decision 6.5 / bug_023): a row moves
 * PENDING → IN_FLIGHT in {@link OutboxPublishingPersister#claim} BEFORE the
 * broker call so the asynchronous {@link OutboxConfirmCoordinator} can find
 * it when the ack lands. Without IN_FLIGHT, a confirm could race the drain's
 * commit and miss its target.
 */
@Component
@ConditionalOnProperty(name = "wallet.scheduling.enabled", havingValue = "true", matchIfMissing = true)
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
                           @Value("${wallet.outbox.in-flight-grace-seconds:60}") long inFlightGraceSeconds) {
        this.repository = repository;
        this.persister = persister;
        this.rabbitTemplate = rabbitTemplate;
        this.inFlightGraceSeconds = inFlightGraceSeconds;
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.publish-delay-ms:2000}")
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
            // Synchronous failure — channel closed, serialization issue, etc.
            // The confirm callback will not fire; treat as nack.
            log.warn("Outbox event {} send failed synchronously: {}", event.getId(), ex.getMessage());
            persister.onSendFailed(event.getId(), ex.getMessage());
        }
    }
}
