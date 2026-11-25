package com.nashi.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WikiLinks {

    private WikiLinks() {
    }

    /** Link to an article of the Ukrainian Wikipedia, or null when there is no title. */
    public static String articleUrl(String title) {
        return pageUrl("https://uk.wikipedia.org/wiki/", title);
    }

    /** Link to a text on the Ukrainian Wikisource, or null when there is no title. An encoded "/" of a subpage works. */
    public static String wikisourceUrl(String title) {
        return pageUrl("https://uk.wikisource.org/wiki/", title);
    }

    /** Link to a category of Wikimedia Commons by its name without "Category:", or null when there is none. */
    public static String commonsCategoryUrl(String category) {
        return pageUrl("https://commons.wikimedia.org/wiki/Category:", category);
    }

    /** MediaWiki title encoding: spaces become "_", the rest is URL-encoded. */
    private static String pageUrl(String base, String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return base + URLEncoder.encode(title.trim().replace(' ', '_'), StandardCharsets.UTF_8);
    }

    /** Link to a Wikidata item, or null when there is no id. */
    public static String wikidataUrl(String qid) {
        if (qid == null || qid.isBlank()) {
            return null;
        }
        return "https://www.wikidata.org/wiki/" + qid.trim();
    }
}
