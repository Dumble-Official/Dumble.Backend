package com.dumble.service.session.service.impl;

import com.dumble.service.session.client.PaymentClient;
import com.dumble.service.session.domain.dto.ChargeRequest;
import com.dumble.service.session.domain.dto.ChargeResponse;
import com.dumble.service.session.domain.dto.RefundRequest;
import com.dumble.service.session.domain.dto.request.BookingCreateRequest;
import com.dumble.service.session.domain.dto.response.BookingResponse;
import com.dumble.service.session.domain.entity.Booking;
import com.dumble.service.session.domain.entity.Session;
import com.dumble.service.session.domain.enumuration.PaymentStatus;
import com.dumble.service.session.domain.mapper.BookingMapper;
import com.dumble.service.session.exception.BadRequestException;
import com.dumble.service.session.exception.DuplicateResourceException;
import com.dumble.service.session.exception.ResourceNotFoundException;
import com.dumble.service.session.repository.BookingRepository;
import com.dumble.service.session.repository.SessionRepository;
import com.dumble.service.session.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // M12: Default read-only to save resources
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final BookingMapper bookingMapper;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request, UUID participantId) {

        Booking booking = savePendingBookingInTx(request, participantId);

        String idempotencyKey = "session-booking-" + booking.getId();

        ChargeRequest chargeReq = new ChargeRequest();
        chargeReq.setUserId(participantId);
        chargeReq.setAmountCents(booking.getAmountPaid().multiply(new BigDecimal(100)).longValue()); // تحويل القرش/السنت لو السيستم شغال كده
        chargeReq.setCurrency("EGP");
        chargeReq.setDescription("Booking for Session ID: " + request.getSessionId());
        chargeReq.setCallerReference(booking.getId().toString()); // Decision 3.1

        try {
            log.info("Dispatching charge request to payment service for booking: {}", booking.getId());
            ChargeResponse chargeRes = paymentClient.charge(idempotencyKey, chargeReq);

            return updateBookingAfterChargeResponse(booking.getId(), chargeRes);

        } catch (Exception e) {
            log.error("Payment service communication failed for booking: {}. Leaving PENDING for webhook async fallback.", booking.getId(), e);
            return bookingMapper.toResponse(booking);
        }
    }

    @Transactional
    public Booking savePendingBookingInTx(BookingCreateRequest request, UUID participantId) {
        Session session = sessionRepository.findByIdForUpdate(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (session.getCurrentParticipants() >= session.getMaxCapacity()) {
            throw new BadRequestException("Sorry, this session is already at full capacity.");
        }

        boolean alreadyBooked = bookingRepository.existsBySessionIdAndParticipantIdAndPaymentStatusIn(
                session.getId(), participantId, List.of(PaymentStatus.PENDING, PaymentStatus.CONFIRMED));

        if (alreadyBooked) {
            throw new DuplicateResourceException("You already have an active booking for this session.");
        }

        Booking booking = Booking.builder()
                .session(session)
                .participantId(participantId)
                .amountPaid(session.getPrice())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        return bookingRepository.save(booking);
    }

    @Transactional
    public BookingResponse updateBookingAfterChargeResponse(UUID bookingId, ChargeResponse chargeRes) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        booking.setPaymentId(chargeRes.getChargeId());

        if ("Succeeded".equalsIgnoreCase(chargeRes.getStatus())) {
            booking.setPaymentStatus(PaymentStatus.CONFIRMED);

            Session session = booking.getSession();
            session.setCurrentParticipants(session.getCurrentParticipants() + 1);
            sessionRepository.save(session);

        } else if ("Failed".equalsIgnoreCase(chargeRes.getStatus())) {
            booking.setPaymentStatus(PaymentStatus.CANCELLED);
        }

        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Override
    public BookingResponse getBookingDetails(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(bookingMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Override
    public List<BookingResponse> getParticipantBookings(UUID participantId) {
        return bookingRepository.findByParticipantId(participantId)
                .stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            Session session = booking.getSession();
            session.setCurrentParticipants(Math.max(0, session.getCurrentParticipants() - 1));
            sessionRepository.save(session);

            try {
                RefundRequest refundReq = new RefundRequest();
                refundReq.setChargeId(booking.getPaymentId());
                refundReq.setAmountCents(booking.getAmountPaid().multiply(new BigDecimal(100)).longValue());
                refundReq.setDestination("WALLET");
                refundReq.setReason("Cancelled by participant");

                String refundIdemKey = "session-refund-" + booking.getId();
                paymentClient.refund(refundIdemKey, refundReq);
                log.info("Refund request dispatched successfully for booking: {}", bookingId);
            } catch (Exception e) {
                log.error("Failed to trigger automatic refund for booking: {}", bookingId, e);
            }
        }

        booking.setPaymentStatus(PaymentStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    @Override
    @Transactional
    public void confirmPayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking from Async Webhook not found"));

        if (booking.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            log.info("Booking {} already confirmed synchronously. Skipping async confirmation.", bookingId);
            return;
        }

        if (booking.getPaymentStatus() == PaymentStatus.PENDING) {
            booking.setPaymentStatus(PaymentStatus.CONFIRMED);

            Session session = booking.getSession();
            session.setCurrentParticipants(session.getCurrentParticipants() + 1);
            sessionRepository.save(session);

            bookingRepository.save(booking);
            log.info("Booking {} successfully CONFIRMED via Async Event.", bookingId);

        }
    }
}