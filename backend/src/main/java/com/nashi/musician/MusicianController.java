package com.nashi.musician;

import com.nashi.api.FieldViolation;
import com.nashi.api.ValidationFailedException;
import com.nashi.artist.Names;
import com.nashi.geo.Region;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** {@code GET /api/v2/musicians} and {@code /{slug}} ({@code docs/02-contracts.md} 2.5, 2.6). */
@RestController
@RequestMapping("/api/v2/musicians")
public class MusicianController {

    static final int MIN_QUERY = 2;
    static final int MAX_SIZE = 100;

    private final MusicianQueryService service;

    public MusicianController(MusicianQueryService service) {
        this.service = service;
    }

    /** An unknown {@code region} or {@code sort}, or a non-numeric {@code page}/{@code size}, is a 400 from the advice. */
    @Operation(operationId = "listMusicians", summary = "Список музикантів")
    @GetMapping
    public MusicianPage list(
            @RequestParam(name = "region", required = false) List<Region> region,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", defaultValue = "NAME") MusicianSort sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        List<FieldViolation> errors = new ArrayList<>();
        if (q != null) {
            String normalized = Names.normalize(q);
            if (normalized.codePointCount(0, normalized.length()) < MIN_QUERY) {
                errors.add(new FieldViolation("q", "Введіть щонайменше " + MIN_QUERY + " символи"));
            }
        }
        if (page < 0) {
            errors.add(new FieldViolation("page", "Не менше 0"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(new FieldViolation("size", "Від 1 до " + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return service.list(new MusicianListQuery(region == null ? List.of() : region, q, sort, page, size));
    }

    @Operation(operationId = "getMusician", summary = "Сторінка музиканта")
    @GetMapping("/{slug}")
    public MusicianDetails get(@PathVariable("slug") String slug) {
        return service.details(slug);
    }
}
