package com.nashi.map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Origin;
import com.nashi.audio.AudioPolicy;
import com.nashi.geo.RegionCatalog;
import com.nashi.geo.RegionMeta;
import com.nashi.media.FileStorage;
import com.nashi.media.ImageUrls;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The map snapshot: one cached entry, dropped by {@link ArtistChangedEvent} and rebuilt lazily on the next read.
 * Every writer of published artists publishes that event (moderation, {@code /hide} and {@code /show}, the Wikipedia
 * import, enrichment, audio resolution and the V1 migration); {@code expireAfterWrite} of 5 minutes is only a backstop
 * for a listener that failed or a change made by hand in the database. Caffeine's {@code get(key, loader)} runs one
 * load at a time, so concurrent requests share one rebuild.
 * <p>
 * The version (the {@code ETag} of the map endpoints) hashes the response shape ({@link #SHAPE}), the enabled audio
 * sources (they decide {@code hasAudio} on pins) and {@code id:updatedAt} of every pinned artist, so a provider switch
 * or a new pin shape changes it even when no artist changed.
 */
@Service
public class MapService {

    private static final Logger LOG = LoggerFactory.getLogger(MapService.class);
    private static final String KEY = "snapshot";
    /** Bump when the JSON of {@code /map/*} changes shape, so clients holding the old ETag get the new body. */
    static final String SHAPE = "map-v2";

    private final MongoTemplate mongo;
    private final RegionCatalog regions;
    private final PlaceStore places;
    private final FileStorage storage;
    private final AudioPolicy audioPolicy;
    private final Cache<String, MapSnapshot> cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public MapService(MongoTemplate mongo, RegionCatalog regions, PlaceStore places, FileStorage storage,
                      AudioPolicy audioPolicy) {
        this.mongo = mongo;
        this.regions = regions;
        this.places = places;
        this.storage = storage;
        this.audioPolicy = audioPolicy;
    }

    /**
     * Relies on Caffeine's synchronous {@code get(key, loader)}: the load runs inside the map's per-key compute, so
     * {@code invalidate(key)} during a load waits for it to finish and then removes the entry. A build that started
     * before an event is therefore never served after the event; the next read rebuilds.
     */
    public MapSnapshot snapshot() {
        return cache.get(KEY, k -> build());
    }

    @EventListener
    public void onArtistChanged(ArtistChangedEvent event) {
        cache.invalidate(KEY);
    }

    private MapSnapshot build() {
        long start = System.nanoTime();
        Query query = Query.query(Criteria.where("status").is(ArtistStatus.PUBLISHED)
                .and("pin").ne(null).and("origin.placeId").ne(null).and("origin.region").ne(null));
        query.fields().include("slug", "name", "kind", "pin", "origin", "images", "audio", "likesCount", "updatedAt");
        List<Artist> artists = new ArrayList<>(mongo.find(query, Artist.class));
        artists.sort(Comparator.comparing(Artist::getId));

        List<MapPin> pins = new ArrayList<>(artists.size());
        Map<com.nashi.geo.Region, Long> regionCounts = new HashMap<>();
        Map<String, PlaceAcc> placeCounts = new LinkedHashMap<>();
        MessageDigest sha = sha256();
        sha.update((versionSeed(audioPolicy) + "\n").getBytes(StandardCharsets.UTF_8));
        for (Artist a : artists) {
            Origin o = a.getOrigin();
            pins.add(new MapPin(a.getId(), a.getSlug(), a.getName(), a.getKind(), round(a.getPin().getY()), round(a.getPin().getX()),
                    o.region(), o.placeId(), thumb(a), audioPolicy.hasAudio(a)));
            regionCounts.merge(o.region(), 1L, Long::sum);
            placeCounts.computeIfAbsent(o.placeId(), id -> new PlaceAcc(o)).count++;
            // "<id>:<updatedAt>" per artist, joined with '\n' (a character that is in neither part)
            sha.update((a.getId() + ":" + a.getUpdatedAt() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        String version = HexFormat.of().formatHex(sha.digest()).substring(0, 8);

        List<RegionStat> regionStats = regions.all().stream()
                .map(m -> new RegionStat(m.code(), m.name(), m.center(), m.bbox(), regionCounts.getOrDefault(m.code(), 0L)))
                .toList();
        List<PlaceStat> placeStats = placeCounts.entrySet().stream().map(e -> placeStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(PlaceStat::placeId)).toList();

        LOG.info("Map snapshot {} built in {} ms: {} pins, {} places", version, (System.nanoTime() - start) / 1_000_000,
                pins.size(), placeStats.size());
        return new MapSnapshot(version, List.copyOf(pins), regionStats, placeStats);
    }

    /** What the version depends on besides the artists: the shape constant and the enabled audio sources. */
    static String versionSeed(AudioPolicy policy) {
        return SHAPE + "|" + policy.enabledSources();
    }

    private PlaceStat placeStat(String placeId, PlaceAcc acc) {
        Origin o = acc.origin;
        Place place = places.find(placeId).orElse(null);
        if (place != null) {
            return new PlaceStat(placeId, o.placeName() != null ? o.placeName() : place.name(), place.region(),
                    place.type(), place.lat(), place.lon(), acc.count);
        }
        // The place left the gazetteer: fall back to the settlement point stored with the artist.
        RegionMeta region = regions.of(o.region());
        double lat = o.center() != null ? o.center().getY() : region.center()[1];
        double lon = o.center() != null ? o.center().getX() : region.center()[0];
        return new PlaceStat(placeId, Objects.requireNonNullElse(o.placeName(), placeId), o.region(), PlaceType.OTHER,
                lat, lon, acc.count);
    }

    /** 4 decimals (about 11 m; pins are offset by 150 m or more anyway) keeps /map/pins under the NFR-02 gzip budget. */
    private static double round(double degrees) {
        return Math.round(degrees * 1e4) / 1e4;
    }

    private String thumb(Artist a) {
        List<ImageAsset> images = a.getImages();
        return images == null || images.isEmpty() ? null : ImageUrls.thumb(images.get(0), storage);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class PlaceAcc {
        final Origin origin;
        long count;

        PlaceAcc(Origin origin) {
            this.origin = origin;
        }
    }
}
