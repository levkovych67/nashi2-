package com.nashi.artist;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A musician or a band ({@code docs/02-contracts.md} section 3, {@code artists}). {@code legacyV1Id} is internal
 * (the id in V1, for the migration); {@code version} gives optimistic locking and makes {@code save} with a
 * pre-assigned id an insert. {@code likesCount} is changed only with {@code $inc}.
 */
@Document("artists")
@Data
@Builder
public class Artist {

    @Id
    private String id;
    @Version
    private Long version;

    private String slug;
    private String name;
    private String nameNormalized;
    private List<Discipline> disciplines;
    private MusicianKind kind;
    private ArtistStatus status;
    private ArtistSource source;
    private String wikidataId;
    private String wikiTitle;
    private String tagline;
    private String summary;
    private String description;
    private TextSource textSource;
    private List<ImageAsset> images;
    private Origin origin;
    /** Where the pin is on the map; {@code null} = not on the map. */
    private GeoJsonPoint pin;
    private LifeDates dates;
    private List<String> genres;
    private List<Link> links;
    private String youtubeChannelUrl;
    private List<Work> works;
    private List<Video> videos;
    private List<AudioClip> audio;
    private Instant audioCheckedAt;
    private long likesCount;
    private String submissionId;
    private String legacyV1Id;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private Instant publishedAt;
}
