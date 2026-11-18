package com.nashi.artist;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Latin slug of a name, transliterated by the Ukrainian Cabinet resolution No. 55 of 2010: {@code є ї й ю я} are
 * {@code ye yi y yu ya} at the start of a word and {@code ie i i iu ia} elsewhere, {@code зг} is {@code zgh}, the soft
 * sign and apostrophes are dropped. Every other character outside {@code [a-z0-9]} becomes {@code -}; runs of
 * {@code -} collapse, the ends are trimmed, the result is at most 80 characters (cut at a word boundary) and
 * {@code "artist"} when nothing is left.
 */
public final class SlugGenerator {

    static final int MAX_LENGTH = 80;
    static final String FALLBACK = "artist";

    /** Letters with one spelling everywhere. */
    private static final Map<Character, String> PLAIN = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "h"),
            Map.entry('ґ', "g"), Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "y"), Map.entry('і', "i"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ь', ""));

    /** Letters spelled one way at the start of a word and another way inside it. */
    private static final Map<Character, String[]> POSITIONAL = Map.of(
            'є', new String[]{"ye", "ie"},
            'ї', new String[]{"yi", "i"},
            'й', new String[]{"y", "i"},
            'ю', new String[]{"yu", "iu"},
            'я', new String[]{"ya", "ia"});

    private SlugGenerator() {
    }

    public static String slugify(String name) {
        String s = name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        // Stress marks and apostrophes vanish before anything else, so they do not split a word
        s = s.replaceAll("[\\u0301'’ʼ‘`]", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean wordStart = i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1));
            if (c == 'з' && i + 1 < s.length() && s.charAt(i + 1) == 'г') {
                out.append("zgh");
                i++;
            } else if (PLAIN.containsKey(c)) {
                out.append(PLAIN.get(c));
            } else if (POSITIONAL.containsKey(c)) {
                out.append(POSITIONAL.get(c)[wordStart ? 0 : 1]);
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        String slug = trimDashes(out.toString().replaceAll("-{2,}", "-"));
        if (slug.length() > MAX_LENGTH) {
            int cut = slug.charAt(MAX_LENGTH) == '-' ? MAX_LENGTH : slug.lastIndexOf('-', MAX_LENGTH);
            slug = trimDashes(slug.substring(0, cut > 0 ? cut : MAX_LENGTH));
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }

    private static String trimDashes(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }
}
