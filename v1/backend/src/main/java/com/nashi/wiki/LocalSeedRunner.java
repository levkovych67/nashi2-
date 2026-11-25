package com.nashi.wiki;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fills an empty local database: in the {@code local} profile, with {@code nashi.wiki-import.on-startup=true} and no
 * artist from Wikipedia yet, starts the import in the background at startup ({@code nashi.wiki-import.limit-per-category}
 * keeps it small).
 */
@Slf4j
@Profile("local")
@Component
public class LocalSeedRunner implements ApplicationRunner {

    private final ArtistImportService importService;
    private final ArtistStore store;
    private final boolean onStartup;

    public LocalSeedRunner(ArtistImportService importService, ArtistStore store,
                           @Value("${nashi.wiki-import.on-startup:false}") boolean onStartup) {
        this.importService = importService;
        this.store = store;
        this.onStartup = onStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!onStartup) {
            log.info("[WIKI] local seeding is off (nashi.wiki-import.on-startup=false)");
            return;
        }
        long imported = store.countWikipediaArtists();
        if (imported > 0) {
            log.info("[WIKI] local database already has {} artists from Wikipedia; no seeding", imported);
            return;
        }
        log.info("[WIKI] local database has no artists from Wikipedia; seeding in the background");
        importService.startInBackground();
    }
}
