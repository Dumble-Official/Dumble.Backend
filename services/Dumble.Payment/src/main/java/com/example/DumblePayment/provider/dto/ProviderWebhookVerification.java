package com.example.DumblePayment.provider.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderWebhookVerification {
    private boolean valid;
    /** Paymob's event id, parsed out of the body — used as the dedup PK (Decision 4.2). */
    private String eventId;
    private String eventType;
    private String reason;          // populated when valid=false
}
