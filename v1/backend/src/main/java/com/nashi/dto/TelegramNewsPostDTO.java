package com.nashi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramNewsPostDTO {

    private String title;
    // Текст посту (з емодзі та форматуванням)
    private String caption;

    // Пряме посилання на зображення (S3 URL)
    private String imageUrl;

    // Пряме посилання на аудіофайл (S3 URL)
    private String audioUrl;
}