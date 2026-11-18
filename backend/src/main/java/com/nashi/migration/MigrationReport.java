package com.nashi.migration;

import com.nashi.telegram.Html;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of one migration run; a dry run reports what a real run would do.
 *
 * @param found                V1 artists in scope: approved, added through the form (no source)
 * @param migrated             inserted into V2 (a dry run: would be)
 * @param skippedExisting      already migrated by an earlier run ({@code legacyV1Id} found)
 * @param skippedDuplicateName a published V2 artist has the same normalized name
 * @param skippedNonMusic      only visual artists and writers: phase 1 is musicians only
 * @param withoutPlace         migrated, but no place was found, so not on the map
 * @param denylisted           V1 Wikipedia artists hidden by V1 administrators, copied to {@code wiki_denylist}
 * @param errors               one line per file or artist that failed; a failed file does not stop its artist
 */
public record MigrationReport(int found, int migrated, int skippedExisting, int skippedDuplicateName,
                              int skippedNonMusic, int withoutPlace, int denylisted, List<String> errors) {

    private static final int ERRORS_SHOWN = 5;

    public MigrationReport {
        errors = List.copyOf(errors);
    }

    /** The summary for the moderation group (HTML-escaped). */
    public String toTelegramText(boolean dryRun) {
        StringBuilder text = new StringBuilder("Міграція з V1")
                .append(dryRun ? " (пробний запуск, у V2 нічого не записано)" : "")
                .append(": знайдено ").append(found)
                .append(", перенесено ").append(migrated)
                .append(", уже були ").append(skippedExisting)
                .append(", дублікати за назвою ").append(skippedDuplicateName)
                .append(", не музика ").append(skippedNonMusic)
                .append(", без місця на карті ").append(withoutPlace)
                .append(", у списку заборони Вікіпедії ").append(denylisted)
                .append(", помилок ").append(errors.size()).append('.');
        errors.stream().limit(ERRORS_SHOWN).forEach(e -> text.append("\n• ").append(Html.escape(e)));
        if (errors.size() > ERRORS_SHOWN) {
            text.append("\n… і ще ").append(errors.size() - ERRORS_SHOWN);
        }
        return text.toString();
    }

    /** The report of the run in {@code jobs_runs}. */
    public Map<String, Object> toMap(boolean dryRun) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dryRun", dryRun);
        map.put("found", found);
        map.put("migrated", migrated);
        map.put("skippedExisting", skippedExisting);
        map.put("skippedDuplicateName", skippedDuplicateName);
        map.put("skippedNonMusic", skippedNonMusic);
        map.put("withoutPlace", withoutPlace);
        map.put("denylisted", denylisted);
        map.put("errors", errors);
        return map;
    }
}
