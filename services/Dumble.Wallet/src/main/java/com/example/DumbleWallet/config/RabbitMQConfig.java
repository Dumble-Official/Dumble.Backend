package com.example.DumbleWallet.config;

import com.example.DumbleWallet.event.OutboxConfirmCoordinator;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
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
 *
 * <p><b>Message-converter scoping</b>: the Jackson2JsonMessageConverter is
 * wired ONLY into the outbound {@link RabbitTemplate}. The inbound listener
 * container intentionally uses {@link SimpleMessageConverter} so Payment's
 * application/json messages arrive at {@link
 * com.example.DumbleWallet.event.PaymentEventListener#onMessage(String,
 * String)} as the raw JSON string, which the listener parses itself with
 * {@code objectMapper.readTree}. If Jackson2JsonMessageConverter were also
 * the listener converter (Spring Boot auto-wires it when it's the only
 * MessageConverter bean), it would try to deserialize the JSON OBJECT into a
 * String parameter and fail every inbound event — silently breaking the
 * cross-service withdrawal-completed flow.
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         @Lazy OutboxConfirmCoordinator confirmCoordinator) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Outbound only: serialize outbox payloads as application/json. The
        // converter lives inline here (not a top-level @Bean) so Spring Boot's
        // SimpleRabbitListenerContainerFactory auto-wiring doesn't also pick
        // it up for inbound — see the SimpleRabbitListenerContainerFactory
        // bean below.
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
     * parameters as the raw JSON text — exactly what the listener expects.
     * If this bean is omitted Spring Boot auto-configures the factory and
     * any MessageConverter bean in the context becomes the listener converter
     * automatically; that's how the original Jackson2JsonMessageConverter
     * top-level @Bean ended up silently breaking inbound consumption.
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
