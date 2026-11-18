package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.config.NashiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which stored clips may be shown and played. Uploads always; Deezer, iTunes and Commons clips only while their
 * provider is switched on ({@code nashi.audio.providers.*.enabled}), because the legal OK for Deezer and Apple
 * previews is pending. "Has audio" on cards and map pins, the details page and the fragment endpoint all use it.
 */
@Component
public class AudioPolicy {

    private final Set<AudioSource> enabled;

    @Autowired
    public AudioPolicy(NashiProperties properties) {
        this(properties.audio() == null || properties.audio().providers() == null ? null : properties.audio().providers());
    }

    private AudioPolicy(NashiProperties.ProvidersProps providers) {
        this.enabled = providers == null ? EnumSet.of(AudioSource.UPLOAD)
                : sources(providers.deezer().enabled(), providers.itunes().enabled(), providers.commons().enabled());
    }

    private AudioPolicy(Set<AudioSource> enabled) {
        this.enabled = enabled;
    }

    /** For tests and tools: the switches given directly. */
    public static AudioPolicy of(boolean deezer, boolean itunes, boolean commons) {
        return new AudioPolicy(sources(deezer, itunes, commons));
    }

    private static Set<AudioSource> sources(boolean deezer, boolean itunes, boolean commons) {
        Set<AudioSource> set = EnumSet.of(AudioSource.UPLOAD);
        if (deezer) {
            set.add(AudioSource.DEEZER);
        }
        if (itunes) {
            set.add(AudioSource.ITUNES);
        }
        if (commons) {
            set.add(AudioSource.COMMONS);
        }
        return set;
    }

    public boolean enabled(AudioSource source) {
        return enabled.contains(source);
    }

    public boolean playable(AudioClip clip) {
        return clip != null && enabled.contains(clip.source());
    }

    public List<AudioClip> playableClips(List<AudioClip> clips) {
        return clips == null ? List.of() : clips.stream().filter(this::playable).toList();
    }

    public boolean hasAudio(Artist artist) {
        return artist.getAudio() != null && artist.getAudio().stream().anyMatch(this::playable);
    }
}
