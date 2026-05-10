package com.example.DumblePayment.config;

import com.example.DumblePayment.event.OutboxConfirmCoordinator;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Payment publishes to the same {@code dumble.events} topic exchange as the
 * rest of the platform (Decision 9.4). It does not consume any in v1 — Paymob
 * webhooks come in via HTTP, not the broker.
 */
@Configuration
public class RabbitMQConfig {

    public static final String DUMBLE_EVENTS_EXCHANGE = "dumble.events";

    @Bean
    public TopicExchange dumbleEventsExchange() {
        return new TopicExchange(DUMBLE_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         @Lazy OutboxConfirmCoordinator confirmCoordinator) {
        // No converter — OutboxPublisher sends pre-serialized JSON bytes with
        // an explicit content-type header, matching the Wallet / Subscription
        // pattern (avoids the "Jackson re-encodes the already-JSON string"
        // bug that bit those services).
        //
        // Publisher confirms (correlated) — flip outbox PUBLISHED only on
        // broker ack. Without this, send() returns when the AMQP client buffer
        // accepts the bytes, NOT when the broker persists / routes the
        // message. Mandatory + ReturnsCallback catches unroutable messages.
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback(confirmCoordinator);
        template.setReturnsCallback(confirmCoordinator);
        return template;
    }
}
