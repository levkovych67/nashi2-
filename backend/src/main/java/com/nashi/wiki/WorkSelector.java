package com.nashi.wiki;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Picks the works (albums) to show for each creator. */
public final class WorkSelector {

    /**
     * Notable first, then with a uk.wikipedia article, with a title; then by year (none last) and by QID as a number, so the choice is the same from run to run.
     */
    static final Comparator<WorkRow> ORDER = Comparator.comparing((WorkRow r) -> !r.notable())
            .thenComparing(r -> r.ukTitle() == null)
            .thenComparing(r -> r.title() == null)
            .thenComparing(WorkRow::year, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(r -> qidNumber(r.work()));

    private WorkSelector() {
    }

    /**
     * Up to {@code max} works per creator, in {@link #ORDER}, each work once per creator (its best row). Creators are
     * keyed in the order they first appear in {@code rows}.
     */
    public static Map<String, List<WorkRow>> select(List<WorkRow> rows, int max) {
        Map<String, List<WorkRow>> byCreator = new LinkedHashMap<>();
        for (WorkRow row : rows) {
            byCreator.computeIfAbsent(row.creator(), creator -> new ArrayList<>()).add(row);
        }
        Map<String, List<WorkRow>> selected = new LinkedHashMap<>();
        byCreator.forEach((creator, own) -> {
            Set<String> seen = new HashSet<>();
            selected.put(creator, own.stream()
                    .sorted(ORDER)
                    .filter(row -> seen.add(row.work()))
                    .limit(max)
                    .toList());
        });
        return selected;
    }

    private static long qidNumber(String qid) {
        try {
            return Long.parseLong(qid.substring(1));
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }
}
