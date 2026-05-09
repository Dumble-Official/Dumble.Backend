package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.Receipt;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.repository.ReceiptRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per PDF Decisions 11.5 + 11.6 — Subscription owns receipt data; PDF rendering
 * is deferred (Section 22). For now we store the structured items_json and let
 * NotificationService (or a future renderer) format into bilingual layout.
 */
@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ObjectMapper objectMapper;
    private final OutboxWriter outboxWriter;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ObjectMapper objectMapper,
                          OutboxWriter outboxWriter) {
        this.receiptRepository = receiptRepository;
        this.objectMapper = objectMapper;
        this.outboxWriter = outboxWriter;
    }

    public Receipt issueForBundleSubscription(UUID userId,
                                              UUID subscriptionId,
                                              String transactionId,
                                              long amountCents,
                                              String currency,
                                              String bundleName,
                                              int durationDays) {
        return persist(userId, subscriptionId, "BUNDLE", transactionId, amountCents, currency,
                List.of(Map.of(
                        "description", bundleName,
                        "duration_days", durationDays,
                        "amount_cents", amountCents,
                        "currency", currency,
                        // Bilingual placeholders for future renderer (Decision 11.6).
                        "description_ar", bundleName)));
    }

    public Receipt issueForPlatformSubscription(UUID userId,
                                                UUID subscriptionId,
                                                String transactionId,
                                                long amountCents,
                                                String currency) {
        return persist(userId, subscriptionId, "PLATFORM", transactionId, amountCents, currency,
                List.of(Map.of(
                        "description", "Pro membership — 1 month",
                        "amount_cents", amountCents,
                        "currency", currency,
                        "description_ar", "اشتراك برو — شهر واحد")));
    }

    private Receipt persist(UUID userId,
                            UUID subscriptionId,
                            String subjectType,
                            String transactionId,
                            long amountCents,
                            String currency,
                            List<Map<String, Object>> items) {
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt.setTransactionId(transactionId);
        receipt.setAmountCents(amountCents);
        receipt.setCurrency(currency);
        receipt.setItemsJson(toJson(items));
        receipt.setIssuedAt(Instant.now());
        receipt.setSubjectSubscriptionId(subscriptionId);
        receipt.setSubjectType(subjectType);
        receiptRepository.save(receipt);

        outboxWriter.write("ReceiptIssued", "subscription.receipt.issued",
                Map.of("receiptId", receipt.getId(),
                        "userId", userId,
                        "amountCents", amountCents,
                        "currency", currency));
        return receipt;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
