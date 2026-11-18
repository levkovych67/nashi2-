package com.nashi.support;

import com.nashi.artist.ArtistChangedEvent;
import com.nashi.moderation.AudioResolveRequested;
import com.nashi.moderation.EnrichmentRequested;
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
    /** {@link ArtistChangedEvent}, {@link EnrichmentRequested} and {@link AudioResolveRequested}, in order. */
    private final List<Object> artistEvents = new CopyOnWriteArrayList<>();

    @EventListener
    void on(SubmissionCreatedEvent event) {
        submissionCreated.add(event);
    }

    @EventListener
    void on(ArtistChangedEvent event) {
        artistEvents.add(event);
    }

    @EventListener
    void on(EnrichmentRequested event) {
        artistEvents.add(event);
    }

    @EventListener
    void on(AudioResolveRequested event) {
        artistEvents.add(event);
    }

    public List<SubmissionCreatedEvent> submissionCreated() {
        return List.copyOf(submissionCreated);
    }

    public List<Object> artistEvents() {
        return List.copyOf(artistEvents);
    }

    public void clear() {
        submissionCreated.clear();
        artistEvents.clear();
    }
}
