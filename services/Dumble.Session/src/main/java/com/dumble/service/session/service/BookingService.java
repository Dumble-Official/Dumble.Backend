package com.dumble.service.session.service;

import com.dumble.service.session.domain.dto.request.BookingCreateRequest;
import com.dumble.service.session.domain.dto.response.BookingResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface BookingService {
    BookingResponse createBooking(BookingCreateRequest request, UUID participantId);
    void confirmPayment(UUID paymentId);
    BookingResponse getBookingDetailsSecure(UUID bookingId, UUID callerId);
    void cancelBookingSecure(UUID bookingId, UUID callerId);
    Page<BookingResponse> getParticipantBookingsPage(UUID participantId, org.springframework.data.domain.Pageable pageable);
    void failPayment(UUID bookingId);
}
