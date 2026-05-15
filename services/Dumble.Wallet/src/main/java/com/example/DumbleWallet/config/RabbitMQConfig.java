package com.example.DumbleWallet.config;

import com.example.DumbleWallet.event.OutboxConfirmCoordinator;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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

    /**
     * Without this binding the {@code dumble.events} topic exchange has no
     * queue listening for {@code payment.withdrawal.*} routing keys, so
     * Payment's lifecycle events are silently dropped at the broker and the
     * documented event-driven completion path never fires (only the polling
     * reaper ever rescues users).
     */
    @Bean
    public Binding paymentWithdrawalBinding(Queue walletInboundQueue,
                                            TopicExchange dumbleEventsExchange) {
        return BindingBuilder.bind(walletInboundQueue)
                .to(dumbleEventsExchange)
                .with("payment.withdrawal.*");
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter,
                                         @Lazy OutboxConfirmCoordinator confirmCoordinator) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // Publisher confirms (correlated) — flip outbox PUBLISHED only on
        // broker ack. Without this, send() returns when the AMQP client buffer
        // accepts the bytes, NOT when the broker persists / routes the
        // message. Mandatory + ReturnsCallback catches unroutable messages
        // (e.g. when no queue is bound to the routing key).
        template.setMandatory(true);
        template.setConfirmCallback(confirmCoordinator);
        template.setReturnsCallback(confirmCoordinator);
        return template;
    }
}
