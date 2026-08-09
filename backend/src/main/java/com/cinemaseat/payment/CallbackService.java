package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.HoldToken;
import com.cinemaseat.booking.HoldTokenRepository;
import com.cinemaseat.common.ApiException;
import com.cinemaseat.gateway.GatewayClient;
import com.cinemaseat.seat.ShowSeat;
import com.cinemaseat.seat.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Processes gateway callbacks.
 *
 * Implements addendum A2 (lock order), A3 (late-callback no-steal), A10 (always 200),
 * B4 (HMAC verified at controller), B6 (refund via REFUNDED status), B11 (event_id dedup).
 *
 * Lock order on multi-row paths: Payment -> Booking -> ShowSeat (ascending by id).
 */
@Service
public class CallbackService {
    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    private final PaymentEventRepository paymentEventRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final HoldTokenRepository holdTokenRepository;
    private final GatewayClient gatewayClient;

    public CallbackService(PaymentEventRepository paymentEventRepository,
                           PaymentRepository paymentRepository,
                           BookingRepository bookingRepository,
                           ShowSeatRepository showSeatRepository,
                           HoldTokenRepository holdTokenRepository,
                           GatewayClient gatewayClient) {
        this.paymentEventRepository = paymentEventRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.holdTokenRepository = holdTokenRepository;
        this.gatewayClient = gatewayClient;
    }

    /**
     * Persist event + apply state transition. Returns true if processed for the first time,
     * false if it was a duplicate (already seen event_id).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean handle(String eventId, String paymentId, String bookingRef,
                          String status, java.math.BigDecimal amount, String currency,
                          String rawPayload) {
        // 1. Deduplicate via UNIQUE(event_id). Inserting the same event_id twice
        //    yields DataIntegrityViolationException on the second.
        PaymentEvent ev = new PaymentEvent();
        ev.setEventId(eventId);
        ev.setPaymentId(paymentId);
        ev.setBookingRef(bookingRef);
        ev.setStatus(status);
        ev.setAmount(amount);
        ev.setCurrency(currency);
        ev.setRawPayload(rawPayload);
        ev.setReceivedAt(OffsetDateTime.now());
        try {
            paymentEventRepository.saveAndFlush(ev);
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate callback event_id={} paymentId={} status={} -> no-op",
                    eventId, paymentId, status);
            return false;
        }

        // 2. Lock ordering: Payment -> Booking -> ShowSeat (ascending by id).
        Payment payment = paymentRepository.findByPaymentId(paymentId).orElse(null);
        Booking booking = bookingRepository.findByBookingRef(bookingRef).orElse(null);
        if (payment == null && booking == null) {
            log.warn("Callback for unknown booking/payment: bookingRef={} paymentId={}", bookingRef, paymentId);
            return true;
        }

        // 3. Apply the transition based on status.
        switch (status) {
            case "SUCCEEDED" -> handleSucceeded(payment, booking);
            case "FAILED"    -> handleFailed(payment, booking);
            case "REFUNDED"  -> handleRefunded(payment, booking);
            default -> log.warn("Unknown callback status: {}", status);
        }
        return true;
    }

    private void handleSucceeded(Payment payment, Booking booking) {
        if (payment != null) {
            if (payment.getStatus() == Payment.Status.SUCCEEDED) {
                log.debug("Payment already SUCCEEDED, no-op: paymentId={}", payment.getPaymentId());
                return;
            }
            payment.setStatus(Payment.Status.SUCCEEDED);
            payment.setUpdatedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
        }
        if (booking == null) return;

        // Addendum A3: late-callback no-steal. If the booking is already EXPIRED
        // because another user legitimately took the seat, mark payment SUCCEEDED
        // but do not steal the seat.
        if (booking.getStatus() == Booking.Status.EXPIRED) {
            log.info("Late SUCCEEDED callback after EXPIRED booking; no seat steal. bookingRef={}",
                    booking.getBookingRef());
            return;
        }

        if (booking.getStatus() == Booking.Status.PENDING_PAYMENT
                || booking.getStatus() == Booking.Status.PAYMENT_FAILED) {
            booking.setStatus(Booking.Status.CONFIRMED);
            booking.setUpdatedAt(OffsetDateTime.now());
            bookingRepository.save(booking);

            // Promote the show_seat row to BOOKED.
            var showSeatOpt = showSeatRepository.findAll().stream()
                    .filter(s -> s.getBookingId() != null && s.getBookingId().equals(booking.getId()))
                    .findFirst();
            if (showSeatOpt.isPresent()) {
                ShowSeat ss = showSeatOpt.get();
                if (ss.getStatus() != ShowSeat.Status.BOOKED) {
                    ss.setStatus(ShowSeat.Status.BOOKED);
                    ss.setHoldExpiresAt(null);
                    ss.setUpdatedAt(OffsetDateTime.now());
                    showSeatRepository.save(ss);
                }
                // Revoke the hold token — payment is now bound to a confirmed booking.
                holdTokenRepository.findByBookingIdAndRevokedAtIsNull(booking.getId())
                        .forEach(t -> { t.setRevokedAt(OffsetDateTime.now()); holdTokenRepository.save(t); });
            } else {
                log.warn("No show_seat bound to confirmed booking {}", booking.getBookingRef());
            }
        }
    }

    private void handleFailed(Payment payment, Booking booking) {
        if (payment != null && payment.getStatus() != Payment.Status.FAILED) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setUpdatedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
        }
        if (booking == null) return;

        if (booking.getStatus() != Booking.Status.PENDING_PAYMENT) {
            log.debug("Booking already non-pending, no-op on FAILED: bookingRef={} status={}",
                    booking.getBookingRef(), booking.getStatus());
            return;
        }
        booking.setStatus(Booking.Status.PAYMENT_FAILED);
        booking.setUpdatedAt(OffsetDateTime.now());
        bookingRepository.save(booking);

        // Free the seat.
        var showSeatOpt = showSeatRepository.findAll().stream()
                .filter(s -> s.getBookingId() != null && s.getBookingId().equals(booking.getId()))
                .findFirst();
        if (showSeatOpt.isPresent()) {
            ShowSeat ss = showSeatOpt.get();
            ss.setStatus(ShowSeat.Status.AVAILABLE);
            ss.setHoldExpiresAt(null);
            ss.setBookingId(null);
            ss.setUpdatedAt(OffsetDateTime.now());
            showSeatRepository.save(ss);
        }
        holdTokenRepository.findByBookingIdAndRevokedAtIsNull(booking.getId())
                .forEach(t -> { t.setRevokedAt(OffsetDateTime.now()); holdTokenRepository.save(t); });
    }

    private void handleRefunded(Payment payment, Booking booking) {
        if (payment != null && payment.getStatus() != Payment.Status.REFUNDED) {
            payment.setStatus(Payment.Status.REFUNDED);
            payment.setUpdatedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
        }
        if (booking == null) return;
        booking.setStatus(Booking.Status.REFUNDED);
        booking.setUpdatedAt(OffsetDateTime.now());
        bookingRepository.save(booking);

        var showSeatOpt = showSeatRepository.findAll().stream()
                .filter(s -> s.getBookingId() != null && s.getBookingId().equals(booking.getId()))
                .findFirst();
        if (showSeatOpt.isPresent()) {
            ShowSeat ss = showSeatOpt.get();
            ss.setStatus(ShowSeat.Status.AVAILABLE);
            ss.setHoldExpiresAt(null);
            ss.setBookingId(null);
            ss.setUpdatedAt(OffsetDateTime.now());
            showSeatRepository.save(ss);
        }
        holdTokenRepository.findByBookingIdAndRevokedAtIsNull(booking.getId())
                .forEach(t -> { t.setRevokedAt(OffsetDateTime.now()); holdTokenRepository.save(t); });
    }

    /**
     * Trigger an outbound refund via the gateway (addendum B6).
     */
    public boolean requestRefund(String bookingRef, String mockForce, String mockMode) {
        Booking booking = bookingRepository.findByBookingRef(bookingRef).orElse(null);
        if (booking == null || booking.getStatus() != Booking.Status.CONFIRMED) return false;
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (payment == null || !"SUCCEEDED".equals(payment.getStatus().name())) return false;
        return gatewayClient.refund(payment.getPaymentId(), mockForce, mockMode);
    }
}