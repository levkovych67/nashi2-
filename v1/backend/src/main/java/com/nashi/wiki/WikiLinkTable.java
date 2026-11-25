package com.nashi.wiki;

import com.nashi.domain.SocialLink;
import com.nashi.domain.SocialLink.Platform;
import com.nashi.util.WikiLinks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The links of an imported artist, in the order of the spec's link table. Templates are the formatter URLs (P1630) of
 * the properties as checked on 2026-11-08, with three exceptions that keep the spec's template:
 * <ul>
 *   <li>P3789 Telegram: {@code https://t.me/%s}; its P1630 is the channel preview {@code https://t.me/s/$1}, which
 *   breaks for personal accounts;</li>
 *   <li>P434 MusicBrainz: its P1630 is not a URL;</li>
 *   <li>P648 Open Library: its P1630 is the works path, which only redirects for an author id.</li>
 * </ul>
 * Values are put in as stated; Commons and Wikisource titles are encoded as MediaWiki titles.
 */
public final class WikiLinkTable {

    private record Row(Platform platform, Function<WikiEntity, String> url) {
    }

    private static final List<Row> TABLE = List.of(
            id(Platform.WEBSITE, "P856", "%s"),
            id(Platform.INSTAGRAM, "P2003", "https://www.instagram.com/%s/"),
            id(Platform.FACEBOOK, "P2013", "https://www.facebook.com/%s"),
            id(Platform.X, "P2002", "https://x.com/%s"),
            id(Platform.TIKTOK, "P7085", "https://www.tiktok.com/@%s"),
            id(Platform.TELEGRAM, "P3789", "https://t.me/%s"),
            id(Platform.YOUTUBE, "P2397", "https://www.youtube.com/channel/%s"),
            id(Platform.SPOTIFY, "P1902", "https://open.spotify.com/artist/%s"),
            id(Platform.APPLE_MUSIC, "P2850", "https://music.apple.com/artist/%s"),
            id(Platform.DEEZER, "P2722", "https://www.deezer.com/artist/%s"),
            id(Platform.SOUNDCLOUD, "P3040", "https://soundcloud.com/%s"),
            id(Platform.BANDCAMP, "P3283", "https://%s.bandcamp.com"),
            id(Platform.DISCOGS, "P1953", "https://www.discogs.com/artist/%s"),
            id(Platform.MUSICBRAINZ, "P434", "https://musicbrainz.org/artist/%s"),
            id(Platform.LASTFM, "P3192", "https://www.last.fm/music/%s"),
            new Row(Platform.COMMONS, e -> WikiLinks.commonsCategoryUrl(e.commonsCategory())),
            new Row(Platform.WIKISOURCE, e -> WikiLinks.wikisourceUrl(e.wikisourceTitle())),
            id(Platform.GOODREADS, "P2963", "https://www.goodreads.com/author/show/%s"),
            id(Platform.OPEN_LIBRARY, "P648", "https://openlibrary.org/authors/%s"),
            id(Platform.ARTSY, "P2042", "https://www.artsy.net/artist/%s"),
            id(Platform.WIKIART, "P6002", "https://www.wikiart.org/en/%s"),
            id(Platform.ESU, "P4613", "https://esu.com.ua/search_articles.php?id=%s"),
            id(Platform.CHTYVO, "P13437", "https://chtyvo.org.ua/authors/%s"),
            new Row(Platform.WIKI, e -> WikiLinks.articleUrl(e.ukTitle())),
            new Row(Platform.WIKIDATA, e -> WikiLinks.wikidataUrl(e.qid())));

    private WikiLinkTable() {
    }

    /** Links from the item's own statements only; WIKIDATA is always there, WIKI when the item has a uk article. */
    public static List<SocialLink> links(WikiEntity e) {
        List<SocialLink> links = new ArrayList<>();
        for (Row row : TABLE) {
            String url = row.url().apply(e);
            if (url != null && !url.isBlank()) {
                links.add(SocialLink.builder().platform(row.platform()).url(url).build());
            }
        }
        return links;
    }

    private static Row id(Platform platform, String property, String template) {
        return new Row(platform, e -> {
            String value = e.socialIds() == null ? null : e.socialIds().get(property);
            return value == null || value.isBlank() ? null : String.format(template, value.trim());
        });
    }
}
