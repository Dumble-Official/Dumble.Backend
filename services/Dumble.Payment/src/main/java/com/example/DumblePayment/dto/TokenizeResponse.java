package com.example.DumblePayment.dto;

import com.example.DumblePayment.domain.PaymentMethodToken;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TokenizeResponse {
    private UUID id;
    private String token;
    private String methodType;
    private String label;
    private String cardBrand;
    private String last4;

    public static TokenizeResponse from(PaymentMethodToken t) {
        return TokenizeResponse.builder()
                .id(t.getId())
                .token(t.getToken())
                .methodType(t.getMethodType().name())
                .label(t.getLabel())
                .cardBrand(t.getCardBrand())
                .last4(t.getLast4())
                .build();
    }
}
