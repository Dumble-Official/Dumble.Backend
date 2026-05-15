package com.example.DumblePayment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ publisher confirms + returns plumbing — see Wallet's matching
 * coordinator for the rationale. Without this, {@code rabbitTemplate.send}
 * returns when bytes hit the AMQP client buffer (NOT broker accept), so
 * outbox rows get stamped PUBLISHED for messages the broker silently
 * discarded.
 */
@Component
public class OutboxConfirmCoordinator
        implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private static final Logger log = LoggerFactory.getLogger(OutboxConfirmCoordinator.class);

    private final OutboxPublishingPersister persister;
    private final ConcurrentHashMap<String, String> returnedReasons = new ConcurrentHashMap<>();

    public OutboxConfirmCoordinator(OutboxPublishingPersister persister) {
        this.persister = persister;
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        String corrId = returned.getMessage().getMessageProperties().getCorrelationId();
        String reason = returned.getReplyCode() + " " + returned.getReplyText()
                + " (exchange=" + returned.getExchange()
                + ", routingKey=" + returned.getRoutingKey() + ")";
        if (corrId == null || corrId.isBlank()) {
            log.warn("Returned message had no correlation id — cannot mark outbox row: {}", reason);
            return;
        }
        returnedReasons.put(corrId, reason);
        log.warn("Outbox event {} returned by broker (will be marked FAILED on confirm): {}", corrId, reason);
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null) return;
        String corrId = correlationData.getId();
        if (corrId == null) return;
        UUID id;
        try {
            id = UUID.fromString(corrId);
        } catch (IllegalArgumentException ex) {
            log.warn("Confirm callback for unparseable correlation id '{}'", corrId);
            return;
        }
        String returnedReason = returnedReasons.remove(corrId);
        try {
            if (returnedReason != null) {
                persister.onReturned(id, returnedReason);
                return;
            }
            if (ack) {
                persister.onConfirmed(id);
            } else {
                persister.onNack(id, cause == null ? "broker_nack" : cause);
            }
        } catch (RuntimeException ex) {
            log.error("Outbox confirm callback failed for {}: {}", corrId, ex.toString(), ex);
        }
    }
}
