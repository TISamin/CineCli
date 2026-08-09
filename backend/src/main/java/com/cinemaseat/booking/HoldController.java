package com.cinemaseat.booking;

import com.cinemaseat.common.ApiException;
import com.cinemaseat.seat.Seat;
import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.seat.ShowSeat;
import com.cinemaseat.seat.ShowSeatRepository;
import com.cinemaseat.show.ShowRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/seats")
public class HoldController {

    private final BookingService bookingService;
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public HoldController(BookingService bookingService,
                          ShowRepository showRepository,
                          SeatRepository seatRepository) {
        this.bookingService = bookingService;
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @PostMapping("/{seatCode}/hold")
    public BookingService.HoldResult hold(@PathVariable Long showId,
                                          @PathVariable String seatCode,
                                          @Valid @RequestBody HoldRequest body) {
        if (!showRepository.existsById(showId)) {
            throw ApiException.notFound("Show " + showId);
        }
        // Resolve physical seat from seat_code within the show's screen.
        Long seatId = resolveSeatId(showId, seatCode);
        return bookingService.hold(showId, seatId, body.phone());
    }

    private Long resolveSeatId(Long showId, String seatCode) {
        var show = showRepository.findById(showId).orElseThrow(() -> ApiException.notFound("Show " + showId));
        return seatRepository.findByScreenIdAndSeatCode(show.getScreenId(), seatCode)
                .map(Seat::getId)
                .orElseThrow(() -> ApiException.notFound("Seat " + seatCode + " in show " + showId));
    }

    public record HoldRequest(@NotBlank String phone) {}
}