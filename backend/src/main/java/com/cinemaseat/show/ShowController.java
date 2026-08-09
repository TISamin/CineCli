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
    private final ShowEnrichmentService enrichment;

    public ShowController(ShowRepository showRepository, ShowEnrichmentService enrichment) {
        this.showRepository = showRepository;
        this.enrichment = enrichment;
    }

    @GetMapping("/{showId}")
    public ShowDetail get(@PathVariable Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> ApiException.notFound("Show " + showId));
        return enrichment.enrich(show);
    }
}