package com.example.DumbleWallet.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wallet PDF Decision 6.5 — outbox publishes to a single topic exchange.
 * Consumers (NotificationService, Payment service) bind their own queues.
 *
 * Wallet ALSO consumes events on its own queue ({@code wallet.inbound}) for
 * {@code WithdrawalCompleted} / {@code WithdrawalFailed} from Payment
 * (Decision 6.2).
 */
@Configuration
public class RabbitMQConfig {

    public static final String DUMBLE_EVENTS_EXCHANGE = "dumble.events";
    public static final String WALLET_INBOUND_QUEUE = "wallet.inbound";

    @Bean
    public TopicExchange dumbleEventsExchange() {
        return new TopicExchange(DUMBLE_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue walletInboundQueue() {
        return new Queue(WALLET_INBOUND_QUEUE, true, false, false);
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
