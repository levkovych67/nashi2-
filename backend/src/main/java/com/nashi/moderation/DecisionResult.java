package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.submission.Submission;
import com.nashi.telegram.Verdict;
import org.jspecify.annotations.Nullable;

/** A decision that took effect: the submission as stored after it and, for {@code YES}, the new artist. */
public record DecisionResult(Verdict verdict, Submission submission, @Nullable Artist artist) {
}
