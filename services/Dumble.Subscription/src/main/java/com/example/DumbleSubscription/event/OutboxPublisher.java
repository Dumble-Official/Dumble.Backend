package com.example.DumbleSubscription.event;

import com.example.DumbleSubscription.config.RabbitMQConfig;
import com.example.DumbleSubscription.domain.OutboxEvent;
import com.example.DumbleSubscription.domain.enums.OutboxStatus;
import com.example.DumbleSubscription.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drains pending OutboxEvent rows and publishes them to RabbitMQ. Runs every
 * 2 seconds; bounded batch size to avoid long transactions.
 *
 * Failures bump attempts + lastError but do not block subsequent events.
 * After N attempts a row could be moved to FAILED for manual inspection.
 */
@Component
@ConditionalOnProperty(name = "subscription.scheduling.enabled", havingValue = "true", matchIfMissing = true)
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

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = repository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.DUMBLE_EVENTS_EXCHANGE,
                        event.getRoutingKey(),
                        event.getPayloadJson());
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
