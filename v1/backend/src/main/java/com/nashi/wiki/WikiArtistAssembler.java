package com.nashi.wiki;

import com.nashi.enums.ArtistCategory;
import com.nashi.enums.LocationBasis;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Turns a Wikidata item into the data we store, or nothing when the item must not be imported. */
public final class WikiArtistAssembler {

    static final String RUSSIA = "Q159";
    static final String HUMAN = "Q5";
    static final int MAX_GENRES = 5;
    private static final Pattern PATRONYMIC = Pattern.compile(".+(ович|евич|йович|ич|іч|івна|ївна|овна|евна|ична)");

    private WikiArtistAssembler() {
    }

    /**
     * @param group         the candidate query that found the item; gives the kind when no occupation is in a tree
     * @param places        places in Ukraine, from {@link PlaceResolver#resolve}
     * @param labels        genres (P136) and movements (P135) with their labels
     * @param kinds         occupation → kind
     * @param placeLabel    label of any fetched place, in Ukraine or not; null when unknown
     * @param russianAwards awards for which {@link #isRussianAward} holds
     */
    public static Optional<WikiArtistData> assemble(WikiEntity e, CandidateGroup group, Map<String, WikiPlace> places,
                                                    Map<String, WikiEntity> labels, OccupationCategories kinds,
                                                    Function<String, String> placeLabel, Set<String> russianAwards) {
        if (russiaLinked(e, russianAwards)) {
            return Optional.empty();
        }
        boolean ensemble = !e.instanceOf().equals(Set.of(HUMAN));
        String name = name(e);
        if (name == null) {
            return Optional.empty();
        }
        name = displayName(name, e.ukTitle(), ensemble);

        Map<LocationBasis, List<String>> candidatePlaces = new LinkedHashMap<>();
        if (ensemble) {
            candidatePlaces.put(LocationBasis.FORMATION, e.formationPlaces());
        } else {
            candidatePlaces.put(LocationBasis.BIRTH, e.birthPlaces());
            candidatePlaces.put(LocationBasis.RESIDENCE, e.residences());
            candidatePlaces.put(LocationBasis.WORK, e.workPlaces());
        }
        Located located = locate(candidatePlaces, places);

        Set<String> tags = new LinkedHashSet<>();
        for (String genreId : e.genres()) {
            WikiEntity genre = labels.get(genreId);
            if (genre != null && genre.labelEn() != null) {
                tags.add(genre.labelEn().toLowerCase(Locale.ROOT));
            }
        }
        List<String> genres = genres(e, labels);

        return Optional.of(new WikiArtistData(
                e.qid(),
                name,
                e.ukTitle(),
                ensemble,
                null,
                genres.isEmpty() ? null : genres.get(0),
                tags,
                ensemble ? e.inception() : e.workStart(),
                ensemble ? e.dissolved() : e.workEnd(),
                located.place(),
                e.imageFile(),
                WikiLinkTable.links(e),
                null,
                null,
                categories(e, ensemble, group, kinds),
                ensemble || e.birthDate() == null ? null : e.birthDate().iso(),
                ensemble || e.deathDate() == null ? null : e.deathDate().iso(),
                ensemble || e.birthPlaces().isEmpty() ? null : placeLabel.apply(e.birthPlaces().get(0)),
                located.basis(),
                genres,
                List.of()));
    }

    private record Located(WikiPlace place, LocationBasis basis) {
    }

    /** The first place in Ukraine, trying the lists in order, with the list it came from. */
    private static Located locate(Map<LocationBasis, List<String>> candidatePlaces, Map<String, WikiPlace> places) {
        for (Map.Entry<LocationBasis, List<String>> entry : candidatePlaces.entrySet()) {
            for (String id : entry.getValue()) {
                WikiPlace place = places.get(id);
                if (place != null) {
                    return new Located(place, entry.getKey());
                }
            }
        }
        return new Located(null, null);
    }

    /** A citizen of Russia, or a holder of any award in {@code russianAwards}. */
    static boolean russiaLinked(WikiEntity e, Set<String> russianAwards) {
        return e.citizenships().contains(RUSSIA) || e.awards().stream().anyMatch(russianAwards::contains);
    }

    /**
     * An award or title of the Russian Federation: Russia is the only country it states (P17), or its administrative
     * unit (P131). Awards of the USSR, the RSFSR (their titles state the USSR as country and the RSFSR as unit; owner
     * decision, 2026-11-09) or the Russian Empire, and awards stated for several countries (such as jubilee medals of
     * the CIS states), are not.
     */
    static boolean isOfRussia(WikiEntity award) {
        boolean onlyRussia = !award.countries().isEmpty() && award.countries().stream().allMatch(RUSSIA::equals);
        return onlyRussia || award.partOf().contains(RUSSIA);
    }

    /**
     * A state honour of the Russian Federation (owner decisions: 2026-11-08 «Народний артист Росії», orders of the RF;
     * 2026-11-09 not the titles of the RSFSR): {@link #isOfRussia} and {@link StateHonours#isStateHonour}. Private and
     * union prizes, honorary diplomas, church orders, titles of regions and departmental awards do not count.
     *
     * @param wholes awards that degrees belong to (P361), by QID
     */
    static boolean isRussianAward(WikiEntity award, Map<String, WikiEntity> wholes, StateHonours honours) {
        return isOfRussia(award) && honours.isStateHonour(award, wholes);
    }

    /** Ensembles are bands; people get the kinds of their occupations, else the kind of the query that found them. */
    private static List<ArtistCategory> categories(WikiEntity e, boolean ensemble, CandidateGroup group,
                                                   OccupationCategories kinds) {
        if (ensemble) {
            return List.of(ArtistCategory.BAND);
        }
        List<ArtistCategory> categories = kinds.categoriesOf(e.occupations());
        if (!categories.isEmpty() || group == null) {
            return categories;
        }
        return List.of(switch (group) {
            case MUSIC -> ArtistCategory.MUSICIAN;
            case ENSEMBLE -> ArtistCategory.BAND;
            case VISUAL -> ArtistCategory.VISUAL_ARTIST;
            case WRITER -> ArtistCategory.WRITER;
        });
    }

    /** Labels of genres, then movements: Ukrainian, else English; each once, at most {@value #MAX_GENRES}. */
    private static List<String> genres(WikiEntity e, Map<String, WikiEntity> labels) {
        Set<String> genres = new LinkedHashSet<>();
        Stream.of(e.genres(), e.movements()).flatMap(List::stream).distinct().forEach(id -> {
            WikiEntity label = labels.get(id);
            if (label == null || genres.size() >= MAX_GENRES) {
                return;
            }
            String text = label.labelUk() != null ? label.labelUk() : label.labelEn();
            if (text != null && !text.isBlank()) {
                genres.add(text.trim());
            }
        });
        return List.copyOf(genres);
    }

    private static String name(WikiEntity e) {
        if (e.labelUk() != null && !e.labelUk().isBlank()) {
            return e.labelUk().trim();
        }
        if (e.ukTitle() != null) {
            return e.ukTitle();
        }
        return null;
    }

    /**
     * The name as people know it: no Wikipedia qualifier such as "(співак)", and for people the given name first
     * without a patronymic.
     */
    static String displayName(String name, String title, boolean ensemble) {
        String clean = withoutQualifier(name);
        return ensemble ? clean : givenNameFirst(clean, title == null ? null : withoutQualifier(title));
    }

    private static String withoutQualifier(String text) {
        String clean = text.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
        return clean.isEmpty() ? text.trim() : clean;
    }

    /**
     * Encyclopedia order "Чубай Тарас Григорович" (or "Федорова Анна" from "Федорова Анна Борисівна") becomes
     * "Тарас Чубай", the way people know the artist. Names without a patronymic stay as they are.
     */
    static String givenNameFirst(String name, String title) {
        String[] words = name.trim().split("\\s+");
        if (words.length == 3 && isPatronymic(words[2])) {
            return words[1] + " " + words[0];
        }
        if (words.length == 3 && isPatronymic(words[1])) {
            // "Аркадій Хунович Гендлер": given name, patronymic, surname
            return words[0] + " " + words[2];
        }
        if (words.length == 2 && title != null) {
            String[] titleWords = title.trim().split("\\s+");
            if (titleWords.length == 3 && isPatronymic(titleWords[2])
                    && titleWords[0].equals(words[0]) && titleWords[1].equals(words[1])) {
                return words[1] + " " + words[0];
            }
        }
        return name;
    }

    private static boolean isPatronymic(String word) {
        // Some labels carry a Latin "i" in place of the Ukrainian "і" (Петрiвна).
        return PATRONYMIC.matcher(word.toLowerCase(Locale.ROOT).replace('i', 'і')).matches();
    }
}
