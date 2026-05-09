package com.example.DumbleSubscription.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Subscription publishes domain events to a single topic exchange. Consumers
 * (NotificationService, audit collectors, etc.) bind their own queues with
 * routing-key patterns matching the event names.
 *
 * Outgoing only for now — Subscription doesn't consume events from other
 * services in v1 (it consumes events FROM Payment via the Payment integration
 * but that wiring lives near {@code PaymentEventConsumer}, added when needed).
 */
@Configuration
public class RabbitMQConfig {

    public static final String DUMBLE_EVENTS_EXCHANGE = "dumble.events";

    @Bean
    public TopicExchange dumbleEventsExchange() {
        return new TopicExchange(DUMBLE_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue subscriptionInboundQueue() {
        // Queue Subscription listens on for events from Payment / Wallet
        return new Queue("subscription.inbound", true, false, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
