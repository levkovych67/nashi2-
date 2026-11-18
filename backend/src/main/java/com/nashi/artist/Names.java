package com.nashi.artist;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalization of names for search and duplicate checks ({@code nameNormalized} in {@code artists} and
 * {@code places}; {@code docs/02-contracts.md} 2.5, 2.9, section 3): NFC, lower case ({@link Locale#ROOT}), without
 * quotes {@code «»"„“”}, apostrophes {@code '’ʼ‘} and the backtick, without the combining stress mark U+0301, runs of
 * whitespace collapsed to one space, trimmed. Unlike V1's {@code NameNormalizer}, apostrophes are removed, so
 * «Кам’янець», «Кам'янець» and «Камянець» are the same name.
 */
public final class Names {

    private static final Pattern DROPPED = Pattern.compile("[«»\"„“”'’ʼ‘`\\u0301]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Names() {
    }

    /** @return the normalized form; {@code ""} for {@code null} */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        s = DROPPED.matcher(s).replaceAll("");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }
}
