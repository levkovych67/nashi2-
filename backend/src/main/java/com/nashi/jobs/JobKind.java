package com.nashi.jobs;

/** Background jobs recorded in {@code jobs_runs} ({@code docs/02-contracts.md} section 3). */
public enum JobKind {
    WIKI_IMPORT, AUDIO_RESOLVE, SUBMISSION_CLEANUP, V1_MIGRATION
}
