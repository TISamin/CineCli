package com.cinemaseat.gateway;

import com.cinemaseat.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin client around the mock gateway.
 *
 * Implements:
 *  - B1: env-driven gateway URL
 *  - B3: stable Idempotency-Key per booking+attempt
 *  - B5: never throws on 5xx (let callback decide final state)
 */
@Component
public class GatewayClient {
    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final AppProperties properties;
    private final HttpClient http;

    public GatewayClient(AppProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * POST /charge with Idempotency-Key. Returns the parsed response, or null on transport/5xx.
     *
     * @param mockForce optional X-Mock-Force header (success|fail|duplicate|timeout|race)
     * @param mockMode  optional X-Mock-Mode header (deterministic)
     */
    public ChargeResponse charge(String bookingRef, String idempotencyKey,
                                 String amount, String currency,
                                 String mockForce, String mockMode) {
        String body = """
                {"amount":%s,"currency":"%s","booking_ref":"%s","callback_url":"%s"}
                """.formatted(amount, currency, bookingRef, properties.getGateway().getCallbackUrl());

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(properties.getGateway().getUrl() + "/charge"))
                .timeout(Duration.ofMillis(properties.getGateway().getRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey);
        if (mockForce != null && !mockForce.isBlank()) {
            b.header("X-Mock-Force", mockForce);
        }
        if (mockMode != null && !mockMode.isBlank()) {
            b.header("X-Mock-Mode", mockMode);
        }
        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(body)).build();

        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("Gateway /charge non-2xx: status={} body={}", res.statusCode(), res.body());
                return null;
            }
            return parse(res.body(), ChargeResponse.class);
        } catch (Exception e) {
            log.warn("Gateway /charge transport failure: {}", e.toString());
            return null;
        }
    }

    public boolean refund(String paymentId, String mockForce, String mockMode) {
        String body = "{\"payment_id\":\"" + paymentId + "\"}";
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(properties.getGateway().getUrl() + "/refund"))
                .timeout(Duration.ofMillis(properties.getGateway().getRequestTimeoutMs()))
                .header("Content-Type", "application/json");
        if (mockForce != null && !mockForce.isBlank()) b.header("X-Mock-Force", mockForce);
        if (mockMode != null && !mockMode.isBlank()) b.header("X-Mock-Mode", mockMode);
        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("Gateway /refund transport failure: {}", e.toString());
            return false;
        }
    }

    public boolean sendOtp(String phone, String ref, String callbackUrl) {
        String body = """
                {"phone":"%s","ref":"%s","callback_url":"%s"}
                """.formatted(phone, ref, callbackUrl);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(properties.getGateway().getUrl() + "/otp/send"))
                .timeout(Duration.ofMillis(properties.getGateway().getRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() / 100 == 2;
        } catch (Exception e) {
            log.warn("Gateway /otp/send transport failure: {}", e.toString());
            return false;
        }
    }

    private static <T> T parse(String body, Class<T> type) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, type);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse gateway response: " + body, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChargeResponse(
            @JsonProperty("payment_id") String paymentId,
            @JsonProperty("status") String status) {}
}