package com.nashi.service.impl.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.api.exception.ResourceNotFoundException;
import com.nashi.domain.Event;
import com.nashi.enums.EventStatus;
import com.nashi.repo.EventRepository;
import com.nashi.service.DataValidationService;
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import com.nashi.service.impl.TelegramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private EventRepository eventRepository;
    private EventService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        service = new EventService(eventRepository, mock(S3Service.class), mock(TelegramService.class),
                mock(DataValidationService.class), mock(AiRequestService.class), new ObjectMapper());
    }

    @Test
    void pendingEventByIdIsNotFound() {
        when(eventRepository.findById("e1")).thenReturn(Optional.of(event("e1", EventStatus.PENDING)));
        when(eventRepository.findById("e2")).thenReturn(Optional.of(event("e2", EventStatus.REJECTED)));
        when(eventRepository.findById("e3")).thenReturn(Optional.of(event("e3", EventStatus.APPROVED)));

        ResourceNotFoundException pending = assertThrows(ResourceNotFoundException.class,
                () -> service.getEventById("e1"));
        ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
                () -> service.getEventById("e9"));
        assertThrows(ResourceNotFoundException.class, () -> service.getEventById("e2"));
        // A pending event looks exactly like a missing one.
        assertEquals(missing.getMessage().replace("e9", "e1"), pending.getMessage());

        assertEquals("Концерт e3", service.getEventById("e3").getTitle());
    }

    private static Event event(String id, EventStatus status) {
        return Event.builder().id(id).title("Концерт " + id).status(status).build();
    }
}
