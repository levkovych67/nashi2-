package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Discipline;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.geo.PinPlacer;
import com.nashi.geo.PlaceRef;
import com.nashi.geo.Region;
import com.nashi.media.FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceType;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtistFromSubmissionTest {

    private static final String SID = "6560a1b2c3d4e5f6a7b8c9d0";
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Place LVIV = new Place("Q36036", "Львів", "львів", PlaceType.CITY, Region.LVIV,
            new GeoJsonPoint(24.0315921, 49.841952), 182.0, 717_273L);
    private static final Place BRODY = new Place("Q465104", "Броди", "броди", PlaceType.CITY, Region.LVIV,
            new GeoJsonPoint(25.154166666, 50.078055555), 8.67, 23_454L);

    private final PlaceStore places = mock(PlaceStore.class);
    private final ArtistRepository artists = mock(ArtistRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final ArtistFromSubmission factory =
            new ArtistFromSubmission(places, artists, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void places() {
        when(places.find("Q36036")).thenReturn(Optional.of(LVIV));
        when(places.find("Q465104")).thenReturn(Optional.of(BRODY));
    }

    private static Submission.SubmissionBuilder submission(MusicianKind kind) {
        String p = "submissions/" + SID + "/";
        return Submission.builder().id(SID).status(SubmissionStatus.APPROVED)
                .name("Океан Ельзи").kind(kind).originPlaceId("Q36036")
                .summary("Гурт грає рок уже тридцять років.")
                .genres(List.of("рок"))
                .links(List.of("https://open.spotify.com/artist/abc", "https://www.instagram.com/okean"))
                .youtubeChannelUrl("https://www.youtube.com/@okean")
                .wikipediaUrl("https://uk.wikipedia.org/wiki/Океан_Ельзи")
                .imageKeys(List.of(p + "0.jpg", p + "0-thumb.jpg", p + "1.jpg", p + "1-thumb.jpg"));
    }

    @Test
    void personWithBirthPlaceGetsSettlementPrecision() {
        Artist a = factory.create(submission(MusicianKind.PERSON).birthPlaceId("Q465104").build());

        assertThat(a.getOrigin()).isEqualTo(new Origin("Q465104", "Броди", Region.LVIV, OriginBasis.BIRTH,
                PinPrecision.SETTLEMENT, BRODY.point()));
        assertThat(a.getPin()).isEqualTo(PinPlacer.pinFor(a.getId(), PlaceRef.of(BRODY), PinPrecision.SETTLEMENT));
    }

    @Test
    void personWithoutBirthPlaceUsesOriginInHisOwnWords() {
        Artist a = factory.create(submission(MusicianKind.PERSON).build());

        assertThat(a.getOrigin()).isEqualTo(new Origin("Q36036", "Львів", Region.LVIV, OriginBasis.ORIGIN,
                PinPrecision.CITY, LVIV.point()));
    }

    @Test
    void bandUsesFormationAndCityPrecision() {
        Artist a = factory.create(submission(MusicianKind.BAND).build());

        assertThat(a.getOrigin()).isEqualTo(new Origin("Q36036", "Львів", Region.LVIV, OriginBasis.FORMATION,
                PinPrecision.CITY, LVIV.point()));
    }

    @Test
    void pinComputedWithFinalArtistId() {
        Artist a = factory.create(submission(MusicianKind.BAND).build());

        assertThat(a.getId()).matches("[0-9a-f]{24}");
        assertThat(a.getPin()).isEqualTo(PinPlacer.pinFor(a.getId(), PlaceRef.of(LVIV), PinPrecision.CITY));
        assertThat(a.getPin()).isNotEqualTo(LVIV.point());
    }

    @Test
    void copiesImagesToArtistPrefix() {
        Artist a = factory.create(submission(MusicianKind.BAND).build());

        String from = "submissions/" + SID + "/";
        String to = "artists/" + a.getId() + "/";
        InOrder order = inOrder(storage);
        order.verify(storage).copy(from + "0.jpg", to + "0.jpg");
        order.verify(storage).copy(from + "0-thumb.jpg", to + "0-thumb.jpg");
        order.verify(storage).copy(from + "1.jpg", to + "1.jpg");
        order.verify(storage).copy(from + "1-thumb.jpg", to + "1-thumb.jpg");
        assertThat(a.getImages()).containsExactly(
                new ImageAsset(to + "0.jpg", null, to + "0-thumb.jpg", null, "Океан Ельзи", null),
                new ImageAsset(to + "1.jpg", null, to + "1-thumb.jpg", null, "Океан Ельзи", null));
    }

    @Test
    void fillsAPublishedArtistFromTheSubmission() {
        Artist a = factory.create(submission(MusicianKind.BAND).build());

        assertThat(a.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(a.getSource()).isEqualTo(ArtistSource.SUBMISSION);
        assertThat(a.getDisciplines()).containsExactly(Discipline.MUSIC);
        assertThat(a.getKind()).isEqualTo(MusicianKind.BAND);
        assertThat(a.getName()).isEqualTo("Океан Ельзи");
        assertThat(a.getSlug()).isEqualTo("okean-elzy");
        assertThat(a.getSummary()).isEqualTo("Гурт грає рок уже тридцять років.");
        assertThat(a.getTextSource()).isNull();
        assertThat(a.getGenres()).containsExactly("рок");
        assertThat(a.getLinks()).containsExactly(
                new Link(LinkPlatform.SPOTIFY, "https://open.spotify.com/artist/abc"),
                new Link(LinkPlatform.INSTAGRAM, "https://www.instagram.com/okean"));
        assertThat(a.getYoutubeChannelUrl()).isEqualTo("https://www.youtube.com/@okean");
        assertThat(a.getSubmissionId()).isEqualTo(SID);
        assertThat(a.getPublishedAt()).isEqualTo(NOW);
        assertThat(a.getVersion()).as("null makes save() an insert").isNull();
    }

    @Test
    void takenSlugGetsANumber() {
        when(artists.existsBySlug("okean-elzy")).thenReturn(true);

        assertThat(factory.create(submission(MusicianKind.BAND).build()).getSlug()).isEqualTo("okean-elzy-2");
    }

    @Test
    void copyFailureRemovesTheCopiesMadeSoFar() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 3) {
                throw SdkClientException.create("S3 unavailable");
            }
            return null;
        }).when(storage).copy(anyString(), anyString());

        assertThatThrownBy(() -> factory.create(submission(MusicianKind.BAND).build()))
                .isInstanceOf(SdkClientException.class);

        verify(storage).delete(matches("artists/[0-9a-f]{24}/0\\.jpg"));
        verify(storage).delete(matches("artists/[0-9a-f]{24}/0-thumb\\.jpg"));
        verify(storage, never()).delete(matches(".*/1\\.jpg"));
    }

    @Test
    void unknownPlaceFailsBeforeAnythingIsCopied() {
        when(places.find("Q36036")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.create(submission(MusicianKind.BAND).build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Q36036");
        verify(storage, never()).copy(anyString(), anyString());
    }

    @Test
    void discardDeletesTheCopiedPhotos() {
        Artist a = factory.create(submission(MusicianKind.BAND).build());

        factory.discard(a);

        String to = "artists/" + a.getId() + "/";
        verify(storage).delete(to + "0.jpg");
        verify(storage).delete(to + "0-thumb.jpg");
        verify(storage).delete(to + "1.jpg");
        verify(storage).delete(to + "1-thumb.jpg");
    }
}
