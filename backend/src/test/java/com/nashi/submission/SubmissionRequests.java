package com.nashi.submission;

import com.nashi.artist.MusicianKind;

import java.util.List;

/** Valid requests for the submission tests; each test changes what it checks. */
final class SubmissionRequests {

    static final String SUMMARY = "Гурт грає інді-фолк уже десять років і щороку їздить із концертами по всій країні.";

    private SubmissionRequests() {
    }

    static SubmissionRequest valid() {
        return new SubmissionRequest("Океан Ельзи", MusicianKind.BAND, "Q36036", null, SUMMARY,
                List.of("інді", "фолк"), List.of("https://open.spotify.com/artist/abc"),
                "https://www.youtube.com/@band", null, "telegram @nick", true, "");
    }

    static SubmissionRequest with(SubmissionRequest r, String name, MusicianKind kind, String origin, String birth,
                                  String summary, List<String> links, String youtube, String wiki, Boolean consent) {
        return new SubmissionRequest(name, kind, origin, birth, summary, r.genres(), links, youtube, wiki,
                r.contact(), consent, r.faxNumber());
    }
}
