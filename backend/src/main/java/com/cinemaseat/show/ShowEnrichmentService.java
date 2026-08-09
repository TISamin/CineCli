package com.cinemaseat.show;

import com.cinemaseat.movie.Movie;
import com.cinemaseat.movie.MovieRepository;
import com.cinemaseat.screen.Screen;
import com.cinemaseat.screen.ScreenRepository;
import com.cinemaseat.theatre.Theatre;
import com.cinemaseat.theatre.TheatreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enriches Show entities with denormalized movie + screen + theatre names
 * for the frontend.
 */
@Service
public class ShowEnrichmentService {

    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final TheatreRepository theatreRepository;

    public ShowEnrichmentService(MovieRepository movieRepository,
                                 ScreenRepository screenRepository,
                                 TheatreRepository theatreRepository) {
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
        this.theatreRepository = theatreRepository;
    }

    @Transactional(readOnly = true)
    public ShowDetail enrich(Show show) {
        Movie movie = movieRepository.findById(show.getMovieId()).orElse(null);
        Screen screen = screenRepository.findById(show.getScreenId()).orElse(null);
        Theatre theatre = screen == null ? null
                : theatreRepository.findById(screen.getTheatreId()).orElse(null);
        return new ShowDetail(
                show.getId(),
                show.getMovieId(),
                movie == null ? null : movie.getTitle(),
                show.getScreenId(),
                screen == null ? null : screen.getName(),
                theatre == null ? null : theatre.getName(),
                show.getStartTime(),
                show.getEndTime());
    }

    @Transactional(readOnly = true)
    public List<ShowDetail> enrichAll(List<Show> shows) {
        // Batch-load movies, screens, theatres to avoid N+1.
        Set<Long> movieIds = shows.stream().map(Show::getMovieId).collect(Collectors.toSet());
        Set<Long> screenIds = shows.stream().map(Show::getScreenId).collect(Collectors.toSet());
        Map<Long, Movie> movies = new HashMap<>();
        movieRepository.findAllById(movieIds).forEach(m -> movies.put(m.getId(), m));
        Map<Long, Screen> screens = new HashMap<>();
        screenRepository.findAllById(screenIds).forEach(s -> screens.put(s.getId(), s));
        Set<Long> theatreIds = screens.values().stream().map(Screen::getTheatreId).collect(Collectors.toSet());
        Map<Long, Theatre> theatres = new HashMap<>();
        theatreRepository.findAllById(theatreIds).forEach(t -> theatres.put(t.getId(), t));

        return shows.stream().map(show -> {
            Movie m = movies.get(show.getMovieId());
            Screen s = screens.get(show.getScreenId());
            Theatre t = s == null ? null : theatres.get(s.getTheatreId());
            return new ShowDetail(
                    show.getId(),
                    show.getMovieId(),
                    m == null ? null : m.getTitle(),
                    show.getScreenId(),
                    s == null ? null : s.getName(),
                    t == null ? null : t.getName(),
                    show.getStartTime(),
                    show.getEndTime());
        }).toList();
    }
}