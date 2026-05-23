package com.dumble.service.session.domain.entity;

import com.dumble.service.session.domain.enumuration.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false)
    private UUID participantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    private UUID paymentId;

    private String transactionRef;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime bookingDate;
}
