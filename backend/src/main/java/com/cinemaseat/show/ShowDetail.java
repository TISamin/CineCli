package com.cinemaseat.show;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Show detail with denormalized movie + screen info for the frontend.
 * Used by:
 *   GET /api/shows/{id}
 *   GET /api/movies/{id}/shows (list element)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShowDetail(
        Long id,
        Long movieId,
        String movieTitle,
        Long screenId,
        String screenName,
        String theatreName,
        OffsetDateTime startTime,
        OffsetDateTime endTime) {
}