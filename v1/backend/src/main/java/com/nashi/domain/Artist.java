package com.nashi.domain;


import com.nashi.enums.ArtistCategory;
import com.nashi.enums.ArtistSource;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "artists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Artist {

    @Id
    private String id;

    // --- Core Info ---
    @Indexed
    private String name;

    @Indexed
    private String wikiTitle;

    /** Wikidata id (Q…) of an imported artist; the key that stops the import from adding it twice. */
    @Indexed(unique = true, sparse = true)
    private String wikidataId;

    /** Null when added through the form. */
    private ArtistSource source;

    @Indexed(unique = true)
    private String slug;

    private String biography;
    private String pressRelease;

    // --- Categorization ---
    @Indexed
    private String style;
    @Indexed
    private Region region;

    @Indexed
    private String city;

    @Builder.Default
    private Set<String> labels = new HashSet<>();

    /** Kinds of artist. Empty for artists added through the form; read them with {@code ArtistFacts.categoriesOf}. */
    @Builder.Default
    private List<ArtistCategory> categories = new ArrayList<>();

    /** Genres. Empty for artists added through the form; read them with {@code ArtistFacts.genresOf}. */
    @Builder.Default
    private List<String> genres = new ArrayList<>();

    // --- Timeline ---
    private Integer foundationYear;

    @Indexed // Useful for sorting or filtering inactive bands
    private Integer disbandYear;

    /** People only: "YYYY-MM-DD", "YYYY-MM" when only the month is known, or "YYYY" when only the year is. */
    private String birthDate;
    /** People only, same format as {@link #birthDate}. */
    private String deathDate;
    /** People only: place of birth as text, e.g. "Моринці". */
    private String birthPlace;

    /** Notable works: books, paintings, albums. */
    @Builder.Default
    private List<ArtistWork> works = new ArrayList<>();

    // --- Media ---
    @Builder.Default
    private List<String> imageKeys = new ArrayList<>();
    @Builder.Default
    private List<String> demoTrackKeys = new ArrayList<>();

    /** Author and license of the first image, when it comes from Wikimedia Commons. */
    private ImageCredit imageCredit;

    @Indexed
    @Builder.Default
    private Set<String> musicTags = new HashSet<>();

    // 2. Настрій та атмосфера (наприклад: "Melancholic", "Study", "Drive", "Rainy Day")
    @Indexed
    @Builder.Default
    private Set<String> secondaryTags = new HashSet<>();

    // --- Geo Data ---
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;
    @Indexed
    private String geohash;
    /** What {@link #location} stands for. Null for artists added through the form. */
    private LocationBasis locationBasis;

    // --- Moderation ---
    @Indexed
    @Builder.Default
    private ArtistStatus status = ArtistStatus.PENDING;
    private String contactEmail;

    // --- Embedded ---
    @Builder.Default
    private List<Member> members = new ArrayList<>();
    @Builder.Default
    private List<SocialLink> socialLinks = new ArrayList<>();

    // --- System ---
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}