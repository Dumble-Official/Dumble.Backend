package com.dumble.service.session.event;

import com.rabbitmq.client.Channel;
import com.dumble.service.session.domain.entity.ProcessedEvent;
import com.dumble.service.session.repository.ProcessedEventRepository;
import com.dumble.service.session.service.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private static final String CONSUMER_TYPE = "PaymentEventListener";

    private final BookingService bookingService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${session.rabbitmq.inbound-queue:session.inbound}", containerFactory = "rabbitListenerContainerFactory")
    public void onPaymentEvent(
            String messageBody,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Channel channel) throws IOException {

        log.info("Received event [{}] (messageId={}) with body: {}", routingKey, messageId, messageBody);

        try {
            // Rabbit guarantees at-least-once delivery — a network blip or a
            // consumer NACK can produce a redelivery of an event we've
            // already processed. Without dedup, that's a double-increment of
            // currentParticipants on the same booking.
            //
            // When messageId is missing (legacy publishers), we fall back to
            // process-and-log so we don't drop legitimate events; the
            // duplicate risk lives at the publisher in that case.
            if (messageId != null && processedEventRepository.existsById(messageId)) {
                log.info("Duplicate inbound event {} ignored (already processed)", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }

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

            // Persist dedup AFTER the side-effect so a mid-flight crash
            // leaves the row absent → next redelivery retries the work.
            // A concurrent peer that also slipped past the existsById check
            // will collide on the PK; catch and treat as already-processed.
            if (messageId != null) {
                try {
                    processedEventRepository.save(ProcessedEvent.builder()
                            .messageId(messageId)
                            .routingKey(routingKey)
                            .consumerType(CONSUMER_TYPE)
                            .build());
                } catch (DataIntegrityViolationException dup) {
                    log.info("Concurrent peer beat us to the dedup row for messageId={}; that's fine", messageId);
                }
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process inbound RabbitMQ payment event. Rejecting to DLQ.", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}