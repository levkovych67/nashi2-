package com.nashi.musician;

import com.nashi.artist.Artist;
import com.nashi.audio.AudioPolicy;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.Origin;
import com.nashi.artist.Video;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.geo.Region;
import com.nashi.media.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicianMapperTest {

    private final FileStorage storage = new FileStorage() {
        public void put(String key, byte[] bytes, String contentType) {
        }

        public byte[] get(String key) {
            return new byte[0];
        }

        public void copy(String fromKey, String toKey) {
        }

        public void delete(String key) {
        }

        public String publicUrl(String key) {
            return "https://cdn.test/" + key;
        }
    };
    private final MusicianMapper mapper = new MusicianMapper(storage, AudioPolicy.of(true, true, true));

    private static Artist.ArtistBuilder base() {
        return Artist.builder().id("1").slug("s").name("N").likesCount(3);
    }

    @Test
    void thumbPrefersStoredKeyOverExternalUrl() {
        Artist a = base().images(List.of(new ImageAsset("artists/1/a.jpg", "https://ext/a.jpg", "artists/1/a_t.jpg",
                "https://ext/a_t.jpg", "alt", null))).build();

        assertThat(mapper.toListItem(a).thumbUrl()).isEqualTo("https://cdn.test/artists/1/a_t.jpg");
        assertThat(mapper.toDetails(a).images().get(0).url()).isEqualTo("https://cdn.test/artists/1/a.jpg");
    }

    @Test
    void thumbFallsBackToExternalUrlAndIsNullWithoutImages() {
        Artist ext = base().images(List.of(new ImageAsset(null, "https://ext/a.jpg", null, "https://ext/a_t.jpg",
                "alt", null))).build();

        assertThat(mapper.toListItem(ext).thumbUrl()).isEqualTo("https://ext/a_t.jpg");
        assertThat(mapper.toListItem(base().build()).thumbUrl()).isNull();
    }

    @Test
    void originUsesPinCoordinates() {
        Origin o = new Origin("Q1", "Львів", Region.LVIV, OriginBasis.BIRTH, PinPrecision.CITY,
                new GeoJsonPoint(24.0, 49.8));
        Artist a = base().origin(o).pin(new GeoJsonPoint(24.1, 49.9)).build();

        MusicianDetails.OriginDto origin = mapper.toDetails(a).origin();

        assertThat(origin.lat()).isEqualTo(49.9);
        assertThat(origin.lon()).isEqualTo(24.1);
        assertThat(origin.regionName()).isEqualTo("Львівська область");
    }

    @Test
    void originIsNullWithoutPin() {
        Origin o = new Origin("Q1", "Львів", Region.LVIV, OriginBasis.BIRTH, PinPrecision.CITY, null);

        assertThat(mapper.toDetails(base().origin(o).build()).origin()).isNull();
    }

    @Test
    void linksSortedByGroupThenStored() {
        Artist a = base().links(List.of(
                new Link(LinkPlatform.INSTAGRAM, "https://i"),
                new Link(LinkPlatform.WEBSITE, "https://w"),
                new Link(LinkPlatform.YOUTUBE, "https://y"),
                new Link(LinkPlatform.BANDCAMP, "https://b"),
                new Link(LinkPlatform.SPOTIFY, "https://s"))).build();

        assertThat(mapper.toDetails(a).links()).extracting(MusicianDetails.LinkDto::platform)
                .containsExactly(LinkPlatform.BANDCAMP, LinkPlatform.SPOTIFY, LinkPlatform.YOUTUBE,
                        LinkPlatform.WEBSITE, LinkPlatform.INSTAGRAM);
    }

    @Test
    void clipsHaveNoPlaybackUrl() {
        Artist a = base().audio(List.of(new AudioClip("c1", AudioSource.UPLOAD, null, "k", "https://play", "T", 12,
                "attr", "https://ext"))).build();

        MusicianDetails d = mapper.toDetails(a);

        assertThat(d.hasAudio()).isTrue();
        assertThat(d.materials().clips()).containsExactly(
                new MusicianDetails.ClipSummary("c1", "T", AudioSource.UPLOAD, 12, "https://ext"));
        assertThat(MusicianDetails.ClipSummary.class.getRecordComponents()).extracting("name")
                .doesNotContain("url", "key");
    }

    @Test
    void videosDerivedFromYoutubeLinksWhenNoneStored() {
        Artist a = base().links(List.of(new Link(LinkPlatform.YOUTUBE, "https://youtu.be/aaaaaaaaaaa"),
                new Link(LinkPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=bbbbbbbbbbb"))).build();

        assertThat(mapper.toDetails(a).materials().videos())
                .containsExactly(new Video("aaaaaaaaaaa", "N"));
    }

    @Test
    void textSourceNullForSubmission() {
        assertThat(mapper.toDetails(base().build()).textSource()).isNull();
    }

    @Test
    void clipsOfADisabledSourceAreHiddenAndDoNotLightUpHasAudio() {
        MusicianMapper deezerOff = new MusicianMapper(storage, AudioPolicy.of(false, true, true));
        AudioClip deezer = new AudioClip("dz_1", AudioSource.DEEZER, "1", null, null, "D", 30, "a", null);
        AudioClip itunes = new AudioClip("it_1", AudioSource.ITUNES, "2", null, "https://p", "I", 30, "a", null);

        Artist onlyDeezer = base().audio(List.of(deezer)).build();
        assertThat(deezerOff.toListItem(onlyDeezer).hasAudio()).isFalse();
        assertThat(deezerOff.toDetails(onlyDeezer).hasAudio()).isFalse();
        assertThat(deezerOff.toDetails(onlyDeezer).materials().clips()).isEmpty();

        Artist both = base().audio(List.of(deezer, itunes)).build();
        assertThat(deezerOff.toListItem(both).hasAudio()).isTrue();
        assertThat(deezerOff.toDetails(both).materials().clips()).extracting(MusicianDetails.ClipSummary::clipId)
                .containsExactly("it_1");
    }
}
