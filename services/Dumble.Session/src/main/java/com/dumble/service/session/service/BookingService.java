package com.dumble.service.session.service;

import com.dumble.service.session.domain.dto.request.BookingCreateRequest;
import com.dumble.service.session.domain.dto.response.BookingResponse;

import java.util.List;
import java.util.UUID;

public interface BookingService {
    BookingResponse createBooking(BookingCreateRequest request, UUID participantId);
    BookingResponse getBookingDetails(UUID bookingId);
    List<BookingResponse> getParticipantBookings(UUID participantId);
    void cancelBooking(UUID bookingId);
    void confirmPayment(UUID paymentId, String transactionRef);
}
