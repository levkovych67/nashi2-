package com.nashi.submission;

import com.nashi.api.FieldViolation;
import com.nashi.api.ValidationFailedException;
import com.nashi.artist.Artist;
import com.nashi.media.FileStorage;
import com.nashi.media.ImageProcessor;
import com.nashi.media.ProcessedImage;
import com.nashi.ratelimit.RateLimitPolicy;
import com.nashi.ratelimit.RateLimiter;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a suggested musician: rate limit, bot trap, validation, photo processing, upload, save, event (in this
 * order, so a flooding client is stopped before any work and a bot learns nothing). Photos are processed before any
 * is uploaded; if the upload or the save fails, the keys already uploaded are deleted.
 */
@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private static final String JPEG = "image/jpeg";

    private final RateLimiter limiter;
    private final SubmissionValidator validator;
    private final ImageProcessor processor;
    private final FileStorage storage;
    private final SubmissionRepository submissions;
    private final DuplicateDetector duplicates;
    private final IpHasher ipHasher;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubmissionService(RateLimiter limiter, SubmissionValidator validator, ImageProcessor processor,
                             FileStorage storage, SubmissionRepository submissions, DuplicateDetector duplicates,
                             IpHasher ipHasher, ApplicationEventPublisher events, Clock clock) {
        this.limiter = limiter;
        this.validator = validator;
        this.processor = processor;
        this.storage = storage;
        this.submissions = submissions;
        this.duplicates = duplicates;
        this.ipHasher = ipHasher;
        this.events = events;
        this.clock = clock;
    }

    public SubmissionAccepted submit(SubmissionRequest r, List<MultipartFile> images, String clientIp) {
        limiter.check(RateLimitPolicy.SUBMISSIONS, clientIp);
        if (r.faxNumber() != null && !r.faxNumber().isBlank()) {
            Submission discarded = base(r, clientIp).id(new ObjectId().toHexString())
                    .status(SubmissionStatus.DISCARDED).build();
            return SubmissionAccepted.pending(submissions.save(discarded).getId());
        }
        List<FieldViolation> violations = validator.validate(r, images);
        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }
        String id = new ObjectId().toHexString();
        List<ProcessedImage> processed = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            processed.add(processor.process(read(images.get(i), i), "images[" + i + "]"));
        }
        List<String> keys = new ArrayList<>();
        try {
            for (int i = 0; i < processed.size(); i++) {
                String full = "submissions/" + id + "/" + i + ".jpg";
                String thumb = "submissions/" + id + "/" + i + "-thumb.jpg";
                storage.put(full, processed.get(i).full(), JPEG);
                keys.add(full);
                storage.put(thumb, processed.get(i).thumb(), JPEG);
                keys.add(thumb);
            }
            String duplicateId = duplicates.findDuplicate(r.name()).map(Artist::getId).orElse(null);
            submissions.save(base(r, clientIp).id(id).status(SubmissionStatus.PENDING).imageKeys(List.copyOf(keys))
                    .duplicateOfArtistId(duplicateId).build());
        } catch (RuntimeException e) {
            cleanUp(keys);
            throw e;
        }
        events.publishEvent(new SubmissionCreatedEvent(id));
        return SubmissionAccepted.pending(id);
    }

    private Submission.SubmissionBuilder base(SubmissionRequest r, String clientIp) {
        return Submission.builder()
                .name(trim(r.name())).kind(r.kind())
                .originPlaceId(trim(r.originPlaceId())).birthPlaceId(blankToNull(r.birthPlaceId()))
                .summary(trim(r.summary()))
                .genres(r.genres() == null ? List.of() : r.genres().stream().map(String::trim).toList())
                .links(r.links() == null ? List.of() : r.links().stream().map(SubmissionService::trim).toList())
                .youtubeChannelUrl(blankToNull(r.youtubeChannelUrl()))
                .wikipediaUrl(blankToNull(r.wikipediaUrl()))
                .contact(blankToNull(r.contact()))
                .consentAt(Boolean.TRUE.equals(r.consent()) ? clock.instant() : null)
                .ipHash(ipHasher.hash(clientIp))
                .moderationLog(List.of());
    }

    private static byte[] read(MultipartFile file, int index) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationFailedException(
                    List.of(new FieldViolation("images[" + index + "]", "Не вдалося прочитати файл")));
        }
    }

    private void cleanUp(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not delete {} after a failed submission", key, e);
            }
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
