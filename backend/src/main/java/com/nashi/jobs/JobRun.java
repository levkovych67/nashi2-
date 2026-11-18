package com.nashi.jobs;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/** One run of a background job. {@code status} is {@code RUNNING}, {@code OK} or {@code FAILED}; {@code report} is free-form. */
@Document("jobs_runs")
@Data
public class JobRun {

    @Id
    private String id;
    private JobKind job;
    private Instant startedAt;
    private Instant finishedAt;
    private String status;
    private Map<String, Object> report;
}
