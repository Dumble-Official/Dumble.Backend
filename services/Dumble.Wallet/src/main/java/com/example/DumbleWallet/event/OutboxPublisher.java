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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Drains pending {@link OutboxEvent} rows and publishes them to RabbitMQ.
 *
 * The payload is sent as raw bytes with explicit JSON content-type so the
 * Jackson converter on the {@link RabbitTemplate} doesn't re-encode the
 * already-serialized JSON string into an escaped string literal.
 */
@Component
@ConditionalOnProperty(name = "wallet.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.publish-delay-ms:2000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = repository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            try {
                Message msg = MessageBuilder
                        .withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                        .andProperties(MessagePropertiesBuilder.newInstance()
                                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                                .setContentEncoding(StandardCharsets.UTF_8.name())
                                .build())
                        .build();
                rabbitTemplate.send(
                        RabbitMQConfig.DUMBLE_EVENTS_EXCHANGE,
                        event.getRoutingKey(),
                        msg);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
            } catch (AmqpException ex) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(ex.getMessage(), 1900));
                if (event.getAttempts() >= MAX_ATTEMPTS) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} permanently FAILED after {} attempts: {}",
                            event.getId(), event.getAttempts(), ex.getMessage());
                }
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
