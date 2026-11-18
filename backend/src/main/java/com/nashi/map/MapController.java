package com.nashi.map;

import com.nashi.api.ConditionalGet;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code GET /api/v2/map/*} ({@code docs/02-contracts.md} 2.1-2.3): ETag = the snapshot version, 304 on a match. */
@RestController
@RequestMapping("/api/v2/map")
public class MapController {

    private final MapService service;

    public MapController(MapService service) {
        this.service = service;
    }

    @Operation(operationId = "getMapPins", summary = "Піни на карті")
    @GetMapping("/pins")
    public ResponseEntity<MapPinsResponse> pins(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        MapSnapshot s = service.snapshot();
        return ConditionalGet.respond(s.version(), ifNoneMatch, () -> new MapPinsResponse(s.version(), s.pins()));
    }

    @Operation(operationId = "getMapRegions", summary = "Кількість музикантів по областях")
    @GetMapping("/regions")
    public ResponseEntity<List<RegionStat>> regions(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        MapSnapshot s = service.snapshot();
        return ConditionalGet.respond(s.version(), ifNoneMatch, s::regions);
    }

    @Operation(operationId = "getMapPlaces", summary = "Кількість музикантів по населених пунктах")
    @GetMapping("/places")
    public ResponseEntity<List<PlaceStat>> places(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        MapSnapshot s = service.snapshot();
        return ConditionalGet.respond(s.version(), ifNoneMatch, s::places);
    }
}
