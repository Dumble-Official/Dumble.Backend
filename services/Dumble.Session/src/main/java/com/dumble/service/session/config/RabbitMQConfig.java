package com.dumble.service.session.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "dumble.events";
    public static final String QUEUE_NAME = "session.inbound";
    public static final String DLQ_NAME = "session.inbound.dlq";
    public static final String DLX_NAME = "session.dlx";

    @Bean
    public TopicExchange dumbleEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue sessionInboundDLQ() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(sessionInboundDLQ()).to(deadLetterExchange()).with("#");
    }

    @Bean
    public Queue sessionInboundQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_NAME);
        return QueueBuilder.durable(QUEUE_NAME).withArguments(args).build();
    }

    @Bean
    public Binding paymentChargeSucceededBinding(Queue sessionInboundQueue, TopicExchange dumbleEventsExchange) {
        return BindingBuilder.bind(sessionInboundQueue).to(dumbleEventsExchange).with("payment.charge.*");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}