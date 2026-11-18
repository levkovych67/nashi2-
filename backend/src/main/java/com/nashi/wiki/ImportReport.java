package com.nashi.wiki;

/**
 * The result of one import run.
 *
 * @param failedCandidateChunks candidate queries that failed for good; the run went on without their items
 * @param russiaLinked          items left out as linked to Russia (citizenship, a state award or title of Russia)
 * @param failure               null when the run finished; otherwise why it stopped
 */
public record ImportReport(int candidates, int alreadyKnown, int added, int withoutPlace, int errors,
                           int failedCandidateChunks, int russiaLinked, String failure) {

    public String toTelegramText() {
        if (failure != null) {
            return "Імпорт з Вікіпедії впав: " + failure + ". Збережено до збою: " + added + ".";
        }
        return "Імпорт з Вікіпедії: знайдено " + candidates + ", уже були " + alreadyKnown + ", додано " + added
                + " (без місця на карті " + withoutPlace + "), помилок " + errors
                + (russiaLinked > 0 ? ", відсіяно через зв'язок з РФ: " + russiaLinked : "")
                + (failedCandidateChunks > 0
                        ? ", не вдалося отримати частину кандидатів: " + failedCandidateChunks + " запитів"
                        : "")
                + ".";
    }
}
