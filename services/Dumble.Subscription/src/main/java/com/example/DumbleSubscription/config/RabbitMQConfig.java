package com.example.DumbleSubscription.config;

import com.example.DumbleSubscription.event.OutboxConfirmCoordinator;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
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
 *
 * <p><b>Message-converter scoping</b>: the Jackson2JsonMessageConverter is
 * wired ONLY into the outbound {@link RabbitTemplate}. The inbound listener
 * container intentionally uses {@link SimpleMessageConverter} so Payment's
 * application/json messages arrive at {@link
 * com.example.DumbleSubscription.event.PaymentEventListener#onMessage(String,
 * String)} as the raw JSON string, which the listener parses itself with
 * {@code objectMapper.readTree}. If Jackson2JsonMessageConverter were also
 * the listener converter (Spring Boot auto-wires it when it's the only
 * MessageConverter bean), it tries to deserialize the JSON OBJECT into a
 * String parameter and fails every inbound event — silently breaking the
 * cross-service charge-succeeded / payout-completed / chargeback flows.
 * Same bug fixed in Wallet's RabbitMQConfig earlier this round; the E2E
 * suite caught it here too.
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         @Lazy OutboxConfirmCoordinator confirmCoordinator) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Outbound only: serialize outbox payloads as application/json. The
        // converter lives inline here (not a top-level @Bean) so Spring Boot's
        // SimpleRabbitListenerContainerFactory auto-wiring doesn't also pick
        // it up for inbound — see the listener factory bean below.
        template.setMessageConverter(new Jackson2JsonMessageConverter());
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

    /**
     * Explicit listener container factory wired with SimpleMessageConverter so
     * inbound application/json messages from Payment (and any other publisher
     * on dumble.events) arrive at {@code @RabbitListener String body}
     * parameters as the raw JSON text — exactly what
     * {@link com.example.DumbleSubscription.event.PaymentEventListener}
     * expects. If this bean is omitted Spring Boot auto-configures the
     * factory and any MessageConverter bean in the context becomes the
     * listener converter automatically; that's how the original
     * Jackson2JsonMessageConverter top-level @Bean silently broke inbound
     * consumption of every Payment event.
     */
    @Bean
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }
}
