package com.nashi.wiki;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the Wikipedia import once a month: the 1st at 04:00 Kyiv time unless configured otherwise.
 * The zone id is "Europe/Kiev": "Europe/Kyiv" exists only since tzdata 2022b (JDK 17.0.5), and an unknown zone
 * would stop the app from starting on an older server JDK.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikiImportScheduler {

    private final ArtistImportService importService;

    @Value("${nashi.wiki-import.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${nashi.wiki-import.cron:0 0 4 1 * *}", zone = "Europe/Kiev")
    public void monthly() {
        if (!enabled) {
            log.info("[WIKI] monthly import is switched off");
            return;
        }
        importService.startInBackground();
    }
}
