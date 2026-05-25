package com.dumble.service.session.event;

import com.rabbitmq.client.Channel;
import com.dumble.service.session.service.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${session.rabbitmq.inbound-queue:session.inbound}", containerFactory = "rabbitListenerContainerFactory")
    public void onPaymentEvent(
            String messageBody,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Channel channel) throws IOException {

        log.info("Received event from RabbitMQ with routing key [{}]: {}", routingKey, messageBody);

        try {
            JsonNode root = objectMapper.readTree(messageBody);

            if (root.has("callerReference")) {
                UUID bookingId = UUID.fromString(root.get("callerReference").asText());

                switch (routingKey) {
                    case "payment.charge.succeeded":
                        log.info("Asynchronous payment success confirmed for booking {}", bookingId);
                        bookingService.confirmPayment(bookingId);
                        break;

                    case "payment.charge.failed":
                        log.warn("Asynchronous payment failure received for booking {}", bookingId);
                        bookingService.failPayment(bookingId);
                        break;

                    default:
                        log.debug("Unrecognized pattern routing key: {}", routingKey);
                }
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process inbound RabbitMQ payment event. Rejecting to DLQ.", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}