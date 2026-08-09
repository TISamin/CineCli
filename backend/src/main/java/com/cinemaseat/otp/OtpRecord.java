package com.cinemaseat.otp;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "otp_record")
public class OtpRecord {
    @Id
    private String ref;

    @Column(nullable = false)
    private String phone;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "send_attempt", nullable = false)
    private int sendAttempt;

    @Column(name = "verify_attempts", nullable = false)
    private int verifyAttempts;

    @Column(nullable = false)
    private boolean delivered;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    public String getRef() { return ref; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public int getSendAttempt() { return sendAttempt; }
    public int getVerifyAttempts() { return verifyAttempts; }
    public boolean isDelivered() { return delivered; }
    public boolean isVerified() { return verified; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }

    public void setRef(String r) { this.ref = r; }
    public void setPhone(String p) { this.phone = p; }
    public void setCodeHash(String h) { this.codeHash = h; }
    public void setSendAttempt(int n) { this.sendAttempt = n; }
    public void setVerifyAttempts(int n) { this.verifyAttempts = n; }
    public void setDelivered(boolean d) { this.delivered = d; }
    public void setVerified(boolean v) { this.verified = v; }
    public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public void setExpiresAt(OffsetDateTime t) { this.expiresAt = t; }
}
