package com.dumble.service.session.controller;

import com.dumble.service.session.domain.dto.request.BookingCreateRequest;
import com.dumble.service.session.domain.dto.response.BookingResponse;
import com.dumble.service.session.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/session/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            Authentication authentication) {


        UUID participantId = UUID.fromString(authentication.getName());
//        UUID participantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        log.info("Secure booking request received from Participant ID: {} for Session ID: {}", participantId, request.getSessionId());

        BookingResponse response = bookingService.createBooking(request, participantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBookingDetails(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.getBookingDetails(bookingId));
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<List<BookingResponse>> getMyBookings(Authentication authentication) {
        UUID participantId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(bookingService.getParticipantBookings(participantId));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID bookingId) {
        log.info("Cancel request received for booking ID: {}", bookingId);
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bookingId}/confirm-test")
    public ResponseEntity<String> confirmPaymentTest(@PathVariable UUID bookingId) {
        log.info("Manual trigger for confirming payment for booking ID: {}", bookingId);
        bookingService.confirmPayment(bookingId);
        return ResponseEntity.ok("Payment confirmation triggered successfully!");
    }
}