package com.nashi.submission;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.geo.Region;
import com.nashi.map.MapFixtures;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateDetectorIT extends MongoIT {

    @Autowired
    private ArtistRepository artists;
    @Autowired
    private DuplicateDetector detector;

    @BeforeEach
    void clean() {
        artists.deleteAll();
    }

    private Artist save(String slug, String name, ArtistStatus status) {
        return artists.save(MapFixtures.artist(slug, Region.LVIV, MapFixtures.LVIV_ID, "Львів")
                .name(name).status(status).build());
    }

    @Test
    void matchesPublishedArtistByNormalizedName() {
        Artist ocean = save("ocean", "Океан Ельзи", ArtistStatus.PUBLISHED);
        assertThat(detector.findDuplicate("«океан  ЕЛЬЗИ»")).map(Artist::getId).contains(ocean.getId());
    }

    @Test
    void ignoresHiddenArtistsAndOtherNames() {
        save("hidden", "Скрябін", ArtistStatus.HIDDEN);
        save("other", "Друга ріка", ArtistStatus.PUBLISHED);
        assertThat(detector.findDuplicate("Скрябін")).isEmpty();
        assertThat(detector.findDuplicate("Друга")).isEmpty();
        assertThat(detector.findDuplicate("  ")).isEmpty();
    }
}
