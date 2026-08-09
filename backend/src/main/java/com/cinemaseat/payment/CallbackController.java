package com.cinemaseat.payment;

import com.cinemaseat.common.CachedBodyHttpServletRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * POST /api/payments/callback — gateway posts the payment outcome here.
 *
 * Implements addendum A10 (always 200), B4 (HMAC verified), B11 (event_id dedup),
 * B5 (gateway may retry up to 8 times — we never reject a well-formed duplicate).
 */
@RestController
public class CallbackController {
    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

    private final SignatureVerifier signatureVerifier;
    private final CallbackService callbackService;

    public CallbackController(SignatureVerifier signatureVerifier, CallbackService callbackService) {
        this.signatureVerifier = signatureVerifier;
        this.callbackService = callbackService;
    }

    @PostMapping("/api/payments/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Gateway-Event", required = false) String gatewayEvent,
            jakarta.servlet.http.HttpServletRequest req) {

        try {
            CachedBodyHttpServletRequest cached = (CachedBodyHttpServletRequest) req;
            byte[] body = cached.getCachedBody();

            // B4: verify HMAC over the raw body.
            // Per A10, we MUST always return 200 to the gateway to prevent retries —
            // but for security, a bad signature should be logged and the event dropped
            // (not processed). Returning 200 here is safe: the gateway only retries on
            // transport failure, not on application-level "I rejected this".
            if (!signatureVerifier.verify(body, signature)) {
                log.warn("Invalid HMAC on payment callback: gatewayEvent={} bodyLen={}",
                        gatewayEvent, body.length);
                return ResponseEntity.ok(Map.of("ok", false, "reason", "BAD_SIGNATURE"));
            }

            CallbackPayload p = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, CallbackPayload.class);

            callbackService.handle(p.eventId(), p.paymentId(), p.bookingRef(),
                    p.status(), p.amount(), p.currency(), new String(body));

            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception ex) {
            // A10: log + always return 200 to prevent gateway retry storms.
            log.error("Payment callback handler exception: {}", ex.toString(), ex);
            return ResponseEntity.ok(Map.of("ok", false, "logged", true));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackPayload(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("booking_ref") String bookingRef,
            @JsonProperty("status") String status,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("timestamp") String timestamp) {}
}