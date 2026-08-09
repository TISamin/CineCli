package com.cinemaseat.movie;

import com.cinemaseat.common.ApiException;
import com.cinemaseat.show.Show;
import com.cinemaseat.show.ShowRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;

    public MovieController(MovieRepository movieRepository, ShowRepository showRepository) {
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
    }

    @GetMapping
    public List<Movie> all() {
        return movieRepository.findAll();
    }

    @GetMapping("/{movieId}/shows")
    public List<Show> showsOf(@PathVariable Long movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw ApiException.notFound("Movie " + movieId);
        }
        return showRepository.findByMovieIdOrderByStartTimeAsc(movieId);
    }
}