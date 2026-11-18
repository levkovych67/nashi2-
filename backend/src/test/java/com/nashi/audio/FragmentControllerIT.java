package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.MusicianKind;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class FragmentControllerIT extends MongoIT {

    @Autowired
    private RestTestClient http;

    @Autowired
    private ArtistRepository artists;

    @MockitoBean
    private DeezerPreviewUrls deezer;

    @BeforeEach
    @AfterEach
    void clear() {
        artists.deleteAll();
    }

    private String save(ArtistStatus status, AudioClip... clips) {
        return artists.save(Artist.builder().slug("s" + System.nanoTime()).name("Н").kind(MusicianKind.BAND)
                .status(status).summary("o").audio(List.of(clips)).build()).getId();
    }

    private static AudioClip clip(String id, AudioSource source, int duration) {
        return new AudioClip(id, source, "ext" + id, "k/" + id + ".mp3", "https://stored/" + id, "Title " + id,
                duration, "attr", "https://ext/" + id);
    }

    private RestTestClient.ResponseSpec get(String id, String query) {
        return http.get().uri("/api/v2/musicians/" + id + "/fragment" + query).exchange();
    }

    @Test
    void noAudioIs204() {
        get(save(ArtistStatus.PUBLISHED), "").expectStatus().isNoContent();
    }

    @Test
    void hiddenMusicianIs404() {
        String id = save(ArtistStatus.HIDDEN, clip("u", AudioSource.UPLOAD, 30));
        get(id, "").expectStatus().isNotFound().expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void unknownMusicianIs404() {
        get("000000000000000000000000", "").expectStatus().isNotFound();
        get("not-an-id", "").expectStatus().isNotFound();
    }

    @Test
    void returnsContractShape() {
        String id = save(ArtistStatus.PUBLISHED, clip("c1", AudioSource.COMMONS, 40));
        get(id, "").expectStatus().isOk().expectHeader().cacheControl(org.springframework.http.CacheControl.noStore())
                .expectBody()
                .jsonPath("$.clipId").isEqualTo("c1")
                .jsonPath("$.url").isEqualTo("https://stored/c1")
                .jsonPath("$.durationSec").isEqualTo(15)
                .jsonPath("$.fadeInMs").isEqualTo(300)
                .jsonPath("$.fadeOutMs").isEqualTo(600)
                .jsonPath("$.title").isEqualTo("Title c1")
                .jsonPath("$.source").isEqualTo("COMMONS")
                .jsonPath("$.attribution").isEqualTo("attr")
                .jsonPath("$.externalUrl").isEqualTo("https://ext/c1")
                .jsonPath("$.startSec").isNumber()
                .jsonPath("$.expiresAt").doesNotExist();
    }

    @Test
    void deezerClipHasFreshUrlAndExpiresAt() {
        String id = save(ArtistStatus.PUBLISHED, clip("d", AudioSource.DEEZER, 30));
        when(deezer.fresh("extd")).thenReturn(Optional.of(
                new DeezerPreviewUrls.PreviewUrl("https://dz/fresh.mp3", Instant.parse("2026-11-17T10:09:00Z"))));
        get(id, "").expectStatus().isOk().expectBody()
                .jsonPath("$.url").isEqualTo("https://dz/fresh.mp3")
                .jsonPath("$.expiresAt").isEqualTo("2026-11-17T10:09:00Z");
    }

    @Test
    void deezerFailureFallsBackToOtherClip() {
        String id = save(ArtistStatus.PUBLISHED, clip("d", AudioSource.DEEZER, 30), clip("u", AudioSource.UPLOAD, 30));
        when(deezer.fresh(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 10; i++) {
            get(id, "").expectStatus().isOk().expectBody().jsonPath("$.clipId").isEqualTo("u");
        }
    }

    @Test
    void excludeSkipsThatClip() {
        String id = save(ArtistStatus.PUBLISHED, clip("u1", AudioSource.UPLOAD, 30), clip("u2", AudioSource.UPLOAD, 30));
        for (int i = 0; i < 10; i++) {
            get(id, "?exclude=u1").expectStatus().isOk().expectBody().jsonPath("$.clipId").isEqualTo("u2");
        }
    }
}
