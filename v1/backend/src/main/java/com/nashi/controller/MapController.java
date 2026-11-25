package com.nashi.controller;

import com.nashi.dto.MapPinDTO;
import com.nashi.enums.PinType;
import com.nashi.enums.Region;
import com.nashi.service.MapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    /**
     * GET /api/v1/map/pins
     * * Retrieves map pins with optional filters.
     * * Usage Examples:
     * 1. All pins:
     * GET /api/v1/map/pins
     * 2. Only Events in Lviv:
     * GET /api/v1/map/pins?types=EVENT&region=LVIV
     * 3. Rock Artists:
     * GET /api/v1/map/pins?types=ARTIST&style=rock
     * 4. Indie/Folk Artists:
     * GET /api/v1/map/pins?types=ARTIST&tags=indie,folk
     */
    @GetMapping("/pins")
    public ResponseEntity<List<MapPinDTO>> getMapPins(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Set<PinType> types
    ) {
        log.info("Fetching map pins. Region: {}, Types: {}, Style: {}", region, types, style);

        List<MapPinDTO> pins = mapService.getPins(region, style, tags, types);

        return ResponseEntity.ok(pins);
    }
}
