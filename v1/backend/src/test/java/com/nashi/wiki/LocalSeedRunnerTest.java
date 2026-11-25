package com.nashi.wiki;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LocalSeedRunnerTest {

    private final ArtistImportServiceTest.FakeStore store = new ArtistImportServiceTest.FakeStore();
    private final ArtistImportService importService = mock(ArtistImportService.class);

    private void run(boolean onStartup) {
        new LocalSeedRunner(importService, store, onStartup).run(new DefaultApplicationArguments());
    }

    @Test
    void seedsWhenNoWikipediaArtists() {
        store.save(Artist.builder().name("З форми").slug("z-formy").build());

        run(true);

        verify(importService).startInBackground();
    }

    @Test
    void skipsWhenAlreadySeeded() {
        store.save(Artist.builder().name("Тарас Шевченко").slug("taras").source(ArtistSource.WIKIPEDIA).build());

        run(true);

        verify(importService, never()).startInBackground();
    }

    @Test
    void skipsWhenDisabled() {
        run(false);

        verify(importService, never()).startInBackground();
    }

    @Test
    void existsOnlyInTheLocalProfile() {
        Profile profile = LocalSeedRunner.class.getAnnotation(Profile.class);

        assertNotNull(profile);
        assertArrayEquals(new String[]{"local"}, profile.value());
    }
}
