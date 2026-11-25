package com.nashi.dto;

import com.nashi.enums.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityLookupResponse {
    private String name;      // Назва міста (наприклад, "Червоноград")
    private Region region;    // Область (наприклад, LVIV)
    private String regionUA;  // Область українською (наприклад, "Львівська область")
    
    // Exact city center coordinates
    private Double latitude;   // Точні координати центру міста
    private Double longitude;  // Точні координати центру міста
    
    // Random coordinates somewhere within the city
    private Double latitudeSomewhereInTheCity;   // Випадкові координати десь у місті
    private Double longitudeSomewhereInTheCity;  // Випадкові координати десь у місті
}
