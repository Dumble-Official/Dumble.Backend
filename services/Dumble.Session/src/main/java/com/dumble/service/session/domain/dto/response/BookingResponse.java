package com.dumble.service.session.domain.dto.response;

import com.dumble.service.session.domain.enumuration.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BookingResponse {
    private UUID id;
    private UUID sessionId;
    private String sessionTitle;
    private UUID participantId;
    private PaymentStatus paymentStatus;
    private BigDecimal amountPaid;
    private UUID paymentId;
    private String transactionRef;
    private LocalDateTime bookingDate;
}
