package com.nashi.wiki;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WikiImportSchedulerTest {

    private final MusicianImportService importService = mock(MusicianImportService.class);

    @Test
    void runsOnTheFirstOfTheMonthAtFourKyivTimeUnlessConfigured() throws NoSuchMethodException {
        Scheduled scheduled = WikiImportScheduler.class.getMethod("monthly").getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Europe/Kyiv");
        assertThat(scheduled.cron()).isEqualTo("${nashi.wiki-import.cron:0 0 4 1 * *}");
        ZoneId kyiv = ZoneId.of(scheduled.zone());
        assertThat(CronExpression.parse("0 0 4 1 * *").next(ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, kyiv)))
                .isEqualTo(ZonedDateTime.of(2026, 12, 1, 4, 0, 0, 0, kyiv));
    }

    @Test
    void startsTheImportWhenEnabled() {
        new WikiImportScheduler(importService, true).monthly();

        verify(importService).startInBackground();
    }

    @Test
    void doesNothingWhenDisabled() {
        new WikiImportScheduler(importService, false).monthly();

        verify(importService, never()).startInBackground();
    }
}
