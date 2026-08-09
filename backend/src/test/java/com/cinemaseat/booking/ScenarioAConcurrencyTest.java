package com.cinemaseat.booking;

import com.cinemaseat.common.ApiException;
import com.cinemaseat.testsupport.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario A keystone test (addendum A16 step 7):
 * 100 concurrent hold requests for the SAME (show, seat) must produce
 * exactly 1 success and 99 rejections (HTTP 409), zero oversell.
 */
@SpringBootTest
@ActiveProfiles("test")
class ScenarioAConcurrencyTest extends PostgresTestContainer {

    @Autowired BookingService bookingService;

    @Test
    void hundredConcurrentHolds_sameSeat_yieldExactlyOneSuccess() throws Exception {
        final int N = 100;
        final Long showId = 1L;
        final Long seatId = 1L;

        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new java.util.ArrayList<>(N);

        for (int i = 0; i < N; i++) {
            final String phone = String.format("017%08d", i);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    BookingService.HoldResult r = bookingService.hold(showId, seatId, phone);
                    return new Outcome(true, r.bookingRef(), null);
                } catch (ApiException e) {
                    return new Outcome(false, null, e.getStatus().value());
                } catch (Exception e) {
                    return new Outcome(false, null, -1);
                }
            }));
        }

        start.countDown();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        for (Future<Outcome> f : futures) {
            Outcome o = f.get(60, TimeUnit.SECONDS);
            if (o.success) success.incrementAndGet();
            else if (o.statusCode == 409) conflict.incrementAndGet();
            else other.incrementAndGet();
        }
        pool.shutdown();

        assertEquals(1, success.get(), "Exactly one successful hold expected");
        assertEquals(N - 1, conflict.get(), "All other requests must be 409 Conflict");
        assertEquals(0, other.get(), "No unexpected error codes");
    }

    record Outcome(boolean success, String bookingRef, Integer statusCode) {}
}
