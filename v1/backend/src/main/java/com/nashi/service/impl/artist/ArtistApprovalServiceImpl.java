package com.nashi.service.impl.artist;

import com.nashi.api.exception.AlreadyProcessedException;
import com.nashi.domain.Artist;
import com.nashi.domain.NewsPost;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
import com.nashi.repo.NewsPostRepository;
import com.nashi.service.ArtistApprovalService;
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistApprovalServiceImpl implements ArtistApprovalService {

    private final ArtistRepository artistRepository;
    private final AiRequestService aiRequestService;
    private final S3Service s3Service;
    private final NewsPostRepository newsPostRepository;

    @Override
    @Transactional
    public TelegramNewsPostDTO approveArtist(String artistId) {
        Artist artist = getPendingArtistOrThrow(artistId);

        artist.setStatus(ArtistStatus.APPROVED);
        artistRepository.save(artist);

        return toTelegramDto(Objects.requireNonNull(createNewsPostForArtist(artist)));
    }

    @Override
    @Transactional
    public void rejectArtist(String artistId) {
        Artist artist = getPendingArtistOrThrow(artistId);

        log.info("Rejecting artist: {}. Starting cleanup...", artist.getName());

        cleanupS3Files(artist.getImageKeys());
        cleanupS3Files(artist.getDemoTrackKeys());
        artistRepository.delete(artist);

        log.info("Artist rejected and deleted: {}", artist.getId());
    }

    // --- Private Helpers ---

    /**
     * The buttons act on PENDING artists only. An old button must neither bring back a HIDDEN artist (and post it
     * to the channel) nor delete an APPROVED or HIDDEN one, which the Wikipedia import would then forget.
     */
    private Artist getPendingArtistOrThrow(String id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found: " + id));
        if (artist.getStatus() != ArtistStatus.PENDING) {
            log.info("Ignored approval button for artist {}: status is {}", id, artist.getStatus());
            throw new AlreadyProcessedException(artist.getStatus());
        }
        return artist;
    }

    private void cleanupS3Files(List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                try {
                    s3Service.deleteFile(key);
                } catch (Exception e) {
                    log.error("Failed to delete file from S3 during rejection: " + key, e);
                }
            }
        }
    }

    private NewsPost createNewsPostForArtist(Artist artist) {
        try {
            log.info("Generating news post for {}", artist.getName());

            // 1. Text (AI)
            String smmText = aiRequestService.generateSocialMediaPost(artist);

            // 2. Image (First available)
            String coverImageKey = null;
            if (artist.getImageKeys() != null && !artist.getImageKeys().isEmpty()) {
                coverImageKey = artist.getImageKeys().get(0);
            }

            // 3. NEW: Audio (First available)
            String demoTrackKey = null;
            if (artist.getDemoTrackKeys() != null && !artist.getDemoTrackKeys().isEmpty()) {
                demoTrackKey = artist.getDemoTrackKeys().get(0);
            }

            // 4. Save
            NewsPost newsPost = NewsPost.builder()
                    .content(smmText)
                    .imageKey(coverImageKey)
                    .audioKey(demoTrackKey) // <-- Зберігаємо ключ
                    .artistId(artist.getId())
                    .artistSlug(artist.getSlug())
                    .artistName(artist.getName())
                    .build();

            return newsPostRepository.save(newsPost);

        } catch (Exception e) {
            log.error("Failed to create news post for artist: " + artist.getName(), e);
        }
        return null;
    }

    private TelegramNewsPostDTO toTelegramDto(NewsPost newsPost) {
        // 1. Отримуємо URL картинки
        String imageUrl = null;
        if (newsPost.getImageKey() != null && !newsPost.getImageKey().isBlank()) {
            imageUrl = s3Service.getFileUrl(newsPost.getImageKey());
        }

        // 2. Отримуємо URL аудіо
        String audioUrl = null;
        if (newsPost.getAudioKey() != null && !newsPost.getAudioKey().isBlank()) {
            audioUrl = s3Service.getFileUrl(newsPost.getAudioKey());
        }

        // 3. Будуємо DTO
        return TelegramNewsPostDTO.builder()
                .caption(newsPost.getContent())
                .title(newsPost.getArtistName())
                .imageUrl(imageUrl)
                .audioUrl(audioUrl)
                .build();
    }
}