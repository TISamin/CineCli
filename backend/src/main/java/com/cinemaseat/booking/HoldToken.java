package com.cinemaseat.booking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Hold tokens are stored as SHA-256 hashes (addendum A7). The raw UUID is never persisted.
 */
@Entity
@Table(name = "hold_token")
public class HoldToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public Long getBookingId() { return bookingId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }

    public void setTokenHash(String h) { this.tokenHash = h; }
    public void setBookingId(Long b) { this.bookingId = b; }
    public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public void setRevokedAt(OffsetDateTime t) { this.revokedAt = t; }
}