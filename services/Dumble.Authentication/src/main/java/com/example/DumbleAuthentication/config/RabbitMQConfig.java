package com.example.DumbleAuthentication.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth publishes to the shared {@code dumble.events} topic exchange that the rest of the platform
 * already uses (same exchange the Subscription service publishes to and NotificationService /
 * RecommendationService consume from). Auth only publishes — it has no inbound listeners — so this
 * is just the exchange declaration plus a mandatory-publish RabbitTemplate.
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
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Surface unroutable messages instead of silently dropping them at the broker.
        template.setMandatory(true);
        return template;
    }
}
