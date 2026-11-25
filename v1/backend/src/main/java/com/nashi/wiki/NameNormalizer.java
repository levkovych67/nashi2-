package com.nashi.wiki;

import java.util.Locale;

/** Makes names comparable: case, quotes, apostrophe variants and spaces do not matter. */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[«»\"„“”]", "")
                .replaceAll("[’ʼ‘`]", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
