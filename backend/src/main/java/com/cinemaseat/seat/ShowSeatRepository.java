package com.cinemaseat.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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
     * Cleanup job (addendum A2/A9). SKIP LOCKED so the job yields to in-flight hold/callback transactions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT * FROM show_seat " +
                   "WHERE status = 'HELD' AND hold_expires_at < :now " +
                   "ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<ShowSeat> findExpiredForCleanup(@Param("now") OffsetDateTime now);
}