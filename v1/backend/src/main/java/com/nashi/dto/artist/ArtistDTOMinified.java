package com.nashi.dto.artist;

import com.nashi.enums.ArtistCategory;
import com.nashi.enums.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistDTOMinified {

    private String id;
    private String name;
    // Null when the artist has no place on the map
    private Double longitude;
    private Double latitude;
    private String city;
    private Region region;
    private List<ArtistCategory> categories;

    // Використовується для посилань (наприклад: <a href="/artist/{slug}">)
    private String slug;

    // Список повних URL-адрес картинок (вже згенерованих сервісом)
    private List<String> imageUrls;
}