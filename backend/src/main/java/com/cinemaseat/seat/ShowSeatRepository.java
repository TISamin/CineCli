package com.cinemaseat.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /**
     * Locking read for the hold transaction. Issues SELECT ... FOR UPDATE.
     * Lock order rule (addendum A2): acquire this row before any Booking row, and Booking before Payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.showId = :showId AND s.seatId = :seatId")
    Optional<ShowSeat> findForUpdate(@Param("showId") Long showId, @Param("seatId") Long seatId);

    @Query("SELECT s FROM ShowSeat s WHERE s.showId = :showId ORDER BY s.id")
    List<ShowSeat> findByShowId(@Param("showId") Long showId);

    /**
     * Cleanup job (addendum A2/A9). SKIP LOCKED is inlined into the SQL; Spring Data JPA
     * does NOT allow @Lock on a nativeQuery (would throw "Illegal attempt to set lock mode
     * for a native query"), so we do not annotate this method with @Lock.
     *
     * Note: takes java.time.Instant instead of OffsetDateTime to avoid the Hibernate 6 +
     * Postgres 16 native-query binding bug where OffsetDateTime is sent as
     * TIMESTAMP WITHOUT TIME ZONE and rejected with:
     *   "column ... is of type timestamptz but expression is of type timestamp".
     */
    @Query(value = "SELECT * FROM show_seat " +
                   "WHERE status = 'HELD' AND hold_expires_at < :now " +
                   "ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<ShowSeat> findExpiredForCleanup(@Param("now") Instant now);
}