package com.cinemaseat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cinemaseat")
public class AppProperties {
    private Hold hold = new Hold();
    private Gateway gateway = new Gateway();
    private Otp otp = new Otp();

    public Hold getHold() { return hold; }
    public Gateway getGateway() { return gateway; }
    public Otp getOtp() { return otp; }

    public static class Hold {
        private int ttlSeconds = 120;
        private long cleanupIntervalMs = 10_000;
        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int s) { this.ttlSeconds = s; }
        public long getCleanupIntervalMs() { return cleanupIntervalMs; }
        public void setCleanupIntervalMs(long ms) { this.cleanupIntervalMs = ms; }
    }

    public static class Gateway {
        private String url;
        private String callbackUrl;
        private String secret;
        private long requestTimeoutMs = 10_000;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String url) { this.callbackUrl = url; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(long ms) { this.requestTimeoutMs = ms; }
    }

    public static class Otp {
        private String callbackUrl;
        private int maxVerifyAttempts = 5;
        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String url) { this.callbackUrl = url; }
        public int getMaxVerifyAttempts() { return maxVerifyAttempts; }
        public void setMaxVerifyAttempts(int n) { this.maxVerifyAttempts = n; }
    }
}
