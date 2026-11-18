package com.nashi.filter;

import com.nashi.api.ConditionalGet;
import com.nashi.map.MapService;
import com.nashi.map.MapSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code GET /api/v2/filters} (2.4): ETag = the map snapshot version, the counts derive from the same snapshot. */
@RestController
@RequestMapping("/api/v2/filters")
public class FilterController {

    private final FilterService filters;
    private final MapService map;

    public FilterController(FilterService filters, MapService map) {
        this.filters = filters;
        this.map = map;
    }

    @Operation(operationId = "getFilters", summary = "Визначення фільтрів")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "304", description = "Не змінилося (збіг If-None-Match); тіла немає")
    @GetMapping
    public ResponseEntity<List<FilterDefinition>> filters(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        MapSnapshot s = map.snapshot();
        return ConditionalGet.respond(s.version(), ifNoneMatch, () -> filters.definitions(s));
    }
}
