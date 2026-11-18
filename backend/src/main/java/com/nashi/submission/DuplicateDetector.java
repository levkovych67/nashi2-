package com.nashi.submission;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Names;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Finds the published artist a submission may repeat, by normalized name. */
@Component
public class DuplicateDetector {

    private final ArtistRepository artists;

    public DuplicateDetector(ArtistRepository artists) {
        this.artists = artists;
    }

    public Optional<Artist> findDuplicate(String name) {
        String normalized = Names.normalize(name);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return artists.findByStatusAndNameNormalized(ArtistStatus.PUBLISHED, normalized).stream().findFirst();
    }
}
