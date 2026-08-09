package com.cinemaseat.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_event")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "booking_ref", nullable = false)
    private String bookingRef;

    @Column(nullable = false)
    private String status;

    private BigDecimal amount;
    private String currency;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getPaymentId() { return paymentId; }
    public String getBookingRef() { return bookingRef; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRawPayload() { return rawPayload; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }

    public void setEventId(String e) { this.eventId = e; }
    public void setPaymentId(String p) { this.paymentId = p; }
    public void setBookingRef(String b) { this.bookingRef = b; }
    public void setStatus(String s) { this.status = s; }
    public void setAmount(BigDecimal a) { this.amount = a; }
    public void setCurrency(String c) { this.currency = c; }
    public void setRawPayload(String p) { this.rawPayload = p; }
    public void setReceivedAt(OffsetDateTime t) { this.receivedAt = t; }
}