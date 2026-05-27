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
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final BookingMapper bookingMapper;
    private final PaymentClient paymentClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(readOnly = false)
    public BookingResponse createBooking(BookingCreateRequest request, UUID participantId) {

        Booking booking = transactionTemplate.execute(status -> savePendingBookingInTx(request, participantId));

        if (booking == null) {
            throw new BadRequestException("Could not initiate booking.");
        }

        String idempotencyKey = "session-booking-" + booking.getId();
        long cents = booking.getAmountPaid()
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValueExact();

        ChargeRequest chargeReq = new ChargeRequest();
        chargeReq.setUserId(participantId);
        chargeReq.setAmountCents(cents);
        chargeReq.setCurrency("EGP");
        chargeReq.setDescription("Booking for Session ID: " + request.getSessionId());
        chargeReq.setCallerReference(booking.getId().toString());

        try {
            log.info("Dispatching charge request outside database transaction for booking: {}", booking.getId());
            ChargeResponse chargeRes = paymentClient.charge(idempotencyKey, chargeReq);

            return transactionTemplate.execute(status -> updateBookingAfterChargeResponse(booking.getId(), chargeRes));

        } catch (FeignException e) {
            log.error("Payment service communication failed for booking: {}. Leaving PENDING for webhook async fallback.", booking.getId(), e);
            return bookingMapper.toResponse(booking);
        }
    }

    @Transactional(readOnly = false)
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

        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Concurrent booking detected. You already have an active booking.");
        }
    }

    public BookingResponse updateBookingAfterChargeResponse(UUID bookingId, ChargeResponse chargeRes) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        booking.setPaymentId(chargeRes.getChargeId());

        if ("Succeeded".equalsIgnoreCase(chargeRes.getStatus())) {
            booking.setPaymentStatus(PaymentStatus.CONFIRMED);

            int updatedRows = sessionRepository.incrementParticipants(booking.getSession().getId());
            if (updatedRows == 0) {
                booking.setPaymentStatus(PaymentStatus.CANCELLED);
                log.warn("Session full at final verification. Flipping booking {} to CANCELLED.", bookingId);
            }

        } else if ("Failed".equalsIgnoreCase(chargeRes.getStatus())) {
            booking.setPaymentStatus(PaymentStatus.CANCELLED);
        }

        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    public Page<BookingResponse> getParticipantBookingsPage(UUID participantId, Pageable pageable) {
        return bookingRepository.findByParticipantId(participantId, pageable)
                .map(bookingMapper::toResponse);
    }

    @Override
    @Transactional
    public void cancelBookingSecure(UUID bookingId, UUID callerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getParticipantId().equals(callerId)) {
            throw new ResourceNotFoundException("Booking not found");
        }

        if (booking.getPaymentStatus() == PaymentStatus.CANCELLED) {
            log.info("Booking {} is already CANCELLED. Skipping duplicate logic.", bookingId);
            return;
        }

        if (booking.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            sessionRepository.decrementParticipants(booking.getSession().getId());

            try {
                long cents = booking.getAmountPaid()
                        .movePointRight(2)
                        .setScale(0, RoundingMode.HALF_EVEN)
                        .longValueExact();

                RefundRequest refundReq = new RefundRequest();
                refundReq.setChargeId(booking.getPaymentId());
                refundReq.setAmountCents(cents);
                refundReq.setDestination("WALLET");
                refundReq.setReason("Cancelled by participant");

                String refundIdemKey = "session-refund-" + booking.getId();
                paymentClient.refund(refundIdemKey, refundReq);
                log.info("Refund request dispatched successfully for booking: {}", bookingId);
            } catch (Exception e) {
                log.error("Failed to trigger automatic refund. Rolling back status to prevent money loss.", e);
                throw new BadRequestException("Refund failed. Please try again later or contact support.");
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
            return;
        }

        if (booking.getPaymentStatus() == PaymentStatus.PENDING) {
            booking.setPaymentStatus(PaymentStatus.CONFIRMED);

            sessionRepository.incrementParticipants(booking.getSession().getId());
            bookingRepository.save(booking);
            log.info("Booking {} successfully CONFIRMED via Async Event.", bookingId);
        }
    }

    @Override
    @Transactional
    public void failPayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking from Async Webhook not found"));

        if (booking.getPaymentStatus() == PaymentStatus.PENDING) {
            booking.setPaymentStatus(PaymentStatus.CANCELLED);
            bookingRepository.save(booking);
            log.info("Booking {} successfully CANCELLED via Async Failure Event.", bookingId);
        }
    }

    @Override
    public BookingResponse getBookingDetailsSecure(UUID bookingId, UUID callerId) { // 💡 شيلنا الـ isAdmin
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getParticipantId().equals(callerId)) {
            throw new BadRequestException("You are not authorized to view this booking.");
        }

        return bookingMapper.toResponse(booking);
    }
}