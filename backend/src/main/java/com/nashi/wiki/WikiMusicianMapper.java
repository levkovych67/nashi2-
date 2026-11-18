package com.nashi.wiki;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Discipline;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.ImageCredit;
import com.nashi.artist.LifeDates;
import com.nashi.artist.Link;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.artist.TextSource;
import com.nashi.artist.Work;
import com.nashi.artist.WorkType;
import com.nashi.geo.PinPlacer;
import com.nashi.geo.PlaceRef;
import com.nashi.place.Place;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns what the collector found into a published {@link Artist} ({@code source WIKIPEDIA}). The origin rule differs
 * from V1, which took the first of birth, residence and work place: a person is on the map only by the place of birth
 * (P19) and a band only by the place of formation (P740), both with {@code SETTLEMENT} precision; anyone else is saved
 * with {@code origin} and {@code pin} null, so the page exists but the map does not show them.
 */
public final class WikiMusicianMapper {

    static final String TEXT_SOURCE_TYPE = "WIKIPEDIA";
    static final String LICENSE = "CC BY-SA 4.0";
    static final String LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/deed.uk";

    private WikiMusicianMapper() {
    }

    /**
     * The place that counts as the origin: the place of birth of a person, the place of formation of a band; null
     * when the collector found none, or found a place of residence or work.
     */
    public static WikiPlace originPlace(WikiArtistData d) {
        LocationBasis wanted = d.ensemble() ? LocationBasis.FORMATION : LocationBasis.BIRTH;
        return d.place() != null && d.locationBasis() == wanted ? d.place() : null;
    }

    /**
     * The artist to insert, not saved.
     *
     * @param place    the gazetteer place of {@link #originPlace}; ignored (no origin, no pin) when there is no origin
     *                 place
     * @param artistId the id the artist will be saved with; the pin is placed with it
     */
    public static Artist toArtist(WikiArtistData d, Place place, String slug, String artistId) {
        boolean onMap = place != null && originPlace(d) != null;
        String description = SummaryCleaner.description(d.biography());
        return Artist.builder()
                .id(artistId)
                .slug(slug)
                .name(d.name())
                .disciplines(List.of(Discipline.MUSIC))
                .kind(d.ensemble() ? MusicianKind.BAND : MusicianKind.PERSON)
                .status(ArtistStatus.PUBLISHED)
                .source(ArtistSource.WIKIPEDIA)
                .wikidataId(d.qid())
                .wikiTitle(d.wikiTitle())
                .tagline(d.tagline())
                .summary(SummaryCleaner.summary(d.biography()))
                .description(description)
                .textSource(description == null ? null : textSource(d.wikiTitle()))
                .images(image(d).map(List::of).orElse(List.of()))
                .origin(onMap ? origin(place, d.ensemble()) : null)
                .pin(onMap ? PinPlacer.pinFor(artistId, PlaceRef.of(place), PinPrecision.SETTLEMENT) : null)
                .dates(dates(d))
                .genres(d.genres() == null ? List.of() : List.copyOf(d.genres()))
                .links(links(d))
                .youtubeChannelUrl(d.youtubeChannelUrl())
                .works(works(d))
                .build();
    }

    /** The article of {@code wikiTitle} under CC BY-SA 4.0. */
    public static TextSource textSource(String wikiTitle) {
        return new TextSource(TEXT_SOURCE_TYPE, wikiTitle, WikiLinks.articleUrl(wikiTitle), LICENSE, LICENSE_URL);
    }

    /** The Commons portrait: about 800 px as {@code url}, about 400 px as {@code thumbUrl}, with its credit. */
    public static Optional<ImageAsset> image(WikiArtistData d) {
        if (d.imageUrl() == null) {
            return Optional.empty();
        }
        WikiImageCredit c = d.imageCredit();
        ImageCredit credit = c == null ? null
                : new ImageCredit(c.getAuthor(), c.getLicense(), c.getLicenseUrl(), c.getFilePageUrl());
        String url = d.imageFullUrl() != null ? d.imageFullUrl() : d.imageUrl();
        return Optional.of(new ImageAsset(null, url, null, d.imageUrl(), d.name(), credit));
    }

    /** Birth and death of a person; the years of activity (a band: formed and disbanded). */
    public static LifeDates dates(WikiArtistData d) {
        return new LifeDates(d.birthDate(), d.deathDate(), year(d.foundationYear()), year(d.disbandYear()));
    }

    /** The links of a platform the API knows, in the collector's order; the others are dropped. */
    public static List<Link> links(WikiArtistData d) {
        if (d.socialLinks() == null) {
            return List.of();
        }
        return d.socialLinks().stream()
                .filter(l -> l.getUrl() != null && !l.getUrl().isBlank())
                .flatMap(l -> l.getPlatform().toV2().map(p -> new Link(p, l.getUrl())).stream())
                .distinct()
                .toList();
    }

    /** The albums, each as an {@code ALBUM}. */
    public static List<Work> works(WikiArtistData d) {
        if (d.works() == null) {
            return List.of();
        }
        return d.works().stream()
                .filter(w -> w.title() != null)
                .map(w -> new Work(w.title(), w.year(), WorkType.ALBUM, w.articleUrl()))
                .toList();
    }

    private static Origin origin(Place place, boolean ensemble) {
        return new Origin(place.id(), place.name(), place.region(),
                ensemble ? OriginBasis.FORMATION : OriginBasis.BIRTH, PinPrecision.SETTLEMENT, place.point());
    }

    private static String year(Integer year) {
        return year == null ? null : Objects.toString(year);
    }
}
