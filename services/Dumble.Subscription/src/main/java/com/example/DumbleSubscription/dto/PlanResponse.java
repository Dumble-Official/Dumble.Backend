package com.example.DumbleSubscription.dto;

import com.example.DumbleSubscription.domain.Plan;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanResponse {
    private String code;
    private String name;
    private long priceCents;
    private String currency;
    private boolean canUseChatbot;
    private Integer chatbotMessagesPerDay;
    private boolean canDmAnyone;

    public static PlanResponse from(Plan p) {
        return PlanResponse.builder()
                .code(p.getCode().name())
                .name(p.getName())
                .priceCents(p.getPriceCents())
                .currency(p.getCurrency())
                .canUseChatbot(p.isCanUseChatbot())
                .chatbotMessagesPerDay(p.getChatbotMessagesPerDay())
                .canDmAnyone(p.isCanDmAnyone())
                .build();
    }
}
