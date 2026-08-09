package com.cinemaseat.show;

import com.cinemaseat.common.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowRepository showRepository;

    public ShowController(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    @GetMapping("/{showId}")
    public Show get(@PathVariable Long showId) {
        return showRepository.findById(showId).orElseThrow(() -> ApiException.notFound("Show " + showId));
    }
}