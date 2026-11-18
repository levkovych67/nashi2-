package com.nashi.submission;

import com.nashi.api.FieldViolation;
import com.nashi.artist.MusicianKind;
import com.nashi.config.NashiProperties;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.nashi.submission.SubmissionRequests.valid;
import static com.nashi.submission.SubmissionRequests.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionValidatorTest {

    private SubmissionValidator validator;

    @BeforeEach
    void setUp() {
        PlaceStore places = mock(PlaceStore.class);
        when(places.find(anyString())).thenReturn(Optional.empty());
        for (String id : new String[] {"Q36036", "Q465104"}) {
            when(places.find(id)).thenReturn(Optional.of(
                    new Place(id, "Місто", "місто", PlaceType.CITY, Region.LVIV, null, null, null)));
        }
        var props = new NashiProperties(null, null, null, null, new NashiProperties.SubmissionProps(5, 10, 30, 1000),
                null, null);
        validator = new SubmissionValidator(places, Validation.buildDefaultValidatorFactory().getValidator(), props);
    }

    private static List<MultipartFile> photos(int n) {
        return IntStream.range(0, n).<MultipartFile>mapToObj(
                i -> new MockMultipartFile("images", "p" + i + ".jpg", "image/jpeg", new byte[] {1, 2, 3})).toList();
    }

    private List<FieldViolation> check(SubmissionRequest r) {
        return validator.validate(r, photos(1));
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(check(valid())).isEmpty();
    }

    @Test
    void summaryShorterThan30() {
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, "Коротко", valid().links(), null, null, true);
        assertThat(check(r)).containsExactly(new FieldViolation("summary", "Мінімум 30 символів"));
    }

    @Test
    void needsMusicOrVideoLink() {
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(),
                List.of("https://instagram.com/band"), null, null, true);
        assertThat(check(r)).extracting(FieldViolation::field).containsExactly("links");
    }

    @Test
    void youtubeChannelCountsAsVideoLink() {
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(),
                List.of("https://instagram.com/band"), "https://www.youtube.com/@band", null, true);
        assertThat(check(r)).isEmpty();
    }

    @Test
    void youtubeChannelMustBeYoutube() {
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(), valid().links(),
                "https://vimeo.com/band", null, true);
        assertThat(check(r)).extracting(FieldViolation::field).containsExactly("youtubeChannelUrl");
    }

    @Test
    void birthPlaceOnlyForPerson() {
        var band = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", "Q465104", valid().summary(), valid().links(),
                null, null, true);
        assertThat(check(band)).extracting(FieldViolation::field).containsExactly("birthPlaceId");
        var person = with(valid(), "Особа", MusicianKind.PERSON, "Q36036", "Q465104", valid().summary(),
                valid().links(), null, null, true);
        assertThat(check(person)).isEmpty();
    }

    @Test
    void unknownPlaceId() {
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q0", null, valid().summary(), valid().links(), null, null,
                true);
        assertThat(check(r)).extracting(FieldViolation::field).containsExactly("originPlaceId");
        var person = with(valid(), "Особа", MusicianKind.PERSON, "Q36036", "Q0", valid().summary(), valid().links(),
                null, null, true);
        assertThat(check(person)).extracting(FieldViolation::field).containsExactly("birthPlaceId");
    }

    @Test
    void wikipediaMustBeUkOrEn() {
        for (String ok : new String[] {"https://uk.wikipedia.org/wiki/X", "https://en.wikipedia.org/wiki/X"}) {
            assertThat(check(with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(),
                    valid().links(), null, ok, true))).as(ok).isEmpty();
        }
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(), valid().links(), null,
                "https://ru.wikipedia.org/wiki/X", true);
        assertThat(check(r)).extracting(FieldViolation::field).containsExactly("wikipediaUrl");
    }

    @Test
    void rejectsJavascriptUrl() {
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(),
                List.of("javascript:alert(1)"), "https://www.youtube.com/@band", null, true);
        assertThat(check(r)).extracting(FieldViolation::field).containsExactly("links[0]");
        var relative = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(),
                List.of("open.spotify.com/artist/abc"), "https://www.youtube.com/@band", null, true);
        assertThat(check(relative)).extracting(FieldViolation::field).containsExactly("links[0]");
    }

    @Test
    void rejectsSixthImage() {
        assertThat(validator.validate(valid(), photos(6)))
                .extracting(FieldViolation::field).containsExactly("images");
        assertThat(validator.validate(valid(), photos(5))).isEmpty();
    }

    @Test
    void rejectsNoImages() {
        assertThat(validator.validate(valid(), List.of())).extracting(FieldViolation::field).containsExactly("images");
        assertThat(validator.validate(valid(), null)).extracting(FieldViolation::field).containsExactly("images");
    }

    @Test
    void rejectsOversizedImageByIndex() {
        MultipartFile big = mock(MultipartFile.class);
        when(big.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        var images = List.of(photos(1).get(0), big);
        assertThat(validator.validate(valid(), images)).extracting(FieldViolation::field)
                .containsExactly("images[1]");
    }

    @Test
    void consentRequired() {
        for (Boolean consent : new Boolean[] {false, null}) {
            var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(), valid().links(), null,
                    null, consent);
            assertThat(check(r)).as("consent=" + consent).containsExactly(
                    new FieldViolation("consent", "Потрібна згода"));
        }
    }

    @Test
    void collectsAllViolationsAtOnce() {
        var r = new SubmissionRequest("Г", null, "Q0", null, "x", List.of("a"), List.of(), null, null, null, false, "");
        assertThat(validator.validate(r, List.of())).extracting(FieldViolation::field)
                .contains("name", "kind", "originPlaceId", "summary", "genres[0]", "links", "consent", "images");
    }

    @Test
    void capsUrlAndPlaceIdLengths() {
        String longUrl = "https://open.spotify.com/artist/" + "a".repeat(2048);
        String youtube = "https://www.youtube.com/@" + "b".repeat(2048);
        String wiki = "https://uk.wikipedia.org/wiki/" + "c".repeat(2048);
        var r = new SubmissionRequest("Гурт", MusicianKind.PERSON, "Q" + "1".repeat(32), "Q" + "2".repeat(32),
                valid().summary(), null, List.of("https://open.spotify.com/artist/abc", longUrl), youtube, wiki, null,
                true, "");
        List<FieldViolation> violations = check(r);
        assertThat(violations).contains(
                new FieldViolation("links[1]", "Максимум 2048 символів"),
                new FieldViolation("youtubeChannelUrl", "Максимум 2048 символів"),
                new FieldViolation("wikipediaUrl", "Максимум 2048 символів"),
                new FieldViolation("originPlaceId", "Максимум 32 символів"),
                new FieldViolation("birthPlaceId", "Максимум 32 символів"));
        // an over-long id is not looked up as well
        assertThat(violations).extracting(FieldViolation::field).containsOnlyOnce("originPlaceId", "birthPlaceId");
    }

    @Test
    void urlsAtTheCapAreAccepted() {
        String url = "https://open.spotify.com/artist/";
        String atCap = url + "a".repeat(2048 - url.length());
        var r = with(valid(), "Гурт", MusicianKind.BAND, "Q36036", null, valid().summary(), List.of(atCap), null, null,
                true);
        assertThat(check(r)).isEmpty();
    }
}
