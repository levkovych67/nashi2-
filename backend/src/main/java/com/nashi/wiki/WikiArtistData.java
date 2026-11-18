package com.nashi.wiki;

import java.util.List;
import java.util.Set;

/**
 * Everything the import stores about one artist.
 *
 * @param tagline       the Wikidata description in Ukrainian; null when there is none
 * @param place         null when no place in Ukraine is known; such an artist is saved but not shown on the map
 * @param biography     the intro of the uk.wikipedia article, cleaned by {@link MediaWikiParser#cleanIntro}, not cut
 * @param tags          genres in English, lower case
 * @param style         the first genre in Ukrainian
 * @param youtubeChannelUrl the YouTube channel of Wikidata P2397 ({@code https://www.youtube.com/channel/UC…}); a
 *                          YouTube link MusicBrainz adds to {@code socialLinks} never becomes the channel
 * @param imageUrl      the portrait as a Commons thumbnail about 400 px wide
 * @param imageFullUrl  the same portrait about 800 px wide
 * @param categories    kinds of artist, the main one first
 * @param birthDate     people only, ISO cut to its precision ("1814-03-09", "1814-03", "1814")
 * @param birthPlace    people only: the label of the first place of birth, in Ukraine or not
 * @param locationBasis what {@code place} stands for; null without a place
 * @param genres        Ukrainian (else English) labels of genres, then movements; at most 5
 * @param works         notable works, attached after assembly with {@link #withWorks}
 */
public record WikiArtistData(
        String qid,
        String name,
        String tagline,
        String wikiTitle,
        boolean ensemble,
        String biography,
        String style,
        Set<String> tags,
        Integer foundationYear,
        Integer disbandYear,
        WikiPlace place,
        String imageFile,
        List<WikiLink> socialLinks,
        String youtubeChannelUrl,
        String imageUrl,
        String imageFullUrl,
        WikiImageCredit imageCredit,
        List<WikiCategory> categories,
        String birthDate,
        String deathDate,
        String birthPlace,
        LocationBasis locationBasis,
        List<String> genres,
        List<WikiWork> works) {

    public WikiArtistData withMedia(String biography, String imageUrl, String imageFullUrl,
                                    WikiImageCredit imageCredit) {
        return new WikiArtistData(qid, name, tagline, wikiTitle, ensemble, biography, style, tags, foundationYear, disbandYear,
                place, imageFile, socialLinks, youtubeChannelUrl, imageUrl, imageFullUrl, imageCredit, categories,
                birthDate, deathDate, birthPlace, locationBasis, genres, works);
    }

    public WikiArtistData withWorks(List<WikiWork> works) {
        return new WikiArtistData(qid, name, tagline, wikiTitle, ensemble, biography, style, tags, foundationYear, disbandYear,
                place, imageFile, socialLinks, youtubeChannelUrl, imageUrl, imageFullUrl, imageCredit, categories,
                birthDate, deathDate, birthPlace, locationBasis, genres, List.copyOf(works));
    }

    public WikiArtistData withSocialLinks(List<WikiLink> socialLinks) {
        return new WikiArtistData(qid, name, tagline, wikiTitle, ensemble, biography, style, tags, foundationYear, disbandYear,
                place, imageFile, socialLinks, youtubeChannelUrl, imageUrl, imageFullUrl, imageCredit, categories,
                birthDate, deathDate, birthPlace, locationBasis, genres, works);
    }
}
