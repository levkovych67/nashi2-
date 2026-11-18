package com.nashi.wiki;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The texts of an imported musician from the intro of the uk.wikipedia article: {@link #summary} (the first sentences,
 * at most {@value #SUMMARY_MAX} characters with the closing «…») and {@link #description} (the whole intro, at most
 * {@value #DESCRIPTION_MAX}). Both are cleaned the same way:
 * <ol>
 *   <li>{@link MediaWikiParser#cleanIntro}: stress marks, the hidden copy of a date template glued to the visible date
 *   ({@code 1978(19780106)}, {@code 1975(1975-05-14)}) anywhere in the text, spaces before commas;</li>
 *   <li>in the first sentence, every balanced pair of parentheses (nested ones included) whose content has a year or
 *   one of {@link #MARKERS}: the birth date, the Latin spelling, the real name;</li>
 *   <li>spaces before {@code , . ; : )} and runs of spaces.</li>
 * </ol>
 */
public final class SummaryCleaner {

    static final int SUMMARY_MAX = 300;
    static final int DESCRIPTION_MAX = 2000;

    private static final Pattern YEAR = Pattern.compile("\\b(1[0-9]{3}|20[0-9]{2})\\b");
    private static final List<String> MARKERS =
            List.of("нар.", "лат.", "англ.", "рос.", "пол.", "справжнє", "псевдонім");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("[ \\t\\u00A0]+([,.;:)])");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]{2,}");

    private SummaryCleaner() {
    }

    /** The cleaned intro cut at the last sentence that fits in {@value #SUMMARY_MAX}; null when there is none. */
    public static String summary(String intro) {
        return fit(clean(intro), SUMMARY_MAX);
    }

    /** The cleaned intro cut at the last sentence that fits in {@value #DESCRIPTION_MAX}; null when there is none. */
    public static String description(String intro) {
        return fit(clean(intro), DESCRIPTION_MAX);
    }

    static String clean(String intro) {
        if (intro == null || intro.isBlank()) {
            return null;
        }
        String text = withoutBiographicParentheses(MediaWikiParser.cleanIntro(intro));
        text = SPACE_BEFORE_PUNCTUATION.matcher(text).replaceAll("$1");
        text = SPACES.matcher(text).replaceAll(" ").trim();
        return text.isEmpty() ? null : text;
    }

    /** {@link MediaWikiParser#truncate} adds «…» after the cut, so it gets one character less than {@code max}. */
    private static String fit(String text, int max) {
        return text == null || text.length() <= max ? text : MediaWikiParser.truncate(text, max - 1);
    }

    /**
     * The text without the parentheses of the first sentence that hold a year or a marker. The first sentence ends at
     * the first {@code . ! ?} outside parentheses that is followed by a space or the end; an unclosed parenthesis is
     * kept as it is.
     */
    static String withoutBiographicParentheses(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int depth = 0;
        int open = -1;
        int i = 0;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                if (depth++ == 0) {
                    open = i;
                }
            } else if (c == ')' && depth > 0) {
                if (--depth == 0) {
                    if (!biographic(text.substring(open + 1, i))) {
                        out.append(text, open, i + 1);
                    }
                    open = -1;
                }
            } else if (depth == 0) {
                out.append(c);
                if (endsSentence(text, i)) {
                    i++;
                    break;
                }
            }
        }
        if (open >= 0) {
            out.append(text, open, i);
        }
        return out.append(text, i, text.length()).toString();
    }

    private static boolean endsSentence(String text, int i) {
        char c = text.charAt(i);
        return (c == '.' || c == '!' || c == '?') && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1)));
    }

    private static boolean biographic(String inside) {
        if (YEAR.matcher(inside).find()) {
            return true;
        }
        String lower = inside.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(lower::contains);
    }
}
