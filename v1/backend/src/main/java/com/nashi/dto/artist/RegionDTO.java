package com.nashi.dto.artist;

public record RegionDTO(
    String key,   // Enum name (e.g. "LVIV")
    String label  // Display name (e.g. "Львівська")
) {}