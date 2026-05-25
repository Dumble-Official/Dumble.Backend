package com.dumble.service.session.controller;

import com.dumble.service.session.domain.dto.request.BookingCreateRequest;
import com.dumble.service.session.domain.dto.response.BookingResponse;
import com.dumble.service.session.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    @PostMapping("/book")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            Authentication authentication) {

        UUID participantId = UUID.fromString(authentication.getName());
        log.info("Secure booking request received from Participant ID: {} for Session ID: {}", participantId, request.getSessionId());

        BookingResponse response = bookingService.createBooking(request, participantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBookingDetails(
            @PathVariable UUID bookingId,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(bookingService.getBookingDetailsSecure(bookingId, callerId));
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            Authentication authentication,
            Pageable pageable) {

        UUID participantId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(bookingService.getParticipantBookingsPage(participantId, pageable));
    }

    @PostMapping("/cancel/{bookingId}")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable UUID bookingId,
            Authentication authentication) {

        log.info("Secure cancel request received from participant for booking ID: {}", bookingId);
        UUID callerId = UUID.fromString(authentication.getName());

        bookingService.cancelBookingSecure(bookingId, callerId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }


//    @PostMapping("/{bookingId}/confirm-test")
//    public ResponseEntity<String> confirmPaymentTest(@PathVariable UUID bookingId) {
//        log.info("Manual trigger for confirming payment for booking ID: {}", bookingId);
//        bookingService.confirmPayment(bookingId);
//        return ResponseEntity.ok("Payment confirmation triggered successfully!");
//    }
}