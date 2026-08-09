package com.cinemaseat.payment;

import com.cinemaseat.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 of the raw callback body (addendum B4).
 * Header: X-Signature: hex digest.
 * Secret: GATEWAY_SECRET (default z2p-2026-secret per gateway reference).
 *
 * Note: framework JSON re-serialization breaks the signature. We compute
 * over the RAW bytes via CachedBodyHttpServletRequest.
 */
@Component
public class SignatureVerifier {
    private final AppProperties properties;

    public SignatureVerifier(AppProperties properties) {
        this.properties = properties;
    }

    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getGateway().getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    public boolean verify(byte[] body, String providedSignatureHex) {
        if (providedSignatureHex == null || providedSignatureHex.isBlank()) return false;
        String expected = sign(body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignatureHex.getBytes(StandardCharsets.UTF_8));
    }
}