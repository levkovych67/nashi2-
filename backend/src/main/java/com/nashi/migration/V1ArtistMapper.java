package com.nashi.migration;

import com.nashi.artist.Artist;
import com.nashi.artist.Discipline;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Names;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.geo.PinPlacer;
import com.nashi.geo.PlaceRef;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.wiki.MediaWikiParser;
import org.bson.Document;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.List;
import java.util.Set;

/**
 * Turns a raw V1 {@code artists} document (read as a {@link Document}; V1 classes are not part of V2) into a V2
 * {@link Artist}. Photos and demo tracks are added by the job, which copies the files. V1 slugs are not kept: Cyrillic
 * ones are not valid in V2, so the slug is allocated from the name.
 */
final class V1ArtistMapper {

    static final String KYIV_CITY_NAME = Names.normalize("Київ");
    static final String SEVASTOPOL_CITY_NAME = Names.normalize("Севастополь");
    private static final int SUMMARY_MAX = 1000;
    private static final Set<String> NON_MUSIC = Set.of("VISUAL_ARTIST", "WRITER");

    private V1ArtistMapper() {
    }

    /** The V1 id: an {@code ObjectId} or a string in the raw document. */
    static String idOf(Document v1) {
        return String.valueOf(v1.get("_id"));
    }

    /**
     * True for an artist whose categories are only {@code VISUAL_ARTIST} and/or {@code WRITER}; no categories, or any
     * of {@code MUSICIAN} and {@code BAND}, means a musician.
     */
    static boolean isNonMusic(Document v1) {
        List<String> categories = strings(v1.get("categories"));
        return !categories.isEmpty() && NON_MUSIC.containsAll(categories);
    }

    /**
     * The V2 region of the V1 region and city: V1 had no cities of region rank, so {@code KYIV} with «Київ» is
     * {@link Region#KYIV_CITY} and {@code CRIMEA} with «Севастополь» is {@link Region#SEVASTOPOL_CITY}.
     *
     * @return null when V1 has no region or one V2 does not know
     */
    static Region regionOf(Document v1) {
        Region region = parseRegion(v1.getString("region"));
        String city = Names.normalize(v1.getString("city"));
        if (region == Region.KYIV && city.equals(KYIV_CITY_NAME)) {
            return Region.KYIV_CITY;
        }
        if (region == Region.CRIMEA && city.equals(SEVASTOPOL_CITY_NAME)) {
            return Region.SEVASTOPOL_CITY;
        }
        return region;
    }

    /** The V1 {@code location} (GeoJSON point), or null. */
    static GeoJsonPoint locationOf(Document v1) {
        Object location = v1.get("location");
        if (location instanceof Document point && point.get("coordinates") instanceof List<?> xy && xy.size() >= 2
                && xy.get(0) instanceof Number lon && xy.get(1) instanceof Number lat) {
            return new GeoJsonPoint(lon.doubleValue(), lat.doubleValue());
        }
        return null;
    }

    /** @param place where the artist is from, null for none (no origin, no pin) */
    static Artist map(Document v1, Place place, String artistId, String slug) {
        String style = v1.getString("style");
        return Artist.builder()
                .id(artistId)
                .slug(slug)
                .name(v1.getString("name"))
                .disciplines(List.of(Discipline.MUSIC))
                .kind(isBand(v1) ? MusicianKind.BAND : MusicianKind.PERSON)
                .status(ArtistStatus.PUBLISHED)
                .source(ArtistSource.V1_MIGRATION)
                .legacyV1Id(idOf(v1))
                .summary(MediaWikiParser.truncate(blankToNull(v1.getString("biography")), SUMMARY_MAX))
                .genres(style == null || style.isBlank() ? List.of() : List.of(style.trim()))
                .links(links(v1))
                .images(List.of())
                .audio(List.of())
                .origin(place == null ? null : new Origin(place.id(), place.name(), place.region(),
                        OriginBasis.ORIGIN, PinPrecision.CITY, place.point()))
                .pin(place == null ? null : PinPlacer.pinFor(artistId, PlaceRef.of(place), PinPrecision.CITY))
                .build();
    }

    private static boolean isBand(Document v1) {
        return v1.get("members") instanceof List<?> members && !members.isEmpty()
                || strings(v1.get("categories")).contains("BAND");
    }

    private static List<Link> links(Document v1) {
        if (!(v1.get("socialLinks") instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(Document.class::isInstance).map(Document.class::cast)
                .filter(l -> l.getString("url") != null && !l.getString("url").isBlank())
                .map(l -> new Link(platform(l.getString("platform")), l.getString("url").trim()))
                .distinct()
                .toList();
    }

    /** V1 {@code WIKI} is Wikipedia; a platform V2 does not have (or a V1 one added since) is {@code OTHER}. */
    static LinkPlatform platform(String v1Platform) {
        if ("WIKI".equals(v1Platform)) {
            return LinkPlatform.WIKIPEDIA;
        }
        try {
            return LinkPlatform.valueOf(v1Platform);
        } catch (IllegalArgumentException | NullPointerException e) {
            return LinkPlatform.OTHER;
        }
    }

    private static Region parseRegion(String name) {
        try {
            return name == null ? null : Region.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
