package com.nashi.audio;

import com.nashi.api.NotFoundException;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.config.NashiProperties;
import com.nashi.media.FileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** The random fragment of a published musician's playable clips. */
@Service
public class FragmentService {

    static final int FADE_IN_MS = 300;
    static final int FADE_OUT_MS = 600;
    static final int MAX_ATTEMPTS = 3;

    private final ArtistRepository artists;
    private final AudioPolicy policy;
    private final FileStorage storage;
    private final DeezerPreviewUrls deezer;
    private final int fragmentSeconds;
    private final Supplier<RandomGenerator> random;

    @Autowired
    public FragmentService(ArtistRepository artists, AudioPolicy policy, FileStorage storage, DeezerPreviewUrls deezer,
                           NashiProperties properties) {
        this(artists, policy, storage, deezer, properties.audio().fragmentSeconds(), ThreadLocalRandom::current);
    }

    FragmentService(ArtistRepository artists, AudioPolicy policy, FileStorage storage, DeezerPreviewUrls deezer,
                    int fragmentSeconds, Supplier<RandomGenerator> random) {
        this.artists = artists;
        this.policy = policy;
        this.storage = storage;
        this.deezer = deezer;
        this.fragmentSeconds = fragmentSeconds;
        this.random = random;
    }

    /**
     * @throws NotFoundException for an unknown or hidden musician
     * @return empty (204) when no playable clip is left, including after Deezer failed for the picked clips
     */
    public Optional<Fragment> fragment(String artistId, @Nullable String exclude) {
        Artist artist = artists.findById(artistId)
                .filter(a -> a.getStatus() == ArtistStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("musician"));
        List<AudioClip> candidates = new ArrayList<>(policy.playableClips(artist.getAudio()).stream()
                .filter(c -> c.durationSec() > 0).toList());
        RandomGenerator rnd = random.get();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Optional<Pick> pick = FragmentPicker.pick(candidates, exclude, fragmentSeconds, rnd);
            if (pick.isEmpty()) {
                return Optional.empty();
            }
            Optional<Fragment> fragment = toFragment(pick.get());
            if (fragment.isPresent()) {
                return fragment;
            }
            candidates.remove(pick.get().clip());
        }
        return Optional.empty();
    }

    private Optional<Fragment> toFragment(Pick pick) {
        AudioClip c = pick.clip();
        String url;
        java.time.Instant expiresAt = null;
        switch (c.source()) {
            case UPLOAD -> url = c.key() == null ? null : storage.publicUrl(c.key());
            case DEEZER -> {
                Optional<DeezerPreviewUrls.PreviewUrl> preview = deezer.fresh(c.externalId());
                url = preview.map(DeezerPreviewUrls.PreviewUrl::url).orElse(null);
                expiresAt = preview.map(DeezerPreviewUrls.PreviewUrl::expiresAt).orElse(null);
            }
            default -> url = c.url();
        }
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Fragment(c.clipId(), url, pick.startSec(), pick.durationSec(), FADE_IN_MS, FADE_OUT_MS,
                c.title(), c.source(), c.attribution(), c.externalUrl(), expiresAt));
    }
}
