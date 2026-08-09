package com.cinemaseat.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoldTokenHasherTest {

    @Test
    void hashIsStable() {
        String h1 = HoldTokenHasher.hash("abc123");
        String h2 = HoldTokenHasher.hash("abc123");
        assertEquals(h1, h2);
        assertEquals(64, h1.length()); // SHA-256 hex
    }

    @Test
    void hashDiffersForDifferentInput() {
        assertNotEquals(HoldTokenHasher.hash("a"), HoldTokenHasher.hash("b"));
    }

    @Test
    void constantTimeEqualsBehavesCorrectly() {
        assertTrue(HoldTokenHasher.constantTimeEquals("abc", "abc"));
        assertFalse(HoldTokenHasher.constantTimeEquals("abc", "abd"));
        assertFalse(HoldTokenHasher.constantTimeEquals(null, "abc"));
        assertFalse(HoldTokenHasher.constantTimeEquals("abc", null));
    }
}