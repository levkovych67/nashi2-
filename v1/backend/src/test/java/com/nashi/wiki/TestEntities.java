package com.nashi.wiki;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small hand-made entities for cases the real fixtures do not cover. */
final class TestEntities {

    private TestEntities() {
    }

    /** Starts an empty entity; set only what a test cares about, so new record components do not break callers. */
    static Builder entity(String qid) {
        return new Builder(qid);
    }

    static WikiEntity place(String qid, String labelUk, Double lat, Double lon, String... partOf) {
        return entity(qid).labelUk(labelUk).lat(lat).lon(lon).partOf(List.of(partOf)).build();
    }

    static WikiEntity person(String qid, String labelUk, Set<String> citizenships, List<String> birthPlaces,
                             List<String> residences, List<String> workPlaces) {
        return entity(qid).labelUk(labelUk).ukTitle(labelUk).instanceOf(Set.of("Q5")).citizenships(citizenships)
                .birthPlaces(birthPlaces).residences(residences).workPlaces(workPlaces).build();
    }

    static WikiEntity genre(String qid, String labelUk, String labelEn) {
        return entity(qid).labelUk(labelUk).labelEn(labelEn).build();
    }

    static final class Builder {
        private final String qid;
        private String labelUk;
        private String labelEn;
        private String ukTitle;
        private Set<String> instanceOf = Set.of();
        private Set<String> citizenships = Set.of();
        private List<String> birthPlaces = List.of();
        private List<String> formationPlaces = List.of();
        private List<String> residences = List.of();
        private List<String> workPlaces = List.of();
        private Integer inception;
        private Integer dissolved;
        private Integer workStart;
        private Integer workEnd;
        private String imageFile;
        private List<String> genres = List.of();
        private Map<String, String> socialIds = Map.of();
        private Double lat;
        private Double lon;
        private List<String> partOf = List.of();
        private List<String> occupations = List.of();
        private WikiDate birthDate;
        private WikiDate deathDate;
        private List<String> movements = List.of();
        private List<String> notableWorks = List.of();
        private String commonsCategory;
        private String wikisourceTitle;
        private List<String> awards = List.of();
        private List<String> countries = List.of();
        private List<String> wholes = List.of();
        private List<String> conferredBy = List.of();

        private Builder(String qid) {
            this.qid = qid;
        }

        Builder labelUk(String v) { this.labelUk = v; return this; }
        Builder labelEn(String v) { this.labelEn = v; return this; }
        Builder ukTitle(String v) { this.ukTitle = v; return this; }
        Builder instanceOf(Set<String> v) { this.instanceOf = v; return this; }
        Builder citizenships(Set<String> v) { this.citizenships = v; return this; }
        Builder birthPlaces(List<String> v) { this.birthPlaces = v; return this; }
        Builder formationPlaces(List<String> v) { this.formationPlaces = v; return this; }
        Builder residences(List<String> v) { this.residences = v; return this; }
        Builder workPlaces(List<String> v) { this.workPlaces = v; return this; }
        Builder inception(Integer v) { this.inception = v; return this; }
        Builder dissolved(Integer v) { this.dissolved = v; return this; }
        Builder workStart(Integer v) { this.workStart = v; return this; }
        Builder workEnd(Integer v) { this.workEnd = v; return this; }
        Builder imageFile(String v) { this.imageFile = v; return this; }
        Builder genres(List<String> v) { this.genres = v; return this; }
        Builder socialIds(Map<String, String> v) { this.socialIds = v; return this; }
        Builder lat(Double v) { this.lat = v; return this; }
        Builder lon(Double v) { this.lon = v; return this; }
        Builder partOf(List<String> v) { this.partOf = v; return this; }
        Builder occupations(List<String> v) { this.occupations = v; return this; }
        Builder birthDate(WikiDate v) { this.birthDate = v; return this; }
        Builder deathDate(WikiDate v) { this.deathDate = v; return this; }
        Builder movements(List<String> v) { this.movements = v; return this; }
        Builder notableWorks(List<String> v) { this.notableWorks = v; return this; }
        Builder commonsCategory(String v) { this.commonsCategory = v; return this; }
        Builder wikisourceTitle(String v) { this.wikisourceTitle = v; return this; }
        Builder awards(List<String> v) { this.awards = v; return this; }
        Builder countries(List<String> v) { this.countries = v; return this; }
        Builder wholes(List<String> v) { this.wholes = v; return this; }
        Builder conferredBy(List<String> v) { this.conferredBy = v; return this; }

        WikiEntity build() {
            return new WikiEntity(qid, labelUk, labelEn, ukTitle, instanceOf, citizenships, birthPlaces,
                    formationPlaces, residences, workPlaces, inception, dissolved, workStart, workEnd, imageFile,
                    genres, socialIds, lat, lon, partOf, occupations, birthDate, deathDate, movements, notableWorks,
                    commonsCategory, wikisourceTitle, awards, countries, wholes, conferredBy);
        }
    }
}
