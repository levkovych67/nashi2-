package com.nashi.wiki;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The result of one finished import run. A run that stops with an exception has no report: {@code JobRunLog} records
 * it as {@code FAILED} and alerts the group.
 *
 * @param alreadyKnown          candidates skipped as already in the database (by Wikidata id, article title or the
 *                              name of an artist added through the form)
 * @param withoutPlace          added without an origin, so not on the map
 * @param failedCandidateChunks candidate queries that failed for good; the run went on without their items
 * @param russiaLinked          items left out as linked to Russia (citizenship, a state award or title of Russia)
 */
public record ImportReport(int candidates, int alreadyKnown, int added, int withoutPlace, int errors,
                           int failedCandidateChunks, int russiaLinked) {

    public String toTelegramText() {
        return "Імпорт з Вікіпедії: знайдено " + candidates + ", уже були " + alreadyKnown + ", додано " + added
                + " (без місця на карті " + withoutPlace + "), помилок " + errors
                + (russiaLinked > 0 ? ", відсіяно через зв'язок з РФ: " + russiaLinked : "")
                + (failedCandidateChunks > 0
                        ? ", не вдалося отримати частину кандидатів: " + failedCandidateChunks + " запитів"
                        : "")
                + ".";
    }

    /** The report of the run in {@code jobs_runs}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("candidates", candidates);
        map.put("alreadyKnown", alreadyKnown);
        map.put("added", added);
        map.put("withoutPlace", withoutPlace);
        map.put("errors", errors);
        map.put("failedCandidateChunks", failedCandidateChunks);
        map.put("russiaLinked", russiaLinked);
        return map;
    }
}
