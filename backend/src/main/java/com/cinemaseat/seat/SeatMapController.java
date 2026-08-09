package com.cinemaseat.seat;

import com.cinemaseat.common.ApiException;
import com.cinemaseat.show.ShowRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/shows")
public class SeatMapController {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final SeatRepository seatRepository;

    public SeatMapController(ShowRepository showRepository,
                             ShowSeatRepository showSeatRepository,
                             SeatRepository seatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.seatRepository = seatRepository;
    }

    @GetMapping("/{showId}/seats")
    public SeatMapResponse seatMap(@PathVariable Long showId) {
        if (!showRepository.existsById(showId)) {
            throw ApiException.notFound("Show " + showId);
        }
        List<ShowSeat> rows = showSeatRepository.findByShowId(showId);
        var seatsById = seatRepository.findAllById(
                rows.stream().map(ShowSeat::getSeatId).toList()
        ).stream().collect(java.util.stream.Collectors.toMap(Seat::getId, s -> s));

        List<SeatView> seats = rows.stream().map(s -> {
            Seat seat = seatsById.get(s.getSeatId());
            return new SeatView(
                    s.getId(),
                    seat != null ? seat.getSeatCode() : null,
                    s.getStatus().name(),
                    s.getPrice(),
                    s.getHoldExpiresAt()
            );
        }).toList();

        return new SeatMapResponse(showId, OffsetDateTime.now(), seats);
    }

    public record SeatView(Long showSeatId, String seatCode, String status, BigDecimal price, OffsetDateTime holdExpiresAt) {}
    public record SeatMapResponse(Long showId, OffsetDateTime fetchedAt, List<SeatView> seats) {}
}