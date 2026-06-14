package com.dumble.service.schedule.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Subscribes to the shared {@code dumble.events} topic exchange for bundle-
 * subscription lifecycle, which drives the trainer↔client access read-model.
 * Exchange/queue/bindings are declared so they exist regardless of start order.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "dumble.events";
    public static final String LINKS_QUEUE = "schedule.subscription-links";

    public static final String KEY_ACTIVATED = "subscription.bundle.activated";
    public static final String KEY_RENEWED = "subscription.bundle.renewed";
    public static final String KEY_EXPIRED = "subscription.bundle.expired";

    @Bean
    public TopicExchange dumbleEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue subscriptionLinksQueue() {
        return QueueBuilder.durable(LINKS_QUEUE).build();
    }

    @Bean
    public Declarables subscriptionLinkBindings(Queue subscriptionLinksQueue, TopicExchange dumbleEventsExchange) {
        return new Declarables(
                BindingBuilder.bind(subscriptionLinksQueue).to(dumbleEventsExchange).with(KEY_ACTIVATED),
                BindingBuilder.bind(subscriptionLinksQueue).to(dumbleEventsExchange).with(KEY_RENEWED),
                BindingBuilder.bind(subscriptionLinksQueue).to(dumbleEventsExchange).with(KEY_EXPIRED));
    }
}
