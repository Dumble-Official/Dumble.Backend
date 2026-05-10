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
 * Drains pending OutboxEvent rows and publishes to RabbitMQ. Runs every
 * 2 seconds; bounded batch size to avoid long transactions.
 *
 * Sends raw UTF-8 bytes with explicit {@code application/json} content-type —
 * the Jackson MessageConverter would otherwise re-encode the already-
 * serialized payload as a quoted string literal, breaking every consumer.
 */
@Component
@ConditionalOnProperty(name = "payment.scheduling.enabled", havingValue = "true", matchIfMissing = true)
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

    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay-ms:2000}")
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
