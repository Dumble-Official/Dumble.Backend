package com.dumble.service.session.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class RefundResponse {
    private UUID refundId;
    private String status;
}
