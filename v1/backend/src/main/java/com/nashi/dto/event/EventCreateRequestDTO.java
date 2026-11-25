package com.nashi.dto.event;

import com.nashi.enums.Region;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCreateRequestDTO {

    private String artistName;

    @NotBlank(message = "Title is required")
    private String title; // "Благодійний концерт", "Lviv Acoustic Fest"

    @NotBlank(message = "City is required")
    private String city;  // "Львів", "Київ"

    @NotNull(message = "Region is required")
    private Region region; // Enum: LVIV, KYIV...

    @NotBlank(message = "Venue is required")
    private String venue; // "Club Atlas", "Malevich"

    @NotNull(message = "Date is required")
    @Future(message = "Date must be in the future") // Валідація: дата має бути в майбутньому
    private LocalDateTime dateTime;

    private String ticketUrl;   // Посилання на квитки або банку

    private String description; // Додатковий опис
}