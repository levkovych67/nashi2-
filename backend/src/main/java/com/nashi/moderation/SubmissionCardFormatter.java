package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.LinkClassifier;
import com.nashi.artist.MusicianKind;
import com.nashi.config.NashiProperties;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.submission.Submission;
import com.nashi.telegram.CallbackData;
import com.nashi.telegram.Html;
import com.nashi.telegram.InlineButton;
import com.nashi.telegram.Verdict;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The moderation card of a submission: Telegram HTML in the plan's template, with the «✅ Так» / «❌ Ні» buttons.
 * Every value from the applicant is escaped and clipped (URLs to {@value #URL_MAX} characters, the summary to
 * {@value #SUMMARY_MAX} at a word boundary). The summary also gives way when the card would exceed {@code maxLength}:
 * without it a card is at most about 3.4K characters, so it always fits Telegram's 4096 with room for the line that
 * records the decision.
 */
@Component
public class SubmissionCardFormatter {

    /** Telegram's limit for the text of a message. */
    public static final int MAX_LENGTH = 4096;
    static final int SUMMARY_MAX = 1500;
    static final int URL_MAX = 150;
    static final int NAME_MAX = 200;
    private static final int PLACE_MAX = 100;
    private static final int GENRES_MAX = 200;
    private static final int CONTACT_MAX = 250;
    private static final String NONE = "—";
    private static final String ELLIPSIS = "…";

    private final PlaceStore places;
    private final ArtistRepository artists;
    private final String siteBaseUrl;

    public SubmissionCardFormatter(PlaceStore places, ArtistRepository artists, NashiProperties properties) {
        this.places = places;
        this.artists = artists;
        this.siteBaseUrl = properties.siteBaseUrl();
    }

    public String card(Submission s) {
        return card(s, MAX_LENGTH);
    }

    /** The card of a stored submission; its places and the possible duplicate are looked up. */
    public String card(Submission s, int maxLength) {
        Place origin = place(s.getOriginPlaceId());
        Place birthPlace = place(s.getBirthPlaceId());
        Artist duplicate = s.getDuplicateOfArtistId() == null ? null
                : artists.findById(s.getDuplicateOfArtistId()).orElse(null);
        return format(s, origin, birthPlace, duplicate, siteBaseUrl, maxLength);
    }

    public static String format(Submission s, @Nullable Place origin, @Nullable Place birthPlace,
                                @Nullable Artist duplicate, String siteBaseUrl) {
        return format(s, origin, birthPlace, duplicate, siteBaseUrl, MAX_LENGTH);
    }

    public static String format(Submission s, @Nullable Place origin, @Nullable Place birthPlace,
                                @Nullable Artist duplicate, String siteBaseUrl, int maxLength) {
        String head = "🆕 <b>Нова заявка</b> · " + (s.getKind() == MusicianKind.BAND ? "Гурт" : "Музикант") + "\n"
                + "<b>" + clip(s.getName(), NAME_MAX) + "</b>\n"
                + "📍 Походження: " + origin(s, origin, birthPlace) + "\n"
                + "🎵 Жанри: " + genres(s.getGenres()) + "\n"
                + "📝 ";
        String tail = "\n" + links(s.getLinks()) + "\n"
                + "▶️ YouTube-канал: " + url(s.getYoutubeChannelUrl())
                + " · 📖 Вікіпедія: " + url(s.getWikipediaUrl()) + "\n"
                + "👤 Контакт: " + (isBlank(s.getContact()) ? "не вказано" : clip(s.getContact(), CONTACT_MAX))
                + (duplicate == null ? "" : "\n⚠️ Можливий дублікат: <a href=\""
                        + Html.escape(siteBaseUrl + "/m/" + duplicate.getSlug()) + "\">"
                        + clip(duplicate.getName(), NAME_MAX) + "</a>");
        return head + summary(s.getSummary(), maxLength - head.length() - tail.length()) + tail;
    }

    /** One row: «✅ Так» and «❌ Ні» for this submission. */
    public static List<List<InlineButton>> buttons(String submissionId) {
        return List.of(List.of(
                new InlineButton("✅ Так", new CallbackData(submissionId, Verdict.YES).encode()),
                new InlineButton("❌ Ні", new CallbackData(submissionId, Verdict.NO).encode())));
    }

    /**
     * {@code text} escaped for Telegram HTML and at most {@code max} characters long; a longer text is cut (never
     * inside an entity or a surrogate pair) and ends with «…».
     */
    static String clip(@Nullable String text, int max) {
        String escaped = Html.escape(text);
        if (escaped.length() <= max) {
            return escaped;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String piece = Html.escape(Character.toString(codePoint));
            if (out.length() + piece.length() > max - ELLIPSIS.length()) {
                break;
            }
            out.append(piece);
            i += Character.charCount(codePoint);
        }
        return out.append(ELLIPSIS).toString();
    }

    /** At most {@code max} characters: cut at the last whitespace that fits (or hard, for one long word) plus «…». */
    static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        if (max < 1) {
            return "";
        }
        int end = max - ELLIPSIS.length();
        int cut = end;
        while (cut > 0 && !Character.isWhitespace(text.charAt(cut))) {
            cut--;
        }
        if (cut > 0) {
            end = cut;
        } else if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }

    /** The summary, escaped, cut to {@value #SUMMARY_MAX} characters and further until it fits {@code room}. */
    private static String summary(@Nullable String text, int room) {
        if (text == null) {
            return "";
        }
        String shown = truncate(text, SUMMARY_MAX);
        String escaped = Html.escape(shown);
        while (escaped.length() > room && !shown.isEmpty()) {
            // shrink in proportion (escaping can make a text up to six times longer), always by at least one
            long proportional = (long) shown.length() * Math.max(room, 0) / escaped.length();
            int limit = (int) Math.min(shown.length() - 1L, proportional);
            shown = truncate(text, limit);
            escaped = Html.escape(shown);
        }
        return escaped;
    }

    private static String origin(Submission s, @Nullable Place origin, @Nullable Place birthPlace) {
        String text;
        if (origin == null) {
            text = isBlank(s.getOriginPlaceId()) ? NONE : clip(s.getOriginPlaceId(), PLACE_MAX);
        } else {
            text = clip(origin.name(), PLACE_MAX) + (origin.region() == null ? "" : ", " + origin.region().uaName());
        }
        return birthPlace == null ? text : text + " · народження: " + clip(birthPlace.name(), PLACE_MAX);
    }

    private static String genres(@Nullable List<String> genres) {
        return genres == null || genres.isEmpty() ? NONE : clip(String.join(", ", genres), GENRES_MAX);
    }

    private static String links(@Nullable List<String> links) {
        if (links == null || links.isEmpty()) {
            return "🔗 " + NONE;
        }
        return links.stream().map(link -> "🔗 " + platform(link) + ": " + url(link)).collect(Collectors.joining("\n"));
    }

    private static String url(@Nullable String url) {
        return isBlank(url) ? NONE : clip(url, URL_MAX);
    }

    private static String platform(String url) {
        return switch (LinkClassifier.classify(url)) {
            case SPOTIFY -> "Spotify";
            case APPLE_MUSIC -> "Apple Music";
            case DEEZER -> "Deezer";
            case YOUTUBE_MUSIC -> "YouTube Music";
            case SOUNDCLOUD -> "SoundCloud";
            case BANDCAMP -> "Bandcamp";
            case YOUTUBE -> "YouTube";
            case WIKIPEDIA -> "Вікіпедія";
            case WEBSITE -> "Сайт";
            case INSTAGRAM -> "Instagram";
            case FACEBOOK -> "Facebook";
            case TIKTOK -> "TikTok";
            case TELEGRAM -> "Telegram";
            case X -> "X";
            case OTHER -> "Посилання";
        };
    }

    private @Nullable Place place(@Nullable String placeId) {
        return placeId == null ? null : places.find(placeId).orElse(null);
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isBlank();
    }
}
