package com.cinemaseat.payment;

import com.cinemaseat.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignatureVerifierTest {

    private AppProperties props;
    private SignatureVerifier verifier;

    @BeforeEach
    void setup() {
        props = new AppProperties();
        props.getGateway().setSecret("test-secret");
        verifier = new SignatureVerifier(props);
    }

    @Test
    void signProducesConsistentHex() {
        byte[] body = "{\"hello\":\"world\"}".getBytes();
        String s1 = verifier.sign(body);
        String s2 = verifier.sign(body);
        assertEquals(s1, s2);
        assertEquals(64, s1.length()); // HMAC-SHA256 hex
    }

    @Test
    void verifyAcceptsCorrectSignature() {
        byte[] body = "{\"a\":1}".getBytes();
        String sig = verifier.sign(body);
        assertTrue(verifier.verify(body, sig));
    }

    @Test
    void verifyRejectsWrongSignature() {
        byte[] body = "{\"a\":1}".getBytes();
        assertFalse(verifier.verify(body, "deadbeef"));
        assertFalse(verifier.verify(body, ""));
        assertFalse(verifier.verify(body, null));
    }

    @Test
    void verifyIsSensitiveToBodyChanges() {
        String sig = verifier.sign("{\"a\":1}".getBytes());
        assertFalse(verifier.verify("{\"a\":2}".getBytes(), sig));
    }
}