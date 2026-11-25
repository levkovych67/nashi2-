package com.nashi.service.impl.artist;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The admin's {@code /hide} and {@code /show} commands. Each returns the reply for the admin chat.
 * <p>
 * Hiding keeps the document and only sets {@link ArtistStatus#HIDDEN}: the public endpoints show APPROVED artists
 * only, and the Wikipedia import still counts the artist as known (by Wikidata id, article title or, for form
 * artists, name), so it is never imported again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistVisibilityService {

    private static final Pattern QID = Pattern.compile("[Qq]\\d+");

    private final ArtistRepository artistRepository;

    /**
     * Only an APPROVED artist is hidden. A PENDING or REJECTED one is left as it is: once HIDDEN, {@link #show} would
     * publish it without moderation.
     */
    public String hide(String argument) {
        Optional<Artist> found = find(argument);
        if (found.isEmpty()) {
            return notFound(argument);
        }
        Artist artist = found.get();
        ArtistStatus status = artist.getStatus();
        if (status == ArtistStatus.PENDING) {
            return "Це заявка на модерації — відхиліть її кнопкою в чаті.";
        }
        if (status == ArtistStatus.REJECTED) {
            return "Заявку відхилено, її й так не видно: " + artist.getName() + " (" + artist.getSlug() + ")";
        }
        if (status == ArtistStatus.APPROVED) {
            log.info("Hiding artist {} ({})", artist.getId(), artist.getSlug());
            artist.setStatus(ArtistStatus.HIDDEN);
            artistRepository.save(artist);
        }
        return "Приховано: " + artist.getName() + " (" + artist.getSlug() + "). Повторно не імпортується.";
    }

    /** Only a HIDDEN artist comes back; a PENDING one still waits for moderation. */
    public String show(String argument) {
        Optional<Artist> found = find(argument);
        if (found.isEmpty()) {
            return notFound(argument);
        }
        Artist artist = found.get();
        if (artist.getStatus() != ArtistStatus.HIDDEN) {
            return "Не приховано: " + artist.getName() + " (" + artist.getSlug() + ")";
        }
        log.info("Showing artist {} ({}) again", artist.getId(), artist.getSlug());
        artist.setStatus(ArtistStatus.APPROVED);
        artistRepository.save(artist);
        return "Знову показано: " + artist.getName();
    }

    /** A Q-id is looked up by Wikidata id first, then as a slug; anything else as a slug. */
    private Optional<Artist> find(String argument) {
        String key = argument == null ? "" : argument.trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        if (QID.matcher(key).matches()) {
            Optional<Artist> byQid = artistRepository.findByWikidataId(key.toUpperCase(Locale.ROOT));
            if (byQid.isPresent()) {
                return byQid;
            }
        }
        return artistRepository.findBySlug(key);
    }

    private static String notFound(String argument) {
        return "Не знайдено: " + (argument == null ? "" : argument.trim());
    }
}
