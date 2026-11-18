package com.nashi.submission;

import com.nashi.api.UnsupportedMediaException;
import com.nashi.media.FileStorage;
import com.nashi.media.ImageProcessor;
import com.nashi.media.ProcessedImage;
import com.nashi.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The failure paths that leave nothing behind in storage; the happy path is in SubmissionControllerIT. */
class SubmissionServiceTest {

    private final SubmissionValidator validator = mock(SubmissionValidator.class);
    private final ImageProcessor processor = mock(ImageProcessor.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final DuplicateDetector duplicates = mock(DuplicateDetector.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private SubmissionService service;

    private final List<MockMultipartFile> files = List.of(
            new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1}),
            new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[] {2}));

    @BeforeEach
    void setUp() {
        when(validator.validate(any(), any())).thenReturn(List.of());
        when(duplicates.findDuplicate(anyString())).thenReturn(Optional.empty());
        when(processor.process(any(), anyString())).thenReturn(new ProcessedImage(new byte[] {9}, new byte[] {8}, 1, 1));
        service = new SubmissionService(new RateLimiter(Clock.systemUTC()), validator, processor, storage, repository,
                duplicates, new IpHasher("s"), events, Clock.systemUTC());
    }

    private void submit() {
        service.submit(SubmissionRequests.valid(), List.copyOf(files), "198.51.100.1");
    }

    @Test
    void unsupportedSecondPhotoUploadsNothing() {
        when(processor.process(eq(new byte[] {2}), eq("images[1]"))).thenThrow(new UnsupportedMediaException("images[1]"));

        assertThatThrownBy(this::submit).isInstanceOf(UnsupportedMediaException.class);

        verify(storage, never()).put(anyString(), any(), anyString());
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void failedUploadDeletesKeysAlreadyUploaded() {
        doNothing().doNothing().doThrow(new IllegalStateException("s3 down")).when(storage)
                .put(anyString(), any(), anyString());

        assertThatThrownBy(this::submit).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> put = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(storage, org.mockito.Mockito.times(3)).put(put.capture(), any(), anyString());
        verify(storage, org.mockito.Mockito.times(2)).delete(deleted.capture());
        assertThat(deleted.getAllValues()).containsExactlyElementsOf(put.getAllValues().subList(0, 2));
        assertThat(deleted.getAllValues().get(0)).matches("submissions/[0-9a-f]{24}/0\\.jpg");
        assertThat(deleted.getAllValues().get(1)).matches("submissions/[0-9a-f]{24}/0-thumb\\.jpg");
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void failedSaveDeletesAllUploadedKeys() {
        when(repository.save(any())).thenThrow(new IllegalStateException("mongo down"));

        assertThatThrownBy(this::submit).isInstanceOf(IllegalStateException.class);

        verify(storage, org.mockito.Mockito.times(4)).delete(anyString());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
