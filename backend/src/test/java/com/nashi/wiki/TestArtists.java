package com.nashi.wiki;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hand-made {@link WikiArtistData} for tests of what comes after the collector (mapper, import, enrichment). Set only
 * what a test cares about, so new record components do not break callers.
 */
public final class TestArtists {

    private TestArtists() {
    }

    /** A musician with a uk article of the same name and nothing else. */
    public static Builder musician(String qid, String name) {
        return new Builder(qid, name);
    }

    /** A band (an ensemble) with a uk article of the same name and nothing else. */
    public static Builder band(String qid, String name) {
        return new Builder(qid, name).ensemble(true);
    }

    public static final class Builder {
        private final String qid;
        private final String name;
        private String tagline;
        private String wikiTitle;
        private boolean ensemble;
        private String biography;
        private Integer foundationYear;
        private Integer disbandYear;
        private WikiPlace place;
        private List<WikiLink> socialLinks = new ArrayList<>();
        private String youtubeChannelUrl;
        private String imageUrl;
        private String imageFullUrl;
        private WikiImageCredit imageCredit;
        private String birthDate;
        private String deathDate;
        private LocationBasis locationBasis;
        private List<String> genres = List.of();
        private List<WikiWork> works = List.of();

        private Builder(String qid, String name) {
            this.qid = qid;
            this.name = name;
            this.wikiTitle = name;
        }

        public Builder tagline(String v) { this.tagline = v; return this; }
        public Builder wikiTitle(String v) { this.wikiTitle = v; return this; }
        public Builder ensemble(boolean v) { this.ensemble = v; return this; }
        public Builder biography(String v) { this.biography = v; return this; }
        public Builder years(Integer from, Integer to) { this.foundationYear = from; this.disbandYear = to; return this; }
        public Builder birthDate(String v) { this.birthDate = v; return this; }
        public Builder deathDate(String v) { this.deathDate = v; return this; }
        public Builder genres(List<String> v) { this.genres = v; return this; }
        public Builder works(List<WikiWork> v) { this.works = v; return this; }
        public Builder youtubeChannelUrl(String v) { this.youtubeChannelUrl = v; return this; }

        /** The place and what it stands for. */
        public Builder place(WikiPlace place, LocationBasis basis) {
            this.place = place;
            this.locationBasis = basis;
            return this;
        }

        public Builder link(WikiLink.Platform platform, String url) {
            socialLinks.add(WikiLink.builder().platform(platform).url(url).build());
            return this;
        }

        /** A Commons portrait: the 400 px and 800 px thumbnails of {@code file} and its credit. */
        public Builder photo(String file, String author, String license) {
            this.imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/" + file + "/400px-" + file;
            this.imageFullUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/" + file + "/800px-" + file;
            this.imageCredit = WikiImageCredit.builder().author(author).license(license)
                    .filePageUrl("https://commons.wikimedia.org/wiki/File:" + file).build();
            return this;
        }

        public WikiArtistData build() {
            return new WikiArtistData(qid, name, tagline, wikiTitle, ensemble, biography, null, Set.of(),
                    foundationYear, disbandYear, place, null, List.copyOf(socialLinks), youtubeChannelUrl, imageUrl,
                    imageFullUrl, imageCredit, List.of(ensemble ? WikiCategory.BAND : WikiCategory.MUSICIAN), birthDate,
                    deathDate, null, locationBasis, genres, works);
        }
    }
}
