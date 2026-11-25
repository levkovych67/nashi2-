package com.nashi.service.impl;

 import com.nashi.domain.Artist;
 import com.nashi.dto.TrackDTO;
 import com.nashi.enums.ArtistStatus;
import com.nashi.enums.Region;
import com.nashi.service.RadioService;
import com.nashi.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.SampleOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RadioServiceImpl implements RadioService {

    private final MongoTemplate mongoTemplate;
    private final S3Service s3Service;

    // Скільки треків повертати в одному міксі
    private static final int MIX_SIZE = 20;

    @Override
    public List<TrackDTO> generateMix(Region region) {
        
        // 1. Критерії пошуку
        Criteria criteria = Criteria.where("status").is(ArtistStatus.APPROVED);

        // Важливо: Шукаємо тільки тих, у кого Є демо-треки (масив не пустий)
        criteria.and("demoTrackKeys.0").exists(true); 

        // Якщо обрано регіон - додаємо фільтр
        if (region != null) {
            criteria.and("region").is(region);
        }

        // 2. Створюємо Aggregation Pipeline
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria), // Фільтруємо
                Aggregation.sample(MIX_SIZE) // Беремо випадкові N записів (Random Shuffle на стороні БД)
        );

        // 3. Виконуємо запит
        AggregationResults<Artist> results = mongoTemplate.aggregate(aggregation, "artists", Artist.class);
        List<Artist> randomArtists = results.getMappedResults();

        // 4. Мапимо в DTO
        return randomArtists.stream()
                .map(this::toTrackDto)
                .toList();
    }

    private TrackDTO toTrackDto(Artist artist) {
        // Беремо перший трек зі списку
        String audioUrl = s3Service.getFileUrl(artist.getDemoTrackKeys().get(0));

        // Беремо перше фото або заглушку
        String coverUrl = (artist.getImageKeys() != null && !artist.getImageKeys().isEmpty())
                ? s3Service.getFileUrl(artist.getImageKeys().get(0))
                : null; // На фронті можна поставити дефолтну картинку

        return TrackDTO.builder()
                .id(artist.getId())
                .artistName(artist.getName())
                // Оскільки ми поки не зберігаємо назви треків окремо, формуємо її так
                .title(artist.getStyle() + " Demo") 
                .audioUrl(audioUrl)
                .coverUrl(coverUrl)
                .artistSlug(artist.getSlug())
                .build();
    }
}