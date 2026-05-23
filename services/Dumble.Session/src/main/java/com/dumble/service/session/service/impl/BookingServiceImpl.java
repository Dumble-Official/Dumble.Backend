package com.dumble.service.session.service.impl;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final BookingMapper bookingMapper;

    @Override
    public BookingResponse createBooking(BookingCreateRequest request, UUID participantId) {
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if(session.getCurrentParticipants() >= session.getMaxCapacity()){
            throw new BadRequestException("Sorry, this session is already at full capacity.");
        }

        boolean alreadyBooked = bookingRepository.existsBySessionIdAndParticipantIdAndPaymentStatusIn(
                session.getId(), participantId, Arrays.asList(PaymentStatus.PENDING, PaymentStatus.CONFIRMED));

        if (alreadyBooked) {
            throw new DuplicateResourceException("You already have an active booking for this session.");
        }

        Booking booking = Booking.builder()
                .session(session)
                .participantId(participantId)
                .amountPaid(session.getPrice())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

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
    public void cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            Session session = booking.getSession();
            session.setCurrentParticipants(Math.max(0, session.getCurrentParticipants() - 1));
            sessionRepository.save(session);
        }

        booking.setPaymentStatus(PaymentStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    //@TODO
    @Override
    public void confirmPayment(UUID paymentId, String transactionRef) {
        
    }
}

