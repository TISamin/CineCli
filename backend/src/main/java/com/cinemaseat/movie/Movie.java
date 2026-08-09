package com.cinemaseat.movie;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "movie")
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    private String language;
    private String rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getLanguage() { return language; }
    public String getRating() { return rating; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}