package com.nashi.wiki;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ImageAsset;
import com.nashi.geo.Region;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRun;
import com.nashi.jobs.JobRunLog;
import com.nashi.media.FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceType;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One import run against the real store, gazetteer, job log and MinIO; Wikimedia is faked. */
class MusicianImportServiceIT extends MongoIT {

    private static final String KHUTIR = "Q990001";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x20, 0x21};

    @Autowired
    private MongoMusicianStore store;
    @Autowired
    private PlaceStore places;
    @Autowired
    private JobRunLog runLog;
    @Autowired
    private FileStorage storage;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private RecordedEvents recorded;
    @Autowired
    private Clock clock;

    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void clean() {
        artists.deleteAll();
        mongo.remove(new Query(), JobRun.class);
        mongo.remove(Query.query(Criteria.where("_id").is(KHUTIR)), Place.class);
        recorded.clear();
    }

    @AfterEach
    void removePlace() {
        mongo.remove(Query.query(Criteria.where("_id").is(KHUTIR)), Place.class);
    }

    @Test
    void importsIntoTheDatabaseAndStorageAndLogsTheRun() {
        MusicianImportServiceTest.FakeSource source = new MusicianImportServiceTest.FakeSource();
        source.add("Q990101", "Хуторянин", new WikiPlace(KHUTIR, "Хутір Тестовий", 49.1, 32.2, Region.POLTAVA));
        source.add("Q990102", "Без місця", null);
        WikiFileHttp files = url -> new WikiFileHttp.WikiFile(JPEG, "image/jpeg");
        MusicianImportService service = new MusicianImportService(() -> source, store, places,
                new CommonsThumbnails(files, storage), messages::add, runLog, Runnable::run, clock, 0, false);

        ImportReport report = service.runNow();

        assertThat(report.added()).isEqualTo(2);
        assertThat(report.withoutPlace()).isEqualTo(1);
        Artist artist = artists.findByWikidataId("Q990101").orElseThrow();
        assertThat(artist.getOrigin().placeId()).isEqualTo(KHUTIR);
        assertThat(artist.getPin()).isNotNull();
        assertThat(places.find(KHUTIR)).get().satisfies(place -> {
            assertThat(place.type()).isEqualTo(PlaceType.OTHER);
            assertThat(place.region()).isEqualTo(Region.POLTAVA);
        });
        ImageAsset photo = artist.getImages().get(0);
        assertThat(photo.thumbKey()).isEqualTo("artists/" + artist.getId() + "/0-thumb.jpg");
        assertThat(storage.get(photo.thumbKey())).isEqualTo(JPEG);
        assertThat(photo.url()).endsWith("/800px-Q990101.jpg");
        JobRun run = runLog.lastRun(JobKind.WIKI_IMPORT).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(JobRunLog.OK);
        assertThat(run.getReport()).containsEntry("added", 2).containsEntry("withoutPlace", 1);
        assertThat(recorded.artistEvents()).contains(new ArtistChangedEvent(artist.getId()));
        assertThat(messages).hasSize(1);
    }
}
