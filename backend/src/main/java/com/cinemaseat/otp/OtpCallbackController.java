package com.cinemaseat.otp;

import com.cinemaseat.common.CachedBodyHttpServletRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Receives the OTP code from the gateway callback.
 * Hashes the code, persists to otp_record.code_hash.
 *
 * IMPORTANT: We don't HMAC-verify this path because the gateway reference
 * only documents X-Signature on the payment callback. We do still consume
 * the raw body via RawBodyFilter for safety.
 *
 * Always returns 200 (same convention as payment callback).
 */
@RestController
public class OtpCallbackController {
    private static final Logger log = LoggerFactory.getLogger(OtpCallbackController.class);

    private final OtpRecordRepository repo;

    public OtpCallbackController(OtpRecordRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/api/otp/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> callback(jakarta.servlet.http.HttpServletRequest req) {
        try {
            CachedBodyHttpServletRequest cached = (CachedBodyHttpServletRequest) req;
            byte[] body = cached.getCachedBody();
            OtpPayload p = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, OtpPayload.class);
            repo.findById(p.ref()).ifPresent(r -> {
                if (p.code() != null && !p.code().isBlank()) {
                    r.setCodeHash(sha256(p.code()));
                    r.setDelivered(true);
                    repo.save(r);
                }
            });
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.warn("OTP callback exception: {}", e.toString());
            return ResponseEntity.ok(Map.of("ok", false));
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtpPayload(
            @JsonProperty("ref") String ref,
            @JsonProperty("code") String code,
            @JsonProperty("phone") String phone) {}
}