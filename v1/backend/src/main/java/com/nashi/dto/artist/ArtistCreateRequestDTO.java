package com.nashi.dto.artist;

import com.nashi.domain.SocialLink;
import com.nashi.enums.Region;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistCreateRequestDTO {

    // --- Core Info ---
    @NotBlank(message = "Artist name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Biography is required")
    @Size(max = 5000, message = "Biography must not exceed 5000 characters")
    private String biography;

    @Size(max = 10000, message = "Press release must not exceed 10000 characters")
    private String pressRelease;

    // --- Categorization ---
    @NotBlank(message = "Style/Genre is required")
    private String style;

    @NotNull(message = "Region is required")
    private Region region; // Ensure your frontend sends values like "LVIV" or "KYIV"

    @NotNull(message = "City is required")
    private String city; // Ensure your frontend sends values like "Львів" or "Червоноград"

    @Builder.Default
    private Set<String> labels = new HashSet<>();

    // --- Timeline ---
    @Min(value = 1900, message = "Foundation year must be after 1900")
    private Integer foundationYear;

    @Min(value = 1900, message = "Disband year must be after 1900")
    private Integer disbandYear;

    // --- Geo Data ---
    // Frontend sends simple coordinates; Backend converts to GeoJsonPoint
    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be valid (-90 to 90)")
    @Max(value = 90, message = "Latitude must be valid (-90 to 90)")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be valid (-180 to 180)")
    @Max(value = 180, message = "Longitude must be valid (-180 to 180)")
    private Double longitude;

    // --- Private Data (Moderation) ---
    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    // --- Nested Objects ---
    @Valid // Validates the content of the list
    @Builder.Default
    private List<MemberDTO> members = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<SocialLinkDTO> socialLinks = new ArrayList<>();

    // ========================================================================
    // Inner DTO Classes
    // ========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemberDTO {
        @NotBlank(message = "Member first name is required")
        private String firstName;

        private String lastName;

        private String city;

        @NotBlank(message = "Role in band is required")
        private String role; // e.g., "Vocals", "Bass"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SocialLinkDTO {
        @NotNull(message = "Platform is required")
        private SocialLink.Platform platform; // Enum: INSTAGRAM, SPOTIFY...

        @NotBlank(message = "URL is required")
        @URL(message = "Invalid URL format")
        private String url;
    }
}