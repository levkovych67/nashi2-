package com.nashi.wiki;

/**
 * One row of a works query. A work can come in several rows (two images, two dates, found as notable and as authored);
 * {@link WorkSelector} keeps one per creator.
 *
 * @param creator         QID of the artist or author
 * @param work            QID of the work
 * @param title           uk label, else en label; null when the work has neither
 * @param year            of publication or inception
 * @param imageFile       Commons file name of P18 (the cover), without "File:"
 * @param ukTitle         title of the uk.wikipedia article about the work
 * @param wikisourceTitle title of the work's text on uk.wikisource
 * @param notable         the creator lists it as a notable work (P800)
 */
public record WorkRow(
        String creator,
        String work,
        String title,
        Integer year,
        String imageFile,
        String ukTitle,
        String wikisourceTitle,
        boolean notable) {
}
