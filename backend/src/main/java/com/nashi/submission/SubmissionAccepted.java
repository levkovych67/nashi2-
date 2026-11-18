package com.nashi.submission;

/** The {@code 202} body: the submission waits for moderation. */
public record SubmissionAccepted(String id, String status) {

    public static SubmissionAccepted pending(String id) {
        return new SubmissionAccepted(id, "PENDING");
    }
}
