package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.MusicianKind;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import com.nashi.telegram.InlineButton;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionCardFormatterTest {

    private static final String SITE = "https://nashi.example";
    private static final String ID = "6560a1b2c3d4e5f6a7b8c9d0";
    private static final Place LVIV = new Place("Q36036", "Львів", "львів", PlaceType.CITY, Region.LVIV,
            new GeoJsonPoint(24.0315921, 49.841952), 182.0, 717_273L);
    private static final Place BRODY = new Place("Q465104", "Броди", "броди", PlaceType.CITY, Region.LVIV,
            new GeoJsonPoint(25.154166666, 50.078055555), 8.67, 23_454L);

    private static Submission.SubmissionBuilder submission() {
        return Submission.builder().id(ID).status(SubmissionStatus.PENDING)
                .name("Океан Ельзи").kind(MusicianKind.BAND).originPlaceId("Q36036")
                .summary("Гурт грає рок уже тридцять років і збирає стадіони.")
                .genres(List.of("рок", "поп-рок"))
                .links(List.of("https://open.spotify.com/artist/abc", "https://www.instagram.com/okean"))
                .youtubeChannelUrl("https://www.youtube.com/@okean")
                .wikipediaUrl("https://uk.wikipedia.org/wiki/Океан_Ельзи")
                .contact("telegram @nick");
    }

    @Test
    void rendersTemplate() {
        String card = SubmissionCardFormatter.format(submission().build(), LVIV, null, null, SITE);

        assertThat(card).isEqualTo("""
                🆕 <b>Нова заявка</b> · Гурт
                <b>Океан Ельзи</b>
                📍 Походження: Львів, Львівська область
                🎵 Жанри: рок, поп-рок
                📝 Гурт грає рок уже тридцять років і збирає стадіони.
                🔗 Spotify: https://open.spotify.com/artist/abc
                🔗 Instagram: https://www.instagram.com/okean
                ▶️ YouTube-канал: https://www.youtube.com/@okean · 📖 Вікіпедія: https://uk.wikipedia.org/wiki/Океан_Ельзи
                👤 Контакт: telegram @nick""");
    }

    @Test
    void personShowsBirthPlaceAndPlaceholdersForMissingFields() {
        Submission s = submission().kind(MusicianKind.PERSON).birthPlaceId("Q465104").genres(List.of())
                .youtubeChannelUrl(null).wikipediaUrl(null).contact(null).build();

        String card = SubmissionCardFormatter.format(s, LVIV, BRODY, null, SITE);

        assertThat(card).startsWith("🆕 <b>Нова заявка</b> · Музикант\n")
                .contains("\n📍 Походження: Львів, Львівська область · народження: Броди\n")
                .contains("\n🎵 Жанри: —\n")
                .contains("\n▶️ YouTube-канал: — · 📖 Вікіпедія: —\n")
                .endsWith("\n👤 Контакт: не вказано");
    }

    @Test
    void escapesUserHtml() {
        Submission s = submission().name("<script>alert(1)</script>").summary("Рок & ролл <b>жирно</b> \"цитата\"")
                .genres(List.of("<i>джаз</i>")).links(List.of("https://example.com/?a=1&b=<2>"))
                .contact("<a href=\"x\">я</a>").build();

        String card = SubmissionCardFormatter.format(s, LVIV, null, null, SITE);

        assertThat(card).contains("<b>&lt;script&gt;alert(1)&lt;/script&gt;</b>")
                .contains("📝 Рок &amp; ролл &lt;b&gt;жирно&lt;/b&gt; &quot;цитата&quot;")
                .contains("🎵 Жанри: &lt;i&gt;джаз&lt;/i&gt;")
                .contains("🔗 Посилання: https://example.com/?a=1&amp;b=&lt;2&gt;")
                .contains("👤 Контакт: &lt;a href=&quot;x&quot;&gt;я&lt;/a&gt;")
                .doesNotContain("<script>").doesNotContain("<i>").doesNotContain("<a href=\"x\">");
    }

    @Test
    void includesDuplicateWarningLink() {
        Artist duplicate = Artist.builder().id("a1").slug("okean-elzy").name("Океан <Ельзи>").build();

        String card = SubmissionCardFormatter.format(submission().build(), LVIV, null, duplicate, SITE);

        assertThat(card).endsWith("\n👤 Контакт: telegram @nick\n"
                + "⚠️ Можливий дублікат: <a href=\"https://nashi.example/m/okean-elzy\">Океан &lt;Ельзи&gt;</a>");
    }

    @Test
    void fitsTelegramLimit() {
        String summary = "Слово ".repeat(166) + "кінець"; // 1002 characters
        List<String> links = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            links.add("https://open.spotify.com/artist/" + "x".repeat(40) + i);
        }
        Submission s = submission().summary(summary).links(links).build();

        String card = SubmissionCardFormatter.format(s, LVIV, null, null, SITE);

        assertThat(card.length()).isLessThanOrEqualTo(SubmissionCardFormatter.MAX_LENGTH);
        assertThat(card).contains("📝 " + summary + "\n");
        links.forEach(link -> assertThat(card).contains("🔗 Spotify: " + link + "\n"));
    }

    @Test
    void hostileInputStillFitsTheLimitAndLeavesRoomForTheDecisionLine() {
        List<String> links = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            links.add("https://example.com/?" + "q=&".repeat(1000));
        }
        Submission s = submission().name("\"".repeat(100)).summary("&".repeat(1000))
                .genres(List.of("<".repeat(40), "<".repeat(40), "<".repeat(40), "<".repeat(40), "<".repeat(40)))
                .links(links).youtubeChannelUrl("https://www.youtube.com/@" + "&".repeat(2000))
                .wikipediaUrl("https://uk.wikipedia.org/wiki/" + "&".repeat(2000)).contact("\"".repeat(200))
                .build();
        Artist duplicate = Artist.builder().id("a1").slug("a".repeat(80)).name("&".repeat(500)).build();

        String card = SubmissionCardFormatter.format(s, LVIV, BRODY, duplicate, SITE);
        String shorter = SubmissionCardFormatter.format(s, LVIV, BRODY, duplicate, SITE, 3600);

        assertThat(card.length()).isLessThanOrEqualTo(SubmissionCardFormatter.MAX_LENGTH);
        assertThat(shorter.length()).isLessThanOrEqualTo(3600);
        assertThat(shorter).contains("📝 &amp;").contains("⚠️ Можливий дублікат");
        // a cut never leaves half an entity behind
        assertThat(card).doesNotContainPattern("&(?!amp;|lt;|gt;|quot;)");
    }

    @Test
    void truncatesLongSummaryAtWordBoundary() {
        String summary = "слово ".repeat(400); // 2400 characters, more than the validator allows

        String card = SubmissionCardFormatter.format(submission().summary(summary).build(), LVIV, null, null, SITE);

        String shown = card.substring(card.indexOf("📝 ") + 3, card.indexOf("\n🔗"));
        assertThat(shown).endsWith("слово…").hasSizeLessThanOrEqualTo(1500);
        assertThat(shown.substring(0, shown.length() - 1)).isEqualTo(summary.substring(0, shown.length() - 1));
    }

    @Test
    void buttonsCarryTheVerdictForThisSubmission() {
        assertThat(SubmissionCardFormatter.buttons(ID)).containsExactly(List.of(
                new InlineButton("✅ Так", "sub:" + ID + ":y"),
                new InlineButton("❌ Ні", "sub:" + ID + ":n")));
    }
}
