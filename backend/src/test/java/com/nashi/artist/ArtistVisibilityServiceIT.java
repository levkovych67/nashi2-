package com.nashi.artist;

import com.nashi.admin.StatsService;
import com.nashi.geo.Region;
import com.nashi.map.MapService;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.nashi.map.MapFixtures.KYIV_ID;
import static com.nashi.map.MapFixtures.artist;
import static com.nashi.map.MapFixtures.withAudio;
import static org.assertj.core.api.Assertions.assertThat;

class ArtistVisibilityServiceIT extends MongoIT {

    @Autowired
    private ArtistVisibilityService service;
    @Autowired
    private StatsService stats;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private SubmissionRepository submissions;
    @Autowired
    private MapService map;

    @BeforeEach
    @AfterEach
    void clear() {
        artists.deleteAll();
        submissions.deleteAll();
    }

    private Artist save(String slug, ArtistStatus status) {
        return artists.save(artist(slug, Region.KYIV_CITY, KYIV_ID, "Київ").status(status).build());
    }

    @Test
    void hideAndShowToggleStatusAndInvalidateMap() {
        save("vis-a", ArtistStatus.PUBLISHED);
        String v0 = map.snapshot().version();

        assertThat(service.hide("vis-a")).startsWith("Приховано: ").contains("vis-a");
        assertThat(artists.findBySlug("vis-a").orElseThrow().getStatus()).isEqualTo(ArtistStatus.HIDDEN);
        String v1 = map.snapshot().version();
        assertThat(v1).isNotEqualTo(v0);

        assertThat(service.show("vis-a")).startsWith("Знову показано: ");
        assertThat(artists.findBySlug("vis-a").orElseThrow().getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(map.snapshot().version()).isNotEqualTo(v1);
    }

    @Test
    void hideByWikidataId() {
        artists.save(artist("vis-q", Region.KYIV_CITY, KYIV_ID, "Київ").wikidataId("Q2017541").build());
        assertThat(service.hide("q2017541")).startsWith("Приховано: ");
        assertThat(artists.findBySlug("vis-q").orElseThrow().getStatus()).isEqualTo(ArtistStatus.HIDDEN);
    }

    @Test
    void hideOfHiddenAndShowOfPublishedChangeNothing() {
        save("vis-h", ArtistStatus.HIDDEN);
        save("vis-p", ArtistStatus.PUBLISHED);
        String version = map.snapshot().version();

        assertThat(service.hide("vis-h")).startsWith("Приховано: ");
        assertThat(service.show("vis-p")).startsWith("Не приховано: ").contains("vis-p");
        assertThat(map.snapshot().version()).isEqualTo(version);
    }

    @Test
    void unknownArgumentIsEscapedNotFound() {
        assertThat(service.hide("<b>x")).isEqualTo("Не знайдено: &lt;b&gt;x");
        assertThat(service.show("nope")).isEqualTo("Не знайдено: nope");
    }

    @Test
    void statsCountsPublishedPinsAudioPendingAndLikes() {
        save("s-pin", ArtistStatus.PUBLISHED);
        artists.save(artist("s-nopin", Region.KYIV_CITY, KYIV_ID, "Київ").pin(null).likesCount(5).build());
        artists.save(withAudio(artist("s-audio", Region.KYIV_CITY, KYIV_ID, "Київ")).likesCount(2).build());
        save("s-hidden", ArtistStatus.HIDDEN);
        submissions.save(Submission.builder().status(SubmissionStatus.PENDING).build());
        submissions.save(Submission.builder().status(SubmissionStatus.APPROVED).build());

        assertThat(stats.summary()).isEqualTo("Музикантів на карті: 2 · без піна: 1 · з аудіо: 1 · "
                + "заявок на модерації: 1 · лайків за весь час: 7");
    }
}
