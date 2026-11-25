package com.nashi.service.impl.artist;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtistVisibilityServiceTest {

    private ArtistRepository repository;
    private ArtistVisibilityService service;

    @BeforeEach
    void setUp() {
        repository = mock(ArtistRepository.class);
        when(repository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(repository.findByWikidataId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Artist.class))).thenAnswer(call -> call.getArgument(0));
        service = new ArtistVisibilityService(repository);
    }

    @Test
    void hideSetsHidden() {
        Artist okean = artist(ArtistStatus.APPROVED);
        when(repository.findBySlug("okean-elzy")).thenReturn(Optional.of(okean));

        String reply = service.hide("okean-elzy");

        assertEquals("Приховано: Океан Ельзи (okean-elzy). Повторно не імпортується.", reply);
        ArgumentCaptor<Artist> saved = ArgumentCaptor.forClass(Artist.class);
        verify(repository).save(saved.capture());
        assertEquals(ArtistStatus.HIDDEN, saved.getValue().getStatus());
        assertEquals("Q1328549", saved.getValue().getWikidataId()); // the document stays, so the import skips it
    }

    @Test
    void hideFindsArtistByQid() {
        Artist okean = artist(ArtistStatus.APPROVED);
        when(repository.findByWikidataId("Q1328549")).thenReturn(Optional.of(okean));

        assertEquals("Приховано: Океан Ельзи (okean-elzy). Повторно не імпортується.", service.hide("q1328549"));
        assertEquals(ArtistStatus.HIDDEN, okean.getStatus());
    }

    @Test
    void hideRefusesPendingArtist() {
        Artist pending = artist(ArtistStatus.PENDING);
        when(repository.findBySlug("okean-elzy")).thenReturn(Optional.of(pending));

        String reply = service.hide("okean-elzy");

        // Hiding it would let /show publish a suggestion nobody moderated.
        assertEquals("Це заявка на модерації — відхиліть її кнопкою в чаті.", reply);
        assertEquals(ArtistStatus.PENDING, pending.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void hideRefusesRejectedArtist() {
        Artist rejected = artist(ArtistStatus.REJECTED);
        when(repository.findBySlug("okean-elzy")).thenReturn(Optional.of(rejected));

        String reply = service.hide("okean-elzy");

        assertEquals("Заявку відхилено, її й так не видно: Океан Ельзи (okean-elzy)", reply);
        assertEquals(ArtistStatus.REJECTED, rejected.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void hideOfAHiddenArtistChangesNothing() {
        Artist hidden = artist(ArtistStatus.HIDDEN);
        when(repository.findBySlug("okean-elzy")).thenReturn(Optional.of(hidden));

        assertEquals("Приховано: Океан Ельзи (okean-elzy). Повторно не імпортується.", service.hide("okean-elzy"));
        verify(repository, never()).save(any());
    }

    @Test
    void showRestoresApproved() {
        Artist okean = artist(ArtistStatus.HIDDEN);
        when(repository.findByWikidataId("Q1328549")).thenReturn(Optional.of(okean));

        String reply = service.show("Q1328549");

        assertEquals("Знову показано: Океан Ельзи", reply);
        ArgumentCaptor<Artist> saved = ArgumentCaptor.forClass(Artist.class);
        verify(repository).save(saved.capture());
        assertEquals(ArtistStatus.APPROVED, saved.getValue().getStatus());
    }

    @Test
    void showIgnoresNonHidden() {
        Artist pending = artist(ArtistStatus.PENDING);
        when(repository.findBySlug("okean-elzy")).thenReturn(Optional.of(pending));

        String reply = service.show("okean-elzy");

        assertEquals("Не приховано: Океан Ельзи (okean-elzy)", reply);
        assertEquals(ArtistStatus.PENDING, pending.getStatus()); // /show never skips moderation
        verify(repository, never()).save(any());
    }

    @Test
    void unknownArgumentReportsNotFound() {
        assertEquals("Не знайдено: nemaie-takoho", service.hide("nemaie-takoho"));
        assertEquals("Не знайдено: Q999", service.show("Q999"));
        verify(repository, never()).save(any());
    }

    private static Artist artist(ArtistStatus status) {
        return Artist.builder()
                .id("a1")
                .name("Океан Ельзи")
                .slug("okean-elzy")
                .wikidataId("Q1328549")
                .status(status)
                .build();
    }
}
