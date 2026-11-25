package com.nashi.dto.artist;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformStatsDTO {
    private long approvedArtists; // Скільки артистів вже на мапі
    private long pendingArtists;  // Скільки чекають модерації (для адмінів)
    private long totalArtists;    // Загальна кількість
}