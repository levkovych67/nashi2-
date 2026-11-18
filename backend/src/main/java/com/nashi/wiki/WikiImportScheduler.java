package com.nashi.wiki;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the Wikipedia import once a month: on the 1st at 04:00 Kyiv time ({@code nashi.wiki-import.cron}), when
 * {@code nashi.wiki-import.enabled=true}. It is off by default (and in local and test): production turns it on at
 * cutover, after {@code /migrate_v1 run} (ADR 0002). {@code @Scheduled} itself needs {@code nashi.scheduling.enabled}.
 */
@Component
public class WikiImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(WikiImportScheduler.class);

    private final MusicianImportService importService;
    private final boolean enabled;

    public WikiImportScheduler(MusicianImportService importService,
                               @Value("${nashi.wiki-import.enabled:false}") boolean enabled) {
        this.importService = importService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${nashi.wiki-import.cron:0 0 4 1 * *}", zone = "Europe/Kyiv")
    public void monthly() {
        if (!enabled) {
            log.info("[WIKI] the monthly import is switched off (nashi.wiki-import.enabled=false)");
            return;
        }
        importService.startInBackground();
    }
}
