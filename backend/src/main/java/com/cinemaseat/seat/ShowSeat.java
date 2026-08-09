package com.cinemaseat.seat;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Critical inventory table. NO @Version (addendum A1) — pessimistic FOR UPDATE is the only locking strategy.
 */
@Entity
@Table(name = "show_seat")
public class ShowSeat {
    public enum Status { AVAILABLE, HELD, BOOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public Long getSeatId() { return seatId; }
    public BigDecimal getPrice() { return price; }
    public Status getStatus() { return status; }
    public OffsetDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public Long getBookingId() { return bookingId; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) { this.status = status; }
    public void setHoldExpiresAt(OffsetDateTime t) { this.holdExpiresAt = t; }
    public void setBookingId(Long id) { this.bookingId = id; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}