package com.nashi.dto;

import lombok.Builder;

@Builder
public record TrackDTO(
    String id,          // ID артиста (для плеєра)
    String artistName,  // Назва гурту
    String title,       // Назва треку (поки що будемо ставити "Demo Track", бо в нас немає назв файлів в БД)
    String audioUrl,    // Посилання на mp3
    String coverUrl,    // Посилання на фото гурту
    String artistSlug   // Щоб клікнути і перейти на профіль
) {}