package com.dumble.service.session.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class ChargeRequest {
    private UUID userId;
    private long amountCents;
    private String currency;
    private String description;
    private String callerReference;
}
