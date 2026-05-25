package com.dumble.service.session.client;

import com.dumble.service.session.config.FeignClientConfig;
import com.dumble.service.session.domain.dto.ChargeRequest;
import com.dumble.service.session.domain.dto.ChargeResponse;
import com.dumble.service.session.domain.dto.RefundRequest;
import com.dumble.service.session.domain.dto.RefundResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


@FeignClient(
        name = "payment-service",
        url = "${feign.client.config.payment-service.url}",
        configuration = FeignClientConfig.class
)
public interface PaymentClient {

    @PostMapping("/payment/charges")
    ChargeResponse charge(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ChargeRequest request
    );

    @PostMapping("/payment/refunds")
    RefundResponse refund(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RefundRequest request
    );
}