package com.example.DumbleSubscription.config;

import com.example.DumbleSubscription.event.OutboxConfirmCoordinator;
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
 * Subscription publishes domain events to a single topic exchange. Consumers
 * (NotificationService, audit collectors, etc.) bind their own queues with
 * routing-key patterns matching the event names.
 *
 * Subscription also consumes events FROM Payment on its own
 * {@code subscription.inbound} queue — payout confirmations, charge
 * confirmations after Paymob OTP, chargebacks. Those bindings live below.
 */
@Configuration
public class RabbitMQConfig {

    public static final String DUMBLE_EVENTS_EXCHANGE = "dumble.events";
    public static final String SUBSCRIPTION_INBOUND_QUEUE = "subscription.inbound";

    @Bean
    public TopicExchange dumbleEventsExchange() {
        return new TopicExchange(DUMBLE_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue subscriptionInboundQueue() {
        return new Queue(SUBSCRIPTION_INBOUND_QUEUE, true, false, false);
    }

    /**
     * Without this binding the {@code dumble.events} topic exchange has no
     * queue listening for {@code payment.*} routing keys, so every payout
     * confirmation, OTP-completed charge, and chargeback Payment publishes is
     * silently dropped at the broker and {@link com.example.DumbleSubscription.event.PaymentEventListener}
     * never fires. That breaks escrow → PAID_OUT transitions, the Paymob
     * Pending→Active flow (bug_029 from review-pr4-run2), and the chargeback
     * refund path (Decision 6.2).
     *
     * Pattern is {@code payment.#} (multi-segment), not {@code payment.*}
     * (single-segment): Payment emits three-segment routing keys like
     * {@code payment.charge.succeeded}, {@code payment.payout.completed},
     * {@code payment.chargeback.filed}. AMQP topic {@code *} matches exactly
     * one word, so {@code payment.*} would drop every event the listener
     * actually cares about.
     */
    @Bean
    public Binding paymentEventsBinding(Queue subscriptionInboundQueue,
                                        TopicExchange dumbleEventsExchange) {
        return BindingBuilder.bind(subscriptionInboundQueue)
                .to(dumbleEventsExchange)
                .with("payment.#");
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
