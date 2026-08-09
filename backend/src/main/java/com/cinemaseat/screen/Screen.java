package com.cinemaseat.screen;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "screen")
public class Screen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theatre_id", nullable = false)
    private Long theatreId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public Long getTheatreId() { return theatreId; }
    public String getName() { return name; }
}