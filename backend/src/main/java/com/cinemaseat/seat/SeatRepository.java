package com.cinemaseat.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    @Query("SELECT s FROM Seat s WHERE s.screenId = :screenId AND UPPER(s.seatCode) = UPPER(:seatCode)")
    Optional<Seat> findByScreenIdAndSeatCode(@Param("screenId") Long screenId, @Param("seatCode") String seatCode);
}
