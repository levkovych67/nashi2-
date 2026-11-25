package com.nashi.service.impl.event;

import com.nashi.api.exception.AlreadyProcessedException;
import com.nashi.domain.Event;
import com.nashi.domain.NewsPost;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.enums.EventStatus;
import com.nashi.repo.EventRepository;
import com.nashi.repo.NewsPostRepository;
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventApprovalServiceImplTest {

    private EventRepository eventRepository;
    private AiRequestService aiRequestService;
    private S3Service s3Service;
    private NewsPostRepository newsPostRepository;
    private EventApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        aiRequestService = mock(AiRequestService.class);
        s3Service = mock(S3Service.class);
        newsPostRepository = mock(NewsPostRepository.class);
        service = new EventApprovalServiceImpl(eventRepository, aiRequestService, s3Service, newsPostRepository);
    }

    @Test
    void approveIgnoresNonPendingEvent() {
        for (EventStatus status : List.of(EventStatus.APPROVED, EventStatus.REJECTED)) {
            Event event = event(status);
            when(eventRepository.findById("e1")).thenReturn(Optional.of(event));

            AlreadyProcessedException e = assertThrows(AlreadyProcessedException.class, () -> service.approveEvent("e1"));

            assertEquals("Заявку вже оброблено (статус: " + status + ")", e.getMessage());
            assertEquals(status, event.getStatus());
        }
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(aiRequestService, newsPostRepository);
    }

    @Test
    void rejectIgnoresNonPendingEvent() {
        for (EventStatus status : List.of(EventStatus.APPROVED, EventStatus.REJECTED)) {
            when(eventRepository.findById("e1")).thenReturn(Optional.of(event(status)));

            AlreadyProcessedException e = assertThrows(AlreadyProcessedException.class, () -> service.rejectEvent("e1"));

            assertEquals("Заявку вже оброблено (статус: " + status + ")", e.getMessage());
        }
        verify(eventRepository, never()).delete(any());
        verifyNoInteractions(s3Service);
    }

    @Test
    void approvePendingEventPublishesIt() {
        Event pending = event(EventStatus.PENDING);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(pending));
        when(aiRequestService.generateEventSocialMediaPost(pending)).thenReturn("Концерт у Львові");
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(call -> call.getArgument(0));

        TelegramNewsPostDTO post = service.approveEvent("e1");

        assertEquals(EventStatus.APPROVED, pending.getStatus());
        verify(eventRepository).save(pending);
        assertEquals("Концерт у Львові", post.getCaption());
    }

    @Test
    void rejectPendingEventDeletesItAndItsImage() {
        Event pending = event(EventStatus.PENDING);
        when(eventRepository.findById("e1")).thenReturn(Optional.of(pending));

        service.rejectEvent("e1");

        verify(s3Service).deleteFile("events/e.jpg");
        verify(eventRepository).delete(pending);
    }

    private static Event event(EventStatus status) {
        return Event.builder().id("e1").title("Концерт").eventImageKey("events/e.jpg").status(status).build();
    }
}
