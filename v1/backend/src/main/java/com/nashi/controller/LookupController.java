package com.nashi.controller;


import com.nashi.dto.CityLookupResponse;
import com.nashi.dto.artist.RegionDTO;
import com.nashi.enums.Region;
import com.nashi.service.GeoLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/lookup")
@RequiredArgsConstructor
public class LookupController {

    private final GeoLookupService geoLookupService;

    @GetMapping("/cities")
    public ResponseEntity<List<CityLookupResponse>> searchCities(
            @RequestParam("region") Region region,
            @RequestParam("query") String query
    ) {
        if (query == null || query.length() < 4) {
            return ResponseEntity.badRequest().body(List.of());
        }

        List<CityLookupResponse> cities = geoLookupService.searchCity(region, query);
        return ResponseEntity.ok(cities);
    }

    @GetMapping("/regions")
    public ResponseEntity<List<RegionDTO>> getRegions() {
        Collator uaCollator = Collator.getInstance(new Locale("uk", "UA"));
        List<RegionDTO> regions = Arrays.stream(Region.values())
                .map(region -> new RegionDTO(
                        region.name(),
                        region.getUaName()
                ))
                // Сортуємо список за назвою (label), щоб у dropdown було А-Я
                .sorted(Comparator.comparing(RegionDTO::label, uaCollator))
                .toList();

        return ResponseEntity.ok(regions);
    }

}