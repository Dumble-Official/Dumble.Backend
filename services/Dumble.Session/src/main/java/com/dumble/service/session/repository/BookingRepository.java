package com.dumble.service.session.repository;

import com.dumble.service.session.domain.entity.Booking;
import com.dumble.service.session.domain.enumuration.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findBySessionId(UUID sessionId);

    @Query("SELECT b FROM Booking b WHERE b.session.id = :sessionId AND b.paymentStatus = 'CONFIRMED'")
    List<Booking> findConfirmedBookingsForSession(@Param("sessionId") UUID sessionId);

    boolean existsBySessionIdAndParticipantIdAndPaymentStatusIn(
            UUID sessionId,
            UUID participantId,
            List<PaymentStatus> statuses
    );

    Page<Booking> findByParticipantId(UUID participantId, Pageable pageable);

    long countBySessionIdAndPaymentStatus(UUID sessionId, PaymentStatus status);
}
