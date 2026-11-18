package com.nashi.wiki;

/**
 * One row of a works query ({@link WikiQueries#albums}). A work can come in several rows (two release dates, two
 * images); {@link WorkSelector} keeps one per creator.
 *
 * @param creator         QID of the artist or author
 * @param work            QID of the work
 * @param title           uk label, else en label; null when the work has neither
 * @param year            of publication or inception
 * @param imageFile       Commons file name of P18 (the cover), without "File:"
 * @param ukTitle         title of the uk.wikipedia article about the work
 * @param notable         the creator lists it as a notable work (P800)
 */
public record WorkRow(
        String creator,
        String work,
        String title,
        Integer year,
        String imageFile,
        String ukTitle,
        boolean notable) {
}
