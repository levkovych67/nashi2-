package com.nashi.map;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.geo.Region;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.List;

/** Artists for the map ITs. */
public final class MapFixtures {

    public static final String LVIV_ID = "Q36036";
    public static final String KYIV_ID = "Q1899";

    private MapFixtures() {
    }

    /** Pin at lat 49.9 / lon 24.2, not at the settlement center (49.84 / 24.03), so tests can tell them apart. */
    public static Artist.ArtistBuilder artist(String slug, Region region, String placeId, String placeName) {
        return Artist.builder().slug(slug).name("Гурт " + slug).kind(MusicianKind.BAND)
                .status(ArtistStatus.PUBLISHED).summary("Опис")
                .origin(new Origin(placeId, placeName, region, OriginBasis.FORMATION, PinPrecision.CITY,
                        new GeoJsonPoint(24.0315921, 49.841952)))
                .pin(new GeoJsonPoint(24.2, 49.9));
    }

    public static Artist.ArtistBuilder withPhoto(Artist.ArtistBuilder b) {
        return b.images(List.of(new ImageAsset(null, "https://img/full.jpg", null, "https://img/thumb.jpg", "alt", null)));
    }

    public static Artist.ArtistBuilder withAudio(Artist.ArtistBuilder b) {
        return b.audio(List.of(new AudioClip("c1", AudioSource.UPLOAD, null, "k", "https://play", "T", 10, "a", null)));
    }
}
