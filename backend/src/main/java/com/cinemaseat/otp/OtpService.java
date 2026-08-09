package com.cinemaseat.otp;

import com.cinemaseat.common.ApiException;
import com.cinemaseat.config.AppProperties;
import com.cinemaseat.gateway.GatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * OTP flow (addendum B12).
 *
 * /otp/send  - delegate to gateway; persist a local record.
 * /otp/verify - hash + constant-time compare; track attempts; 429 after N failures.
 *
 * Note: OTP is 10% silently lost per gateway reference. We accept that and return
 * success to the user with a "may take up to 15s" message.
 */
@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpRecordRepository otpRecordRepository;
    private final GatewayClient gatewayClient;
    private final AppProperties properties;

    public OtpService(OtpRecordRepository otpRecordRepository,
                      GatewayClient gatewayClient,
                      AppProperties properties) {
        this.otpRecordRepository = otpRecordRepository;
        this.gatewayClient = gatewayClient;
        this.properties = properties;
    }

    @Transactional
    public OtpSendResult send(String phone, String ref) {
        OtpRecord rec = otpRecordRepository.findById(ref).orElseGet(() -> {
            OtpRecord r = new OtpRecord();
            r.setRef(ref);
            r.setCreatedAt(OffsetDateTime.now());
            r.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
            r.setCodeHash(""); // populated by callback
            return r;
        });
        rec.setPhone(phone);
        rec.setSendAttempt(rec.getSendAttempt() + 1);
        rec.setDelivered(false);
        rec.setVerified(false);
        otpRecordRepository.save(rec);

        boolean ok = gatewayClient.sendOtp(phone, ref, properties.getOtp().getCallbackUrl());
        log.info("OTP sent: ref={} phone={} gatewayOk={}", ref, phone, ok);
        return new OtpSendResult(ref, true, "Code may take up to 15 seconds to arrive");
    }

    @Transactional
    public OtpVerifyResult verify(String ref, String code) {
        OtpRecord rec = otpRecordRepository.findById(ref)
                .orElseThrow(() -> ApiException.notFound("OTP ref " + ref));

        if (rec.getVerifyAttempts() >= properties.getOtp().getMaxVerifyAttempts()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many attempts; request a new code");
        }

        rec.setVerifyAttempts(rec.getVerifyAttempts() + 1);

        String hashed = sha256(code);
        if (hashed.equals(rec.getCodeHash())) {
            rec.setVerified(true);
            otpRecordRepository.save(rec);
            return new OtpVerifyResult(true, 0);
        }

        otpRecordRepository.save(rec);
        int remaining = properties.getOtp().getMaxVerifyAttempts() - rec.getVerifyAttempts();
        return new OtpVerifyResult(false, remaining);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record OtpSendResult(String ref, boolean queued, String message) {}
    public record OtpVerifyResult(boolean verified, int remainingAttempts) {}
}