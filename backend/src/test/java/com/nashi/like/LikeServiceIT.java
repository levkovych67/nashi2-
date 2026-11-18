package com.nashi.like;

import com.nashi.api.NotFoundException;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.map.MapFixtures;
import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeServiceIT extends MongoIT {

    private static final Logger LOG = LoggerFactory.getLogger(LikeServiceIT.class);

    @Autowired
    private LikeService service;
    @Autowired
    private ArtistRepository artists;

    @BeforeEach
    void clean() {
        artists.deleteAll();
    }

    private Artist seed(String slug, ArtistStatus status) {
        return artists.save(MapFixtures.artist(slug, Region.LVIV, MapFixtures.LVIV_ID, "Львів").status(status).build());
    }

    @Test
    void incrementsAtomicallyUnderConcurrency() throws Exception {
        Artist a = seed("conc", ArtistStatus.PUBLISHED);
        int calls = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                return service.add(a.getId(), 1);
            }));
        }
        long start = System.nanoTime();
        gate.countDown();
        for (Future<Long> f : futures) {
            f.get();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        pool.shutdown();
        LOG.info("NFR-04: {} concurrent add(id, 1) on 32 threads took {} ms", calls, ms);
        assertThat(artists.findById(a.getId()).orElseThrow().getLikesCount()).isEqualTo(calls);
    }

    @Test
    void addsBatchCount() {
        Artist a = seed("batch", ArtistStatus.PUBLISHED);
        assertThat(service.add(a.getId(), 37)).isEqualTo(37);
        assertThat(service.add(a.getId(), 3)).isEqualTo(40);
    }

    @Test
    void doesNotTouchUpdatedAt() {
        Artist a = seed("stamp", ArtistStatus.PUBLISHED);
        var before = artists.findById(a.getId()).orElseThrow().getUpdatedAt();
        service.add(a.getId(), 5);
        assertThat(artists.findById(a.getId()).orElseThrow().getUpdatedAt()).isEqualTo(before);
    }

    @Test
    void hiddenArtistIs404() {
        Artist a = seed("hidden", ArtistStatus.HIDDEN);
        assertThatThrownBy(() -> service.add(a.getId(), 1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.add("nope", 1)).isInstanceOf(NotFoundException.class);
    }
}
