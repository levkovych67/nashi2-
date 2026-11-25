package com.nashi.util;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistCategory;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facts about an artist that the API shows, with fallbacks for artists added through the form, which store no
 * categories or genres.
 */
public final class ArtistFacts {

    /** The year at the start of "1814-03-09", "1975" or Wikidata's "+1814-03-09T00:00:00Z". */
    private static final Pattern LEADING_YEAR = Pattern.compile("^\\+?(\\d{1,4})(?!\\d)");

    private ArtistFacts() {
    }

    /** Stored categories; otherwise BAND when the artist has members, else MUSICIAN. */
    public static List<ArtistCategory> categoriesOf(Artist artist) {
        List<ArtistCategory> stored = artist.getCategories() == null ? List.of()
                : artist.getCategories().stream().filter(Objects::nonNull).toList();
        if (!stored.isEmpty()) {
            return stored;
        }
        boolean hasMembers = artist.getMembers() != null && !artist.getMembers().isEmpty();
        return List.of(hasMembers ? ArtistCategory.BAND : ArtistCategory.MUSICIAN);
    }

    /** Stored genres; otherwise the style when there is one, else none. */
    public static List<String> genresOf(Artist artist) {
        List<String> stored = artist.getGenres() == null ? List.of()
                : artist.getGenres().stream().filter(g -> g != null && !g.isBlank()).toList();
        if (!stored.isEmpty()) {
            return stored;
        }
        String style = artist.getStyle();
        return style == null || style.isBlank() ? List.of() : List.of(style.trim());
    }

    /** The birth year of a person; the foundation year of a band, or of a person whose birth date is unknown. */
    public static Integer yearOf(Artist artist) {
        if (!categoriesOf(artist).contains(ArtistCategory.BAND)) {
            Integer birthYear = leadingYear(artist.getBirthDate());
            if (birthYear != null) {
                return birthYear;
            }
        }
        return artist.getFoundationYear();
    }

    private static Integer leadingYear(String date) {
        if (date == null) {
            return null;
        }
        Matcher matcher = LEADING_YEAR.matcher(date.trim());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
