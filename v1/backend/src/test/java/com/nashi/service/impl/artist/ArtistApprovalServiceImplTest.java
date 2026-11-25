package com.nashi.service.impl.artist;

import com.nashi.api.exception.AlreadyProcessedException;
import com.nashi.domain.Artist;
import com.nashi.domain.NewsPost;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
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

class ArtistApprovalServiceImplTest {

    private ArtistRepository artistRepository;
    private AiRequestService aiRequestService;
    private S3Service s3Service;
    private NewsPostRepository newsPostRepository;
    private ArtistApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistRepository.class);
        aiRequestService = mock(AiRequestService.class);
        s3Service = mock(S3Service.class);
        newsPostRepository = mock(NewsPostRepository.class);
        service = new ArtistApprovalServiceImpl(artistRepository, aiRequestService, s3Service, newsPostRepository);
    }

    @Test
    void approveIgnoresHiddenArtist() {
        Artist hidden = artist(ArtistStatus.HIDDEN);
        when(artistRepository.findById("a1")).thenReturn(Optional.of(hidden));

        AlreadyProcessedException e = assertThrows(AlreadyProcessedException.class, () -> service.approveArtist("a1"));

        assertEquals("Заявку вже оброблено (статус: HIDDEN)", e.getMessage());
        assertEquals(ArtistStatus.HIDDEN, hidden.getStatus());
        verify(artistRepository, never()).save(any());
        verifyNoInteractions(aiRequestService, newsPostRepository);
    }

    @Test
    void approveIgnoresApprovedArtist() {
        when(artistRepository.findById("a1")).thenReturn(Optional.of(artist(ArtistStatus.APPROVED)));

        AlreadyProcessedException e = assertThrows(AlreadyProcessedException.class, () -> service.approveArtist("a1"));

        assertEquals("Заявку вже оброблено (статус: APPROVED)", e.getMessage());
        verify(artistRepository, never()).save(any());
        verifyNoInteractions(aiRequestService, newsPostRepository);
    }

    @Test
    void rejectIgnoresNonPendingArtist() {
        for (ArtistStatus status : List.of(ArtistStatus.APPROVED, ArtistStatus.HIDDEN, ArtistStatus.REJECTED)) {
            when(artistRepository.findById("a1")).thenReturn(Optional.of(artist(status)));

            AlreadyProcessedException e = assertThrows(AlreadyProcessedException.class, () -> service.rejectArtist("a1"));

            assertEquals("Заявку вже оброблено (статус: " + status + ")", e.getMessage());
        }
        verify(artistRepository, never()).delete(any());
        verifyNoInteractions(s3Service);
    }

    @Test
    void approvePendingArtistPublishesIt() {
        Artist pending = artist(ArtistStatus.PENDING);
        when(artistRepository.findById("a1")).thenReturn(Optional.of(pending));
        when(aiRequestService.generateSocialMediaPost(pending)).thenReturn("Новий гурт на мапі");
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(call -> call.getArgument(0));

        TelegramNewsPostDTO post = service.approveArtist("a1");

        assertEquals(ArtistStatus.APPROVED, pending.getStatus());
        verify(artistRepository).save(pending);
        assertEquals("Новий гурт на мапі", post.getCaption());
    }

    @Test
    void rejectPendingArtistDeletesItAndItsFiles() {
        Artist pending = artist(ArtistStatus.PENDING);
        when(artistRepository.findById("a1")).thenReturn(Optional.of(pending));

        service.rejectArtist("a1");

        verify(s3Service).deleteFile("artists/images/a.jpg");
        verify(s3Service).deleteFile("artists/audio/a.mp3");
        verify(artistRepository).delete(pending);
    }

    private static Artist artist(ArtistStatus status) {
        return Artist.builder()
                .id("a1")
                .name("Новий гурт")
                .slug("novyi-hurt")
                .status(status)
                .imageKeys(List.of("artists/images/a.jpg"))
                .demoTrackKeys(List.of("artists/audio/a.mp3"))
                .build();
    }
}
