package com.cinemaseat.booking;

import com.cinemaseat.seat.ShowSeat;
import com.cinemaseat.seat.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled job that releases expired HELD seats (addendum A2, A9).
 *
 * Correctness guarantee is the lazy expiration check on hold() — this job is memory hygiene only.
 * Uses SELECT ... FOR UPDATE SKIP LOCKED to yield to in-flight hold/callback transactions.
 */
@Component
public class HoldCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(HoldCleanupJob.class);

    private final ShowSeatRepository showSeatRepository;

    public HoldCleanupJob(ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    @Scheduled(fixedDelayString = "${cinemaseat.hold.cleanup-interval-ms:10000}")
    @Transactional
    public void releaseExpiredHolds() {
        Instant nowInstant = Instant.now();
        List<ShowSeat> expired = showSeatRepository.findExpiredForCleanup(nowInstant);
        if (expired.isEmpty()) return;

        OffsetDateTime now = OffsetDateTime.now();
        for (ShowSeat ss : expired) {
            log.info("Releasing expired hold: showSeatId={} bookingId={}", ss.getId(), ss.getBookingId());
            ss.setStatus(ShowSeat.Status.AVAILABLE);
            ss.setHoldExpiresAt(null);
            ss.setBookingId(null);
            ss.setUpdatedAt(now);
            showSeatRepository.save(ss);
        }
    }
}