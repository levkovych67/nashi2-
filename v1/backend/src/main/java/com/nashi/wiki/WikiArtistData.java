package com.nashi.wiki;

import com.nashi.domain.ArtistWork;
import com.nashi.domain.ImageCredit;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.LocationBasis;

import java.util.List;
import java.util.Set;

/**
 * Everything the import stores about one artist.
 *
 * @param place         null when no place in Ukraine is known; such an artist is saved but not shown on the map
 * @param tags          genres in English, lower case
 * @param style         the first genre in Ukrainian
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
        String wikiTitle,
        boolean ensemble,
        String biography,
        String style,
        Set<String> tags,
        Integer foundationYear,
        Integer disbandYear,
        WikiPlace place,
        String imageFile,
        List<SocialLink> socialLinks,
        String imageUrl,
        ImageCredit imageCredit,
        List<ArtistCategory> categories,
        String birthDate,
        String deathDate,
        String birthPlace,
        LocationBasis locationBasis,
        List<String> genres,
        List<ArtistWork> works) {

    public WikiArtistData withMedia(String biography, String imageUrl, ImageCredit imageCredit) {
        return new WikiArtistData(qid, name, wikiTitle, ensemble, biography, style, tags, foundationYear, disbandYear,
                place, imageFile, socialLinks, imageUrl, imageCredit, categories, birthDate, deathDate, birthPlace,
                locationBasis, genres, works);
    }

    public WikiArtistData withWorks(List<ArtistWork> works) {
        return new WikiArtistData(qid, name, wikiTitle, ensemble, biography, style, tags, foundationYear, disbandYear,
                place, imageFile, socialLinks, imageUrl, imageCredit, categories, birthDate, deathDate, birthPlace,
                locationBasis, genres, List.copyOf(works));
    }

    public WikiArtistData withSocialLinks(List<SocialLink> socialLinks) {
        return new WikiArtistData(qid, name, wikiTitle, ensemble, biography, style, tags, foundationYear, disbandYear,
                place, imageFile, socialLinks, imageUrl, imageCredit, categories, birthDate, deathDate, birthPlace,
                locationBasis, genres, works);
    }
}
