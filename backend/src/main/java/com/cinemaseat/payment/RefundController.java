package com.cinemaseat.payment;

import com.cinemaseat.common.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/bookings/{bookingRef}/refund (addendum B6).
 * Forwards to gateway /refund. The REFUNDED callback is handled by the same
 * callback handler with status=REFUNDED -> Booking=REFUNDED, seat freed.
 */
@RestController
public class RefundController {

    private final CallbackService callbackService;

    public RefundController(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/api/bookings/{bookingRef}/refund")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable String bookingRef) {
        boolean ok = callbackService.requestRefund(bookingRef);
        if (!ok) {
            throw ApiException.conflict("Booking " + bookingRef + " cannot be refunded (not confirmed/succeeded)");
        }
        return ResponseEntity.accepted().body(Map.of("bookingRef", bookingRef, "status", "PENDING"));
    }
}