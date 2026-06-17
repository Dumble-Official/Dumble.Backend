package com.dumble.service.session.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class RefundRequest {
    private UUID chargeId;
    private long amountCents;
    private String destination; // "WALLET"
    private String reason;
}
