package com.dumble.service.schedule.messaging;

import com.dumble.service.schedule.service.ScheduleService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps the trainer↔client access read-model in sync with the Subscription
 * service. An active bundle sub with a TRAINER seller → active coaching link;
 * expiry → inactive. This is the production feed behind the Slice 3 gate
 * (the internal upsert endpoint remains for ops/manual sync). Gym-seller bundles
 * are ignored — they don't grant personal schedule coaching.
 */
@Component
public class SubscriptionLinkConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLinkConsumer.class);

    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    public SubscriptionLinkConsumer(ScheduleService scheduleService, ObjectMapper objectMapper) {
        this.scheduleService = scheduleService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.LINKS_QUEUE)
    public void onBundleSubscriptionEvent(Message message,
                                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            BundleSubEvent ev = objectMapper.readValue(message.getBody(), BundleSubEvent.class);
            if (ev.sellerId() == null || ev.participantId() == null) {
                log.warn("Bundle event [{}] missing sellerId/participantId; skipping", routingKey);
                return;
            }
            if (!"TRAINER".equalsIgnoreCase(ev.sellerType())) {
                return; // gym-seller bundles don't grant personal coaching schedules
            }
            boolean active = !RabbitConfig.KEY_EXPIRED.equals(routingKey); // activated/renewed → true; expired → false
            scheduleService.upsertTrainerLink(ev.sellerId(), ev.participantId(), active);
            log.info("Trainer link {} -> trainer={} client={} (from {})",
                    active ? "ACTIVE" : "inactive", ev.sellerId(), ev.participantId(), routingKey);
        } catch (Exception e) {
            // Don't requeue a poison message into an infinite loop — log and drop.
            log.error("Failed to handle bundle event [{}]: {}", routingKey, e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BundleSubEvent(UUID participantId, UUID sellerId, String sellerType, String status) {
    }
}
