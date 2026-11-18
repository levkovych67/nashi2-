package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Discipline;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Link;
import com.nashi.artist.LinkClassifier;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.artist.SlugAllocator;
import com.nashi.geo.PinPlacer;
import com.nashi.geo.PlaceRef;
import com.nashi.media.FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.submission.Submission;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the published artist of an approved submission (not saved). The id is assigned here so the pin is placed
 * with the final id. The origin is the birth place when given ({@code BIRTH}, {@code SETTLEMENT}), otherwise the
 * applicant's origin place ({@code ORIGIN} for a person, {@code FORMATION} for a band; {@code CITY}). Both sizes of
 * every photo are copied from the private {@code submissions/{sid}/} to the public {@code artists/{aid}/}.
 */
@Component
public class ArtistFromSubmission {

    private static final Logger log = LoggerFactory.getLogger(ArtistFromSubmission.class);
    private static final String THUMB_SUFFIX = "-thumb.jpg";
    private static final String JPG = ".jpg";

    private final PlaceStore places;
    private final ArtistRepository artists;
    private final FileStorage storage;
    private final Clock clock;

    public ArtistFromSubmission(PlaceStore places, ArtistRepository artists, FileStorage storage, Clock clock) {
        this.places = places;
        this.artists = artists;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * The new artist with its photos already copied. If a copy fails, the copies made so far are deleted and the
     * exception is rethrown; lookups that can fail run before anything is copied.
     */
    public Artist create(Submission s) {
        String id = new ObjectId().toHexString();
        boolean byBirth = s.getBirthPlaceId() != null;
        Place place = place(byBirth ? s.getBirthPlaceId() : s.getOriginPlaceId());
        OriginBasis basis = byBirth ? OriginBasis.BIRTH
                : s.getKind() == MusicianKind.PERSON ? OriginBasis.ORIGIN : OriginBasis.FORMATION;
        PinPrecision precision = byBirth ? PinPrecision.SETTLEMENT : PinPrecision.CITY;
        Artist artist = Artist.builder()
                .id(id)
                .slug(SlugAllocator.allocate(s.getName(), artists::existsBySlug))
                .name(s.getName())
                .disciplines(List.of(Discipline.MUSIC))
                .kind(s.getKind())
                .status(ArtistStatus.PUBLISHED)
                .source(ArtistSource.SUBMISSION)
                .summary(s.getSummary())
                .origin(new Origin(place.id(), place.name(), place.region(), basis, precision, place.point()))
                .pin(PinPlacer.pinFor(id, PlaceRef.of(place), precision))
                .genres(s.getGenres() == null ? List.of() : List.copyOf(s.getGenres()))
                .links(s.getLinks() == null ? List.of()
                        : s.getLinks().stream().map(url -> new Link(LinkClassifier.classify(url), url)).toList())
                .youtubeChannelUrl(s.getYoutubeChannelUrl())
                .submissionId(s.getId())
                .publishedAt(clock.instant())
                .build();
        artist.setImages(copyPhotos(s, id));
        return artist;
    }

    /** Deletes the photos {@link #create} copied for this artist; best effort, for an artist that was not saved. */
    public void discard(Artist artist) {
        List<String> keys = new ArrayList<>();
        if (artist.getImages() != null) {
            for (ImageAsset image : artist.getImages()) {
                keys.add(image.key());
                keys.add(image.thumbKey());
            }
        }
        deleteQuietly(keys);
    }

    /** {@code submissions/{sid}/{n}.jpg} of every photo, in order; the thumbnails are left out. */
    static List<String> fullImageKeys(Submission s) {
        return s.getImageKeys() == null ? List.of()
                : s.getImageKeys().stream().filter(key -> !key.endsWith(THUMB_SUFFIX)).toList();
    }

    private Place place(String placeId) {
        return places.find(placeId)
                .orElseThrow(() -> new IllegalStateException("Place " + placeId + " is not in the gazetteer"));
    }

    private List<ImageAsset> copyPhotos(Submission s, String artistId) {
        List<ImageAsset> images = new ArrayList<>();
        List<String> copied = new ArrayList<>();
        try {
            for (String full : fullImageKeys(s)) {
                String base = full.substring(0, full.length() - JPG.length());
                String n = base.substring(base.lastIndexOf('/') + 1);
                String to = "artists/" + artistId + "/" + n + JPG;
                String thumbTo = "artists/" + artistId + "/" + n + THUMB_SUFFIX;
                storage.copy(full, to);
                copied.add(to);
                storage.copy(base + THUMB_SUFFIX, thumbTo);
                copied.add(thumbTo);
                images.add(new ImageAsset(to, null, thumbTo, null, s.getName(), null));
            }
        } catch (RuntimeException e) {
            deleteQuietly(copied);
            throw e;
        }
        return images;
    }

    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not delete {} of an artist that was not created: {}", key, e.getMessage());
            }
        }
    }
}
