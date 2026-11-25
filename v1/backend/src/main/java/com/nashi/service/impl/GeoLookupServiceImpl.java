package com.nashi.service.impl;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.dto.CityLookupResponse;
import com.nashi.enums.Region;
import com.nashi.service.GeoLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoLookupServiceImpl implements GeoLookupService {

    private final AiRequestService aiRequestService;
    private final ObjectMapper objectMapper;

    // Caching is on (CachingConfig). An empty answer is not cached: it is also what a failed AI call returns.
    @Override
    @Cacheable(value = "cityCoordinates", key = "#region.name() + '-' + #query.toLowerCase()",
            unless = "#result == null || #result.isEmpty()")
    public List<CityLookupResponse> searchCity(Region region, String query) {
        log.info("Searching coordinates via AI for region: {} and query: {}", region, query);

        try {
            // 1. Отримуємо RAW JSON рядок від AI
            // Формат відповіді: { "cities": [ { ... }, { ... } ] }
            String jsonResponse = aiRequestService.searchCities(region, query);

            if (jsonResponse == null || jsonResponse.isBlank()) {
                return Collections.emptyList();
            }

            // 2. Парсимо JSON
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode citiesArray = rootNode.get("cities");

            if (citiesArray == null || !citiesArray.isArray()) {
                log.warn("AI returned invalid JSON structure: missing 'cities' array");
                return Collections.emptyList();
            }

            // 3. Конвертуємо JSON Array у List<CityLookupResponse>
            // Jackson автоматично сматчить поля (name, latitude...) та сконвертує string "LVIV" у Region.LVIV
            return objectMapper.convertValue(
                    citiesArray,
                    new TypeReference<List<CityLookupResponse>>() {}
            );

        } catch (Exception e) {
            log.error("Error parsing AI response for city lookup", e);
            // Повертаємо пустий список, щоб не "валити" фронтенд помилкою 500
            return Collections.emptyList();
        }
    }
}