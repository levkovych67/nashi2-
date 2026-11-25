package com.nashi.service.impl.event;

import com.nashi.api.exception.AlreadyProcessedException;
import com.nashi.domain.Event;
import com.nashi.domain.NewsPost;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.enums.EventStatus;
import com.nashi.repo.EventRepository;
import com.nashi.repo.NewsPostRepository;
import com.nashi.service.EventApprovalService; // You need to create this interface
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventApprovalServiceImpl implements EventApprovalService {

    private final EventRepository eventRepository;
    private final AiRequestService aiRequestService;
    private final S3Service s3Service;
    private final NewsPostRepository newsPostRepository;

    @Override
    @Transactional
    public TelegramNewsPostDTO approveEvent(String eventId) {
        Event event = getPendingEventOrThrow(eventId);

        event.setStatus(EventStatus.APPROVED);
        eventRepository.save(event);

        return toTelegramDto(Objects.requireNonNull(createNewsPostForEvent(event)));
    }

    @Override
    @Transactional
    public void rejectEvent(String eventId) {
        Event event = getPendingEventOrThrow(eventId);

        log.info("Rejecting event: {}. Starting cleanup...", event.getTitle());

        // 1. Cleanup Image
        if (event.getEventImageKey() != null) {
            try {
                s3Service.deleteFile(event.getEventImageKey());
            } catch (Exception e) {
                log.error("Failed to delete event image from S3: " + event.getEventImageKey(), e);
            }
        }

        // 2. Delete Entity
        eventRepository.delete(event);
        log.info("Event rejected and deleted: {}", event.getId());
    }

    // --- Private Helpers ---

    /** The buttons act on PENDING events only: an old button must not delete or re-post a published event. */
    private Event getPendingEventOrThrow(String id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
        if (event.getStatus() != EventStatus.PENDING) {
            log.info("Ignored approval button for event {}: status is {}", id, event.getStatus());
            throw new AlreadyProcessedException(event.getStatus());
        }
        return event;
    }

    private NewsPost createNewsPostForEvent(Event event) {
        try {
            log.info("Generating news post for event: {}", event.getTitle());

            // 1. Generate Text via AI (You need to add this method to AiRequestService)
            String smmText = aiRequestService.generateEventSocialMediaPost(event);

            // 2. Save NewsPost
            // Note: You might need to add 'eventId' or 'eventTitle' fields to your NewsPost entity
            // or reuse existing fields if your design allows.
            NewsPost newsPost = NewsPost.builder()
                    .content(smmText)
                    .imageKey(event.getEventImageKey())
                    .artistName(event.getArtistName()) // Can be null for generic events
                    // .eventId(event.getId()) // Recommended: Add this field to NewsPost
                    // .title(event.getTitle()) // Recommended: Add this field to NewsPost
                    .build();

            return newsPostRepository.save(newsPost);

        } catch (Exception e) {
            log.error("Failed to create news post for event: " + event.getTitle(), e);
        }
        return null;
    }

    private TelegramNewsPostDTO toTelegramDto(NewsPost newsPost) {
        String imageUrl = null;

        if (newsPost.getImageKey() != null && !newsPost.getImageKey().isBlank()) {
            imageUrl = s3Service.getFileUrl(newsPost.getImageKey());
        }

        return TelegramNewsPostDTO.builder()
                .caption(newsPost.getContent())
                .imageUrl(imageUrl)
                // Events usually don't have audio tracks attached to the post
                .build();
    }
}