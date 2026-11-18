package com.nashi.place;

import com.nashi.api.FieldViolation;
import com.nashi.api.ValidationFailedException;
import com.nashi.artist.Names;
import com.nashi.geo.Region;
import io.swagger.v3.oas.annotations.Operation;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** {@code GET /api/v2/places/search} ({@code docs/02-contracts.md} 2.9). */
@RestController
@RequestMapping("/api/v2/places")
public class PlaceController {

    static final int MIN_QUERY = 2;
    static final int MAX_LIMIT = 20;

    private final PlaceSearchService search;

    public PlaceController(PlaceSearchService search) {
        this.search = search;
    }

    /**
     * {@code q} counts after normalization (case, apostrophes, quotes and stress marks do not count), so «к’» is one
     * character. An unknown {@code region} or a non-numeric {@code limit} is a 400 from {@code ProblemDetailsAdvice}.
     */
    @Operation(operationId = "searchPlaces", summary = "Автодоповнення населених пунктів")
    @GetMapping("/search")
    public List<PlaceSuggestion> search(
            @RequestParam("q") String q,
            @RequestParam(name = "region", required = false) @Nullable Region region,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        List<FieldViolation> errors = new ArrayList<>();
        String normalized = Names.normalize(q);
        if (normalized.codePointCount(0, normalized.length()) < MIN_QUERY) {
            errors.add(new FieldViolation("q", "Введіть щонайменше " + MIN_QUERY + " символи"));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            errors.add(new FieldViolation("limit", "Від 1 до " + MAX_LIMIT));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return search.search(q, region, limit);
    }
}
