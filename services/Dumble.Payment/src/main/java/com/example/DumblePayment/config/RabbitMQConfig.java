package com.example.DumblePayment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // No converter — OutboxPublisher sends pre-serialized JSON bytes with
        // an explicit content-type header, matching the Wallet / Subscription
        // pattern (avoids the "Jackson re-encodes the already-JSON string"
        // bug that bit those services).
        return new RabbitTemplate(connectionFactory);
    }
}
