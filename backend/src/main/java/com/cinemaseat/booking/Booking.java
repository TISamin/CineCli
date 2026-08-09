package com.cinemaseat.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "booking")
public class Booking {
    public enum Status { PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, EXPIRED, REFUNDED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_ref", nullable = false, unique = true)
    private String bookingRef;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "user_phone", nullable = false)
    private String userPhone;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public String getBookingRef() { return bookingRef; }
    public Long getShowId() { return showId; }
    public String getUserPhone() { return userPhone; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setBookingRef(String r) { this.bookingRef = r; }
    public void setShowId(Long id) { this.showId = id; }
    public void setUserPhone(String p) { this.userPhone = p; }
    public void setTotalAmount(BigDecimal a) { this.totalAmount = a; }
    public void setStatus(Status s) { this.status = s; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}