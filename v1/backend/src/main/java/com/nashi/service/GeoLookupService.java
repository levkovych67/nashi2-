package com.nashi.service;


import com.nashi.dto.CityLookupResponse;
import com.nashi.enums.Region;

import java.util.List;

public interface GeoLookupService {
    /**
     * Search for cities in a specific region matching the query.
     * @param region The region to search in (e.g. LVIV).
     * @param query The partial city name (e.g. "Cher").
     * @return List of matching cities with coordinates.
     */
    List<CityLookupResponse> searchCity(Region region, String query);
}