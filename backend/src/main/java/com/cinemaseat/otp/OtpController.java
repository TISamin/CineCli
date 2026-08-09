package com.cinemaseat.otp;

import com.cinemaseat.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/api/otp/send")
    public ResponseEntity<OtpService.OtpSendResult> send(@Valid @RequestBody SendRequest body) {
        return ResponseEntity.accepted().body(otpService.send(body.phone(), body.ref()));
    }

    @PostMapping("/api/otp/verify")
    public ResponseEntity<Map<String, Object>> verify(@Valid @RequestBody VerifyRequest body) {
        try {
            OtpService.OtpVerifyResult r = otpService.verify(body.ref(), body.code());
            return ResponseEntity.ok(Map.of("verified", r.verified(), "remainingAttempts", r.remainingAttempts()));
        } catch (ApiException e) {
            // 429 etc - propagate as-is
            throw e;
        }
    }

    public record SendRequest(@NotBlank String phone, @NotBlank String ref) {}
    public record VerifyRequest(@NotBlank String ref, @NotBlank String code) {}
}