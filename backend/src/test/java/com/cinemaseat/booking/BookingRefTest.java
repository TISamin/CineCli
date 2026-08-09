package com.cinemaseat.booking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookingRefTest {

    @Test
    void bookingRefFormat() {
        String ref = BookingService.generateBookingRef();
        assertTrue(ref.startsWith("BK-"), "Should start with BK-: " + ref);
        // BK-YYYY-MM-DD-XXXXXXXX
        assertTrue(ref.length() >= 20, "Should be a reasonable length: " + ref);
    }

    @Test
    void bookingRefIsReasonablyUnique() {
        java.util.Set<String> refs = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            refs.add(BookingService.generateBookingRef());
        }
        // Some collisions are possible (4 bytes hex = 65k space) but expect near-uniqueness
        assertTrue(refs.size() > 990, "Expected near-unique booking refs, got " + refs.size() + "/1000");
    }
}