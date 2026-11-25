package com.nashi.config;

import com.nashi.domain.Artist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtistBeforeConvertCallbackTest {

    private final ArtistBeforeConvertCallback callback = new ArtistBeforeConvertCallback();

    @Test
    void callbackKeepsAnExistingSlug() {
        Artist artist = Artist.builder().name("Океан Ельзи").slug("океан-ельзи-2").build();

        callback.onBeforeConvert(artist, "artists");

        assertEquals("океан-ельзи-2", artist.getSlug());
    }

    @Test
    void callbackFillsABlankSlugFromTheName() {
        Artist missing = Artist.builder().name("Океан Ельзи").build();
        Artist blank = Artist.builder().name("Океан Ельзи").slug("  ").build();

        callback.onBeforeConvert(missing, "artists");
        callback.onBeforeConvert(blank, "artists");

        assertEquals("океан-ельзи", missing.getSlug());
        assertEquals("океан-ельзи", blank.getSlug());
    }
}
