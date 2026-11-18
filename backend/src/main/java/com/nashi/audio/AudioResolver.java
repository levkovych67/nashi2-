package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the clip list of a musician: own uploads first (always kept, never cut), then Deezer, iTunes and Commons in
 * that order, at most {@value #MAX_PER_SOURCE} per source and {@value #MAX_TOTAL} in all. A provider that is switched
 * off is not called; one that fails is logged and skipped. In both cases the clips that source already had stay, so a
 * network error or a switch never erases data.
 */
@Component
public class AudioResolver {

    static final int MAX_PER_SOURCE = 5;
    static final int MAX_TOTAL = 10;

    private static final Logger log = LoggerFactory.getLogger(AudioResolver.class);

    private final List<AudioProvider> providers;
    private final AudioPolicy policy;

    public AudioResolver(List<AudioProvider> providers, AudioPolicy policy) {
        this.providers = providers.stream().sorted(Comparator.comparing(AudioProvider::source)).toList();
        this.policy = policy;
    }

    public List<AudioClip> resolve(Artist artist) {
        List<AudioClip> existing = artist.getAudio() == null ? List.of() : artist.getAudio();
        List<AudioClip> result = new ArrayList<>(existing.stream().filter(c -> c.source() == AudioSource.UPLOAD).toList());
        for (AudioProvider provider : providers) {
            AudioSource source = provider.source();
            List<AudioClip> clips;
            if (!policy.enabled(source)) {
                clips = of(existing, source);
            } else {
                try {
                    clips = provider.find(artist);
                } catch (RuntimeException e) {
                    log.warn("[AUDIO] {} failed for artist {}: {}", source, artist.getId(), e.getMessage());
                    clips = of(existing, source);
                }
            }
            for (AudioClip clip : clips.stream().limit(MAX_PER_SOURCE).toList()) {
                if (result.size() >= MAX_TOTAL) {
                    break;
                }
                result.add(clip);
            }
        }
        return List.copyOf(result);
    }

    private static List<AudioClip> of(List<AudioClip> clips, AudioSource source) {
        return clips.stream().filter(c -> c.source() == source).toList();
    }
}
