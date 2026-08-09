package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.HoldToken;
import com.cinemaseat.booking.HoldTokenRepository;
import com.cinemaseat.common.ApiException;
import com.cinemaseat.config.AppProperties;
import com.cinemaseat.gateway.GatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payment initiation (addendum A11, B3).
 *
 * Flow:
 *  1. Validate hold token against booking.
 *  2. Create Payment row with status PENDING.
 *  3. POST /charge with stable Idempotency-Key.
 *  4. Return immediately — final state arrives via callback.
 *
 * The /charge response handler is intentionally side-effect-free:
 * it never overwrites a state finalized by the callback (addendum A11).
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final HoldTokenRepository holdTokenRepository;
    private final GatewayClient gatewayClient;
    private final AppProperties properties;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          HoldTokenRepository holdTokenRepository,
                          GatewayClient gatewayClient,
                          AppProperties properties) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.holdTokenRepository = holdTokenRepository;
        this.gatewayClient = gatewayClient;
        this.properties = properties;
    }

    @Transactional
    public PaymentInitiated initiate(String bookingRef, String rawToken, String phone,
                                     String mockForce, String mockMode) {
        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> ApiException.notFound("Booking " + bookingRef));

        if (!booking.getUserPhone().equals(phone)) {
            throw ApiException.badRequest("Phone does not match booking");
        }
        if (booking.getStatus() != Booking.Status.PENDING_PAYMENT) {
            throw ApiException.conflict("Booking is not pending payment (status=" + booking.getStatus() + ")");
        }

        // Validate hold token (addendum A7).
        String hashed = HoldTokenHasher.hash(rawToken);
        HoldToken token = holdTokenRepository.findByTokenHash(hashed)
                .orElseThrow(() -> ApiException.badRequest("Invalid hold token"));
        if (!token.getBookingId().equals(booking.getId())) {
            throw ApiException.badRequest("Hold token does not match booking");
        }
        if (token.getRevokedAt() != null) {
            throw ApiException.badRequest("Hold token has been revoked");
        }

        // Create Payment row in PENDING. UNIQUE(booking_id) guards against duplicate payment rows.
        Payment payment = new Payment();
        payment.setPaymentId("pay_pending_" + booking.getId()); // placeholder; gateway may overwrite via UNIQUE
        payment.setBookingId(booking.getId());
        payment.setAmount(booking.getTotalAmount());
        payment.setCurrency("BDT");
        payment.setStatus(Payment.Status.PENDING);
        payment.setUpdatedAt(OffsetDateTime.now());
        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Payment already exists for this booking — fetch it.
            payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();
        }

        // Idempotency-Key is stable per booking attempt: same booking -> same key -> gateway dedup.
        String idempotencyKey = bookingRef + ":1";

        // Fire-and-track: gateway may be slow or 5xx. We never fail the request because of it.
        // Forward X-Mock-Force / X-Mock-Mode headers from the caller so judges can drive the mock.
        GatewayClient.ChargeResponse res = gatewayClient.charge(
                bookingRef, idempotencyKey,
                payment.getAmount().toPlainString(), payment.getCurrency(),
                mockForce, mockMode);

        if (res != null && res.paymentId() != null && !res.paymentId().isBlank()) {
            // Persist the gateway-assigned payment_id. UNIQUE(payment_id) protects from collisions.
            payment.setPaymentId(res.paymentId());
            payment.setUpdatedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
            log.info("Payment initiated: bookingRef={} paymentId={} mockForce={}", bookingRef, res.paymentId(), mockForce);
        } else {
            log.info("Payment initiated but gateway unreachable; awaiting callback: bookingRef={}", bookingRef);
        }

        return new PaymentInitiated(bookingRef, payment.getPaymentId(), booking.getStatus().name(),
                payment.getStatus().name());
    }

    public record PaymentInitiated(String bookingRef, String paymentId,
                                   String bookingStatus, String paymentStatus) {}
}