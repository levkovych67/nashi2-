package com.nashi.moderation;

import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import org.jspecify.annotations.Nullable;

/**
 * The submission was no longer {@code PENDING} when the decision arrived (another moderator was first, or the button
 * is old). Nothing was changed. {@link #current()} is the submission as stored now, {@code null} when it does not
 * exist.
 */
public class AlreadyDecidedException extends RuntimeException {

    private final transient @Nullable Submission current;

    public AlreadyDecidedException(@Nullable Submission current) {
        super("Submission is not pending: " + (current == null ? "not found" : current.getStatus()));
        this.current = current;
    }

    public @Nullable Submission current() {
        return current;
    }

    public @Nullable SubmissionStatus currentStatus() {
        return current == null ? null : current.getStatus();
    }
}
