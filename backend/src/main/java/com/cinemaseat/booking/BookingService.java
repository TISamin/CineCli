package com.cinemaseat.booking;

import com.cinemaseat.common.ApiException;
import com.cinemaseat.config.AppProperties;
import com.cinemaseat.payment.HoldTokenHasher;
import com.cinemaseat.seat.Seat;
import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.seat.ShowSeat;
import com.cinemaseat.seat.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The keystone. Implements the hold transaction with pessimistic row locking.
 *
 * Invariants enforced here (addendum A2, A4, A7, §10 of base plan):
 *  - Pessimistic SELECT FOR UPDATE on show_seat; concurrent requests serialize.
 *  - Re-hold by same (user_phone, show, seat) returns the existing token and refreshes TTL.
 *  - hold_token stored as SHA-256 hash; raw token never persisted.
 *  - Lock order on multi-row paths: Payment -> Booking -> ShowSeat.
 */
@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatRepository seatRepository;
    private final HoldTokenRepository holdTokenRepository;
    private final AppProperties properties;

    public BookingService(BookingRepository bookingRepository,
                          ShowSeatRepository showSeatRepository,
                          SeatRepository seatRepository,
                          HoldTokenRepository holdTokenRepository,
                          AppProperties properties) {
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.seatRepository = seatRepository;
        this.holdTokenRepository = holdTokenRepository;
        this.properties = properties;
    }

    /**
     * Holds a seat for a user.
     *
     * @param showId  the show
     * @param seatId  the physical seat (not show_seat id)
     * @param phone   user phone
     * @return HoldResult with bookingRef, holdToken (raw, returned once), expiresAt
     * @throws ApiException 409 on contention (different user holds/books the seat)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldResult hold(Long showId, Long seatId, String phone) {
        // 1. Lock the row. This is the critical serialization point.
        ShowSeat showSeat = showSeatRepository.findForUpdate(showId, seatId)
                .orElseThrow(() -> ApiException.notFound("Seat " + seatId + " in show " + showId));

        OffsetDateTime now = OffsetDateTime.now();
        int ttl = properties.getHold().getTtlSeconds();
        OffsetDateTime newExpiry = now.plusSeconds(ttl);

        // 2. Re-hold by same user (addendum A4): return existing token + extend TTL.
        if (showSeat.getStatus() == ShowSeat.Status.HELD
                && showSeat.getBookingId() != null
                && showSeat.getHoldExpiresAt() != null
                && showSeat.getHoldExpiresAt().isAfter(now)) {
            Booking existing = bookingRepository.findById(showSeat.getBookingId())
                    .orElseThrow(() -> ApiException.conflict("Seat held but booking missing"));
            if (phone.equals(existing.getUserPhone())) {
                showSeat.setHoldExpiresAt(newExpiry);
                showSeat.setUpdatedAt(now);
                showSeatRepository.save(showSeat);
                // Re-hold by the same user: revoke old token, issue a new one.
                // The caller needs a fresh token to continue payment.
                String newRaw = issueNewToken(existing);
                log.info("Re-hold extended: bookingRef={} expiresAt={}", existing.getBookingRef(), newExpiry);
                return new HoldResult(existing.getBookingRef(), newRaw, newExpiry,
                        existing.getTotalAmount(), existing.getBookingRef());
            }
            // Different user holds the seat
            throw ApiException.conflict("Seat is held by another user");
        }

        // 3. Lazy expiration check (addendum A12): treat an expired HELD seat as AVAILABLE.
        if (showSeat.getStatus() == ShowSeat.Status.BOOKED) {
            throw ApiException.conflict("Seat is already booked");
        }

        // 4. Create the booking.
        Booking booking = new Booking();
        booking.setBookingRef(generateBookingRef());
        booking.setShowId(showId);
        booking.setUserPhone(phone);
        booking.setTotalAmount(showSeat.getPrice());
        booking.setStatus(Booking.Status.PENDING_PAYMENT);
        booking.setUpdatedAt(now);
        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Booking reference collision; retry");
        }

        // 5. Update show_seat: HELD, expiry, booking.
        showSeat.setStatus(ShowSeat.Status.HELD);
        showSeat.setHoldExpiresAt(newExpiry);
        showSeat.setBookingId(booking.getId());
        showSeat.setUpdatedAt(now);
        showSeatRepository.save(showSeat);

        // 6. Issue a fresh hold token (raw returned to caller once; hash persisted).
        String rawToken = issueNewToken(booking);

        log.info("Seat held: bookingRef={} showId={} seatId={} expiresAt={}",
                booking.getBookingRef(), showId, seatId, newExpiry);

        Seat seat = seatRepository.findById(seatId).orElse(null);
        return new HoldResult(booking.getBookingRef(), rawToken, newExpiry, booking.getTotalAmount(),
                booking.getBookingRef());
    }

    /**
     * Generates a raw token, persists its hash, returns the raw token to the caller.
     */
    public String issueNewToken(Booking booking) {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        String raw = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        HoldToken t = new HoldToken();
        t.setTokenHash(HoldTokenHasher.hash(raw));
        t.setBookingId(booking.getId());
        t.setCreatedAt(OffsetDateTime.now());
        // Revoke any previous live tokens for this booking (single active token policy).
        holdTokenRepository.findByBookingIdAndRevokedAtIsNull(booking.getId())
                .forEach(x -> { x.setRevokedAt(OffsetDateTime.now()); holdTokenRepository.save(x); });
        holdTokenRepository.save(t);
        return raw;
    }

    public static String generateBookingRef() {
        byte[] b = new byte[4];
        RNG.nextBytes(b);
        String hex = HexFormat.of().formatHex(b).toUpperCase();
        return "BK-" + java.time.LocalDate.now() + "-" + hex;
    }

    public record HoldResult(String bookingRef, String holdToken, OffsetDateTime expiresAt,
                             java.math.BigDecimal amount, String ref) {}
}
