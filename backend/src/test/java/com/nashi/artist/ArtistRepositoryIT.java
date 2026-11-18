package com.nashi.artist;

import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistRepositoryIT extends MongoIT {

    @Autowired
    private ArtistRepository artists;

    @AfterEach
    void cleanUp() {
        artists.deleteAll();
    }

    private Artist newArtist() {
        return Artist.builder().id("a" + UUID.randomUUID().toString().replace("-", "").substring(0, 23))
                .slug("slug-" + UUID.randomUUID()).name("«Океан Ельзи»").status(ArtistStatus.PUBLISHED).build();
    }

    @Test
    void savingWithPreassignedIdSetsTimestamps() {
        Artist saved = artists.save(newArtist());

        Artist loaded = artists.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isEqualTo(0L);
    }

    @Test
    void secondSaveChangesUpdatedAtButNotCreatedAt() throws InterruptedException {
        Artist first = artists.save(newArtist());
        Artist before = artists.findById(first.getId()).orElseThrow();
        Instant createdBefore = before.getCreatedAt();
        Instant updatedBefore = before.getUpdatedAt();
        Thread.sleep(20);

        before.setTagline("changed");
        artists.save(before);

        Artist after = artists.findById(first.getId()).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(createdBefore);
        assertThat(after.getUpdatedAt()).isAfter(updatedBefore);
    }

    @Test
    void staleVersionSaveIsRejected() {
        Artist saved = artists.save(newArtist());
        Artist copyA = artists.findById(saved.getId()).orElseThrow();
        Artist copyB = artists.findById(saved.getId()).orElseThrow();
        copyA.setTagline("a");
        artists.save(copyA);

        copyB.setTagline("b");
        assertThatThrownBy(() -> artists.save(copyB)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void saveFillsNameNormalized() {
        Artist saved = artists.save(newArtist());

        assertThat(artists.findById(saved.getId()).orElseThrow().getNameNormalized()).isEqualTo("океан ельзи");
        assertThat(artists.findByStatusAndNameNormalized(ArtistStatus.PUBLISHED, "океан ельзи")).hasSize(1);
        assertThat(artists.findBySlug(saved.getSlug())).isPresent();
    }
}
