package com.nashi.artist;

import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code nameNormalized} in step with {@code name} on every save. Slugs are not generated here: they are
 * allocated explicitly with {@link SlugAllocator}, because allocation needs the uniqueness check.
 */
@Component
public class ArtistBeforeConvertCallback implements BeforeConvertCallback<Artist> {

    @Override
    public Artist onBeforeConvert(Artist artist, String collection) {
        artist.setNameNormalized(Names.normalize(artist.getName()));
        return artist;
    }
}
