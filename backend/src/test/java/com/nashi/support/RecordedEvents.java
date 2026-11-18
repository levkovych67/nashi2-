package com.nashi.support;

import com.nashi.submission.SubmissionCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the events ITs care about. Spring's {@code @RecordApplicationEvents} only sees events published on the test
 * thread, and the application under test runs on server threads.
 */
@Component
public class RecordedEvents {

    private final List<SubmissionCreatedEvent> submissionCreated = new CopyOnWriteArrayList<>();

    @EventListener
    void on(SubmissionCreatedEvent event) {
        submissionCreated.add(event);
    }

    public List<SubmissionCreatedEvent> submissionCreated() {
        return List.copyOf(submissionCreated);
    }

    public void clear() {
        submissionCreated.clear();
    }
}
