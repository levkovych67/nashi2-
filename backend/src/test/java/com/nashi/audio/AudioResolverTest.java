package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AudioResolverTest {

    static final class FakeProvider implements AudioProvider {
        final AudioSource source;
        final Supplier<List<AudioClip>> result;
        final AtomicInteger calls = new AtomicInteger();

        FakeProvider(AudioSource source, Supplier<List<AudioClip>> result) {
            this.source = source;
            this.result = result;
        }

        static FakeProvider returning(AudioSource source, int count) {
            return new FakeProvider(source, () -> clips(source, count));
        }

        @Override
        public AudioSource source() {
            return source;
        }

        @Override
        public List<AudioClip> find(Artist artist) {
            calls.incrementAndGet();
            return result.get();
        }
    }

    static List<AudioClip> clips(AudioSource source, int count) {
        List<AudioClip> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new AudioClip(source.name().toLowerCase() + i, source, "e" + i, null, null, "T" + i, 30, "a", null));
        }
        return list;
    }

    private static Artist artistWith(AudioClip... existing) {
        return Artist.builder().id("a1").audio(List.of(existing)).build();
    }

    private static final AudioPolicy ALL = AudioPolicy.of(true, true, true);

    @Test
    void resolverKeepsUploadsAndOrdersSources() {
        // given in the wrong order on purpose
        AudioResolver resolver = new AudioResolver(List.of(
                FakeProvider.returning(AudioSource.COMMONS, 1),
                FakeProvider.returning(AudioSource.DEEZER, 1),
                FakeProvider.returning(AudioSource.ITUNES, 1)), ALL);
        AudioClip upload = new AudioClip("up1", AudioSource.UPLOAD, null, "k", "u", "Mine", 20, "a", null);

        assertThat(resolver.resolve(artistWith(upload))).extracting(AudioClip::clipId)
                .containsExactly("up1", "deezer0", "itunes0", "commons0");
    }

    @Test
    void resolverCapsClips() {
        AudioResolver resolver = new AudioResolver(List.of(
                FakeProvider.returning(AudioSource.DEEZER, 7),
                FakeProvider.returning(AudioSource.ITUNES, 5),
                FakeProvider.returning(AudioSource.COMMONS, 5)), ALL);
        AudioClip u1 = new AudioClip("u1", AudioSource.UPLOAD, null, "k", "u", "A", 20, "a", null);
        AudioClip u2 = new AudioClip("u2", AudioSource.UPLOAD, null, "k2", "u", "B", 20, "a", null);

        List<AudioClip> result = resolver.resolve(artistWith(u1, u2));

        assertThat(result).hasSize(10);
        assertThat(result.stream().filter(c -> c.source() == AudioSource.DEEZER)).hasSize(5);
        assertThat(result.stream().filter(c -> c.source() == AudioSource.ITUNES)).hasSize(3);
        assertThat(result.stream().filter(c -> c.source() == AudioSource.COMMONS)).isEmpty();
        assertThat(result.subList(0, 2)).containsExactly(u1, u2);
    }

    @Test
    void uploadsAreNeverCutByTheCap() {
        List<AudioClip> uploads = clips(AudioSource.UPLOAD, 12);
        AudioResolver resolver = new AudioResolver(List.of(FakeProvider.returning(AudioSource.DEEZER, 3)), ALL);

        assertThat(resolver.resolve(Artist.builder().id("a").audio(uploads).build())).containsExactlyElementsOf(uploads);
    }

    @Test
    void providerFailureDoesNotDropOthers() {
        FakeProvider failing = new FakeProvider(AudioSource.DEEZER, () -> {
            throw new IllegalStateException("boom");
        });
        AudioResolver resolver = new AudioResolver(List.of(failing, FakeProvider.returning(AudioSource.ITUNES, 2),
                FakeProvider.returning(AudioSource.COMMONS, 1)), ALL);
        AudioClip oldDeezer = clips(AudioSource.DEEZER, 1).get(0);

        List<AudioClip> result = resolver.resolve(artistWith(oldDeezer));

        // the failing source keeps what it had; the others are fresh
        assertThat(result).extracting(AudioClip::clipId).containsExactly("deezer0", "itunes0", "itunes1", "commons0");
    }

    @Test
    void disabledProvidersAreNeverCalledAndKeepTheirOldClips() {
        FakeProvider deezer = FakeProvider.returning(AudioSource.DEEZER, 2);
        FakeProvider itunes = FakeProvider.returning(AudioSource.ITUNES, 2);
        AudioResolver resolver = new AudioResolver(List.of(deezer, itunes), AudioPolicy.of(false, true, false));
        AudioClip oldDeezer = new AudioClip("dz_old", AudioSource.DEEZER, "1", null, null, "Old", 30, "a", null);

        List<AudioClip> result = resolver.resolve(artistWith(oldDeezer));

        assertThat(deezer.calls).hasValue(0);
        assertThat(itunes.calls).hasValue(1);
        assertThat(result).extracting(AudioClip::clipId).containsExactly("dz_old", "itunes0", "itunes1");
    }

    @Test
    void staleClipsOfAnEnabledSourceAreReplaced() {
        AudioResolver resolver = new AudioResolver(List.of(FakeProvider.returning(AudioSource.DEEZER, 1)), ALL);
        AudioClip stale = new AudioClip("dz_old", AudioSource.DEEZER, "1", null, null, "Old", 30, "a", null);

        assertThat(resolver.resolve(artistWith(stale))).extracting(AudioClip::clipId).containsExactly("deezer0");
    }

    @Test
    void artistWithoutAudioListIsFine() {
        AudioResolver resolver = new AudioResolver(List.of(FakeProvider.returning(AudioSource.ITUNES, 1)), ALL);

        assertThat(resolver.resolve(Artist.builder().id("a").build())).hasSize(1);
    }
}
