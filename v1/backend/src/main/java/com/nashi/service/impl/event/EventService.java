package com.nashi.service.impl.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.api.exception.ExternalServiceException;
import com.nashi.api.exception.FileUploadException;
import com.nashi.api.exception.ResourceNotFoundException;
import com.nashi.domain.Event;
import com.nashi.dto.event.EventCoordinatesDTO;
import com.nashi.dto.event.EventCreateRequestDTO;
import com.nashi.dto.event.EventResponseDTO;
import com.nashi.enums.EventStatus;
import com.nashi.enums.Region;
import com.nashi.mapper.EventMapper;
import com.nashi.repo.EventRepository;
import com.nashi.service.DataValidationService;
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import com.nashi.service.impl.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final S3Service s3Service;
    private final TelegramService telegramService;
    private final DataValidationService dataValidationService;
    private final AiRequestService aiRequestService;
    private final ObjectMapper objectMapper;

    /**
     * Handles the user submission of a new event.
     * Uploads image, fetches coordinates via AI, saves to DB, and notifies admin.
     */
    @Transactional
    public void suggestEvent(EventCreateRequestDTO request, MultipartFile image) {
        log.info("Starting suggestEvent for title: '{}', city: '{}'", request.getTitle(), request.getCity());

        Event event = EventMapper.toEntity(request);
        event.setStatus(EventStatus.PENDING);

        // --- 1. AI Coordinate Fetching ---
        try {
            log.debug("Requesting coordinates from AI for venue: '{}'", event.getVenue());
            String jsonResponse = aiRequestService.getEventCoordinates(event);

            EventCoordinatesDTO coordinates = objectMapper.readValue(jsonResponse, EventCoordinatesDTO.class);

            if (coordinates != null && coordinates.getLatitude() != null) {
                event.setLocation(new GeoJsonPoint(coordinates.getLongitude(), coordinates.getLatitude()));
                log.info("Coordinates resolved: [{}, {}]", coordinates.getLatitude(), coordinates.getLongitude());
            } else {
                log.warn("AI returned null or incomplete coordinates for event: {}", request.getTitle());
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI coordinate response for event: {}", request.getTitle(), e);
            throw new ExternalServiceException("AI", "Failed to parse coordinates from AI service", e);
        } catch (Exception e) {
            log.error("Unexpected error during AI coordinate lookup", e);
            throw new ExternalServiceException("AI", "Failed to fetch coordinates from AI service", e);
        }

        // --- 2. Image Upload ---
        if (image != null && !image.isEmpty()) {
            try {
                log.debug("Validating and uploading image for event: {}", request.getTitle());
                List<MultipartFile> validatedFiles = dataValidationService.validateImages(List.of(image));

                if (!validatedFiles.isEmpty()) {
                    String imageKey = s3Service.uploadFile(validatedFiles.get(0), "events/images");
                    event.setEventImageKey(imageKey);
                    log.info("Image uploaded successfully. Key: {}", imageKey);
                }
            } catch (Exception e) {
                log.error("Failed to upload image for event: {}", request.getTitle(), e);
                throw new FileUploadException("Failed to upload event image: " + e.getMessage(), e);
            }
        } else {
            log.debug("No image provided for event: {}", request.getTitle());
        }

        // --- 3. Save and Notify ---
        Event savedEvent = eventRepository.save(event);
        log.info("Event saved with ID: {}. Status: PENDING", savedEvent.getId());

        try {
            telegramService.sendEventForApproval(savedEvent);
            log.info("Telegram notification sent for event ID: {}", savedEvent.getId());
        } catch (Exception e) {
            log.error("Failed to send Telegram notification for event ID: {}", savedEvent.getId(), e);
            // We usually don't fail the request if notification fails, just log it
        }
    }

    public List<EventResponseDTO> getUpcomingEvents(Region region) {
        log.info("Fetching upcoming events. Region: {}", (region != null ? region : "ALL"));

        LocalDateTime now = LocalDateTime.now();
        List<Event> events;

        if (region != null) {
            events = eventRepository.findByStatusAndRegionAndDateTimeAfterOrderByDateTimeAsc(
                    EventStatus.APPROVED, region, now);
        } else {
            events = eventRepository.findByStatusAndDateTimeAfterOrderByDateTimeAsc(
                    EventStatus.APPROVED, now);
        }

        log.info("Found {} upcoming events for region: {}", events.size(), (region != null ? region : "ALL"));

        return events.stream().map(this::toDto).toList();
    }

    /** Public page: an event that is not APPROVED looks exactly like a missing one. */
    public EventResponseDTO getEventById(String id) {
        log.debug("Fetching event by ID: {}", id);

        Event event = eventRepository.findById(id)
                .filter(found -> found.getStatus() == EventStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));

        return EventMapper.toResponseDTO(event);
    }

    /**
     * Internal helper to map Entity -> DTO and resolve Image URL.
     */
    private EventResponseDTO toDto(Event event) {
        String finalImageUrl = null;

        if (event.getEventImageKey() != null && !event.getEventImageKey().isBlank()) {
            // Note: getFileUrl typically generates a presigned URL or full public URL
            finalImageUrl = s3Service.getFileUrl(event.getEventImageKey());
        }

        // Using the static mapper or builder manually as previously defined
        // Ensure consistent usage of your Mapper strategy here.
        // Below is a mix of manual builder + s3 logic based on your snippet.

        return EventResponseDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .city(event.getCity())
                .region(event.getRegion())
                .venue(event.getVenue())
                .dateTime(event.getDateTime())
                .ticketUrl(event.getTicketUrl())
                .artistName(event.getArtistName())
                .eventImageKey(finalImageUrl) // Setting resolved URL here
                // Note: Ensure other fields like lat/long are mapped if needed
                .build();
    }
}
