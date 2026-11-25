package com.nashi.dto.artist;

import com.nashi.domain.ArtistWork;
import com.nashi.domain.ImageCredit;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.ArtistSource;


import com.nashi.domain.SocialLink;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistViewDTO {

    // --- Identity ---
    private String id;
    private String name;
    private String slug; // Useful for SEO and sharing links

    // --- Detailed Content ---
    private String biography;
    private String pressRelease;

    // --- Categorization ---
    private String style;
    private Region region;
    private Set<String> labels;
    private List<ArtistCategory> categories;
    private List<String> genres;

    // --- Timeline ---
    private Integer foundationYear;
    private Integer disbandYear; // Null if active
    private String birthDate; // People only: "YYYY-MM-DD", "YYYY-MM" or "YYYY"
    private String deathDate;

    // --- Media (Mapped to full accessible URLs) ---
    private List<String> imageUrls;
    private List<String> demoTrackUrls;

    private Set<String> musicTags;
    private Set<String> secondaryTags;

    // --- Geo Data (Flattened for Map Components) ---
    private Double latitude;
    private Double longitude;
    private String geohash;
    private String city;
    private String birthPlace;
    private LocationBasis locationBasis; // What latitude/longitude stand for

    // --- Relationships ---
    private List<MemberDTO> members;
    private List<SocialLinkDTO> socialLinks;
    private List<ArtistWork> works;

    // --- Source (Wikipedia imports must show attribution) ---
    private ArtistSource source;
    private String wikiUrl;
    private String wikidataUrl;
    private ImageCredit imageCredit;

    // --- Metadata ---
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ========================================================================
    // Inner Response DTOs
    // ========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemberDTO {
        private String firstName;
        private String lastName;
        private String city;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SocialLinkDTO {
        private SocialLink.Platform platform;
        private String url;
    }
}