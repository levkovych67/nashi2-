package com.nashi.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NewsPostViewDTO {
    private String id;
    private String content;
    
    private String imageUrl; // Картинка
    private String audioUrl; // NEW: Посилання на трек
    
    private String artistSlug;
    private String artistName;
    private LocalDateTime createdAt;
}