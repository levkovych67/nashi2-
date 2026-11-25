package com.nashi.wiki;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which awards are state honours: orders, medals, state decorations (with state prizes) and honorary titles, each
 * with all its subclasses ({@code wdt:P279*}), fetched once per run like the occupation trees. Private, union and
 * academy prizes, festivals, honorary diplomas and thanks are not in these trees. The QIDs and labels were checked
 * against the live API on 2026-11-08.
 */
public final class StateHonours {

    /** Order (орден). */
    static final String ORDER = "Q193622";
    /** Medal (медаль). */
    static final String MEDAL = "Q131647";
    /** State decoration (державна нагорода); covers state prizes (state prize Q141672628, of Russia Q141672666). */
    static final String STATE_DECORATION = "Q3302125";
    /** Title of honour (почесне звання); covers the honorary titles of the RF (Q4375520). */
    static final String HONORARY_TITLE = "Q3320743";
    static final List<String> ROOTS = List.of(ORDER, MEDAL, STATE_DECORATION, HONORARY_TITLE);

    /**
     * Departmental award of Russia (відомча нагорода): medals of ministries, such as the Ushinsky Medal. Its items are
     * also medals, so its tree is fetched on its own and an award in it is no state honour.
     */
    static final String DEPARTMENTAL = "Q6866472";

    /**
     * Subtrees of title of honour that are no state honours: honorary degree (Honoris causa), honorary citizenship
     * (почесний громадянин, given by cities and regions), honorific (гонорифік), ecclesiastical address (церковне
     * звертання) and honorary title of a federal subject of Russia (почесне звання суб'єкта РФ, such as the titles of
     * Tatarstan).
     */
    static final List<String> HONORARY_TITLE_EXCLUDED =
            List.of("Q209896", "Q987080", "Q1326966", "Q3562482", "Q4375517");

    /**
     * Grade of an order (ступінь ордена) and class of award (клас нагороди): such an item is judged by the award it
     * is part of (P361), so a degree of a church order or a category of a film prize is not taken for a state honour.
     */
    static final Set<String> DEGREES = Set.of("Q1499804", "Q38033430");

    /** Orders of the Russian Orthodox Church (Q60995) are orders, but no state honours. */
    static final Set<String> NOT_STATE = Set.of("Q60995");

    private final Set<String> classes;
    private final Set<String> departmental;

    /**
     * @param classes      the subclass trees of {@link #ROOTS}
     * @param departmental the subclass tree of {@link #DEPARTMENTAL}
     */
    public StateHonours(Collection<String> classes, Collection<String> departmental) {
        this.classes = Set.copyOf(classes);
        this.departmental = Set.copyOf(departmental);
    }

    /** Subtrees left out below the root. */
    static List<String> excludedBelow(String root) {
        return HONORARY_TITLE.equals(root) ? HONORARY_TITLE_EXCLUDED : List.of();
    }

    /** A grade or class of another award. */
    boolean isDegree(WikiEntity award) {
        return award.instanceOf().stream().anyMatch(DEGREES::contains);
    }

    /** The award that decides: for a degree, the first award it is part of that is known, else the award itself. */
    WikiEntity decisive(WikiEntity award, Map<String, WikiEntity> wholes) {
        if (!isDegree(award)) {
            return award;
        }
        return award.wholes().stream().map(wholes::get).filter(Objects::nonNull).findFirst().orElse(award);
    }

    /** An order, medal, state decoration or honorary title, not conferred by a church, and no departmental award. */
    boolean isStateHonour(WikiEntity award, Map<String, WikiEntity> wholes) {
        WikiEntity decisive = decisive(award, wholes);
        return decisive.instanceOf().stream().anyMatch(classes::contains)
                && decisive.instanceOf().stream().noneMatch(departmental::contains)
                && decisive.conferredBy().stream().noneMatch(NOT_STATE::contains);
    }

    int size() {
        return classes.size();
    }
}
