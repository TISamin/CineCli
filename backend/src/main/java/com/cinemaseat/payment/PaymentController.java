package com.cinemaseat.payment;

import com.cinemaseat.booking.BookingService;
import com.cinemaseat.booking.BookingService.HoldResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/bookings/{bookingRef}/pay")
    public PaymentService.PaymentInitiated pay(@PathVariable String bookingRef,
                                               @Valid @RequestBody PayRequest body,
                                               @RequestHeader(value = "X-Mock-Force", required = false) String mockForce,
                                               @RequestHeader(value = "X-Mock-Mode", required = false) String mockMode) {
        return paymentService.initiate(bookingRef, body.holdToken(), body.phone(), mockForce, mockMode);
    }

    public record PayRequest(@NotBlank String holdToken, @NotBlank String phone) {}
}