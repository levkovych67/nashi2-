package com.nashi.service.impl.artist;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.api.exception.ExternalServiceException;
import com.nashi.api.exception.FileUploadException;
import com.nashi.api.exception.ResourceNotFoundException;
import com.nashi.api.exception.ValidationException;
import com.nashi.domain.Artist;
import com.nashi.domain.Member;
import com.nashi.domain.SocialLink;
import com.nashi.dto.artist.AiTagsResponse;
import com.nashi.dto.artist.ArtistCreateRequestDTO;
import com.nashi.dto.artist.ArtistDTOMinified;
import com.nashi.dto.artist.ArtistViewDTO;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.Region;
import com.nashi.repo.ArtistRepository;
import com.nashi.service.ArtistService;
import com.nashi.service.DataValidationService;
import com.nashi.service.GeoCoordinateService;
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import com.nashi.util.ArtistFacts;
import com.nashi.util.StringUtils;
import com.nashi.util.WikiLinks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;
    private final S3Service s3Service;
    private final AiRequestService aiRequestService;
    private final ObjectMapper objectMapper;
    private final DataValidationService dataValidationService;
    private final GeoCoordinateService geoCoordinateService;

    @Override
    @Transactional
    public Artist createSuggestion(ArtistCreateRequestDTO dto,
                                   List<MultipartFile> images,
                                   List<MultipartFile> demoTracks) {
        log.info("Processing suggestion for artist: {}", dto.getName());

        validateYears(dto);
        // Before any upload or AI call: a taken slug would fail the save on the unique slug index.
        String slug = freeSlug(dto.getName());

        List<String> uploadedImageKeys = uploadFiles(dataValidationService.validateImages(images), "artists/images");
        List<String> uploadedAudioKeys = uploadFiles(dataValidationService.validateAudio(demoTracks), "artists/audio");

        AiTagsResponse aiTagsResponse = buildTags(dto);

        // Check for coordinate collision and adjust if necessary
        GeoJsonPoint adjustedLocation = geoCoordinateService.adjustCoordinatesIfCollision(
                dto.getLatitude(),
                dto.getLongitude()
        );

        Artist artist = Artist.builder()
                .name(dto.getName())
                .slug(slug)
                .biography(dto.getBiography())
                .pressRelease(dto.getPressRelease())
                .style(dto.getStyle())
                .region(dto.getRegion())
                .labels(dto.getLabels())
                .city(dto.getCity())
                // Timeline
                .foundationYear(dto.getFoundationYear())
                .disbandYear(dto.getDisbandYear())

                // Private/Moderation Data
                .contactEmail(dto.getContactEmail())
                .status(ArtistStatus.PENDING) // Always PENDING for public suggestions

                // Geo Data: Use adjusted coordinates to avoid map pin collisions
                .location(adjustedLocation)

                // S3 Keys
                .imageKeys(uploadedImageKeys)
                .demoTrackKeys(uploadedAudioKeys)

                // Nested Objects
                .members(mapMembers(dto.getMembers()))
                .socialLinks(mapSocialLinks(dto.getSocialLinks()))
                .musicTags(new HashSet<>(aiTagsResponse.musicTags()))
                .secondaryTags(new HashSet<>(aiTagsResponse.secondaryTags()))
                .build();

        Artist savedArtist = artistRepository.save(artist);
        log.info("Artist saved successfully with ID: {}", savedArtist.getId());
        return savedArtist;
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /** The name's slug ("artist" when the name has no slug letters), with -2, -3, … appended until it is free. */
    private String freeSlug(String name) {
        String base = name == null ? "" : StringUtils.toSlug(name);
        if (base.isBlank()) {
            base = "artist";
        }
        String slug = base;
        for (int n = 2; artistRepository.existsBySlug(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private void validateYears(ArtistCreateRequestDTO dto) {
        if (dto.getDisbandYear() != null && dto.getFoundationYear() != null) {
            if (dto.getDisbandYear() < dto.getFoundationYear()) {
                throw new ValidationException("Disband year cannot be earlier than foundation year");
            }
        }
    }

    private List<String> uploadFiles(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        return files.stream()
                .filter(f -> !f.isEmpty()) // Skip empty file parts
                .map(file -> s3Service.uploadFile(file, folder))
                .filter(Objects::nonNull) // Ensure we don't get nulls if upload fails
                .collect(Collectors.toList());
    }

    private List<Member> mapMembers(List<ArtistCreateRequestDTO.MemberDTO> dtos) {
        if (dtos == null) return Collections.emptyList();

        return dtos.stream()
                .map(d -> Member.builder()
                        .firstName(d.getFirstName())
                        .lastName(d.getLastName())
                        .city(d.getCity())
                        .role(d.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    private List<SocialLink> mapSocialLinks(List<ArtistCreateRequestDTO.SocialLinkDTO> dtos) {
        if (dtos == null) return Collections.emptyList();

        return dtos.stream()
                .map(d -> SocialLink.builder()
                        .platform(d.getPlatform())
                        .url(d.getUrl())
                        .build())
                .collect(Collectors.toList());
    }

    private ArtistDTOMinified toMinifiedDto(Artist artist) {
        if (artist == null) {
            return null;
        }

        List<String> fullImageUrls = java.util.Optional.ofNullable(artist.getImageKeys()).orElse(java.util.List.of()).stream()
                .map(e -> e.contains("artists/images/") ? s3Service.getFileUrl(e) : e)
                .toList();

        // 2. Будуємо DTO
        return ArtistDTOMinified.builder()
                .id(artist.getId())
                .name(artist.getName())
                .city(artist.getCity())
                .region(artist.getRegion())
                .categories(ArtistFacts.categoriesOf(artist))
                .latitude(artist.getLocation() != null ? artist.getLocation().getY() : null)
                .longitude(artist.getLocation() != null ? artist.getLocation().getX() : null)
                .slug(artist.getSlug()) // Важливо для посилань на фронтенді
                .imageUrls(fullImageUrls)
                .build();
    }

    /** Public list: APPROVED artists only, optionally in one region. */
    @Override
    public Page<ArtistDTOMinified> getAllArtists(Region region, Pageable pageable) {
        Page<Artist> artistsPage = (region != null)
                ? artistRepository.findByRegionAndStatus(region, ArtistStatus.APPROVED, pageable)
                : artistRepository.findByStatus(ArtistStatus.APPROVED, pageable);
        return artistsPage.map(this::toMinifiedDto);
    }

    /** Public page: an artist that is not APPROVED looks exactly like a missing one. */
    @Override
    public ArtistViewDTO getArtistById(String id) {
        Artist artist = artistRepository.findById(id)
                .filter(ArtistServiceImpl::isPublic)
                .orElseThrow(() -> new ResourceNotFoundException("Artist", id));

        return toViewDto(artist);
    }

    private static boolean isPublic(Artist artist) {
        return artist.getStatus() == ArtistStatus.APPROVED;
    }

    private ArtistViewDTO toViewDto(Artist artist) {
        // 1. Convert S3 keys to full URLs
        List<String> imageUrls = java.util.Optional.ofNullable(artist.getImageKeys()).orElse(java.util.List.of()).stream()
                .map(e -> e.contains("artists/") ? s3Service.getFileUrl(e) : e)
                .toList();

        List<String> demoTrackUrls = java.util.Optional.ofNullable(artist.getDemoTrackKeys()).orElse(java.util.List.of()).stream()
                .map(e -> e.contains("artists/") ? s3Service.getFileUrl(e) : e)
                .toList();

        // 2. Extract Coordinates safely
        Double lat = null;
        Double lon = null;
        if (artist.getLocation() != null) {
            lat = artist.getLocation().getY();
            lon = artist.getLocation().getX();
        }

        // 3. Map nested objects (Members & Socials)
        List<ArtistViewDTO.MemberDTO> memberDTOs = java.util.Optional.ofNullable(artist.getMembers()).orElse(java.util.List.of()).stream()
                .map(m -> new ArtistViewDTO.MemberDTO(m.getFirstName(), m.getLastName(), m.getCity(), m.getRole()))
                .toList();

        List<ArtistViewDTO.SocialLinkDTO> socialDTOs = java.util.Optional.ofNullable(artist.getSocialLinks()).orElse(java.util.List.of()).stream()
                .map(s -> new ArtistViewDTO.SocialLinkDTO(s.getPlatform(), s.getUrl()))
                .toList();

        // 4. Build Final DTO
        return ArtistViewDTO.builder()
                .id(artist.getId())
                .name(artist.getName())
                .slug(artist.getSlug())
                .biography(artist.getBiography())
                .pressRelease(artist.getPressRelease())
                .style(artist.getStyle())
                .region(artist.getRegion())
                .labels(artist.getLabels())
                .categories(ArtistFacts.categoriesOf(artist))
                .genres(ArtistFacts.genresOf(artist))
                .foundationYear(artist.getFoundationYear())
                .disbandYear(artist.getDisbandYear())
                .birthDate(artist.getBirthDate())
                .deathDate(artist.getDeathDate())
                .musicTags(artist.getMusicTags())
                .secondaryTags(artist.getSecondaryTags())
                .city(artist.getCity())
                .birthPlace(artist.getBirthPlace())
                .locationBasis(artist.getLocationBasis())
                // Mapped Data
                .imageUrls(imageUrls)
                .demoTrackUrls(demoTrackUrls)
                .latitude(lat)
                .longitude(lon)
                .geohash(artist.getGeohash())
                .members(memberDTOs)
                .socialLinks(socialDTOs)
                .works(java.util.Optional.ofNullable(artist.getWorks()).orElse(java.util.List.of()))
                .source(artist.getSource())
                .wikiUrl(WikiLinks.articleUrl(artist.getWikiTitle()))
                .wikidataUrl(WikiLinks.wikidataUrl(artist.getWikidataId()))
                .imageCredit(artist.getImageCredit())

                .createdAt(artist.getCreatedAt())
                .updatedAt(artist.getUpdatedAt())
                .build();
    }

    @Override
    public ArtistViewDTO getArtistBySlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .filter(ArtistServiceImpl::isPublic)
                .orElseThrow(() -> new ResourceNotFoundException("Artist with slug '" + slug + "' not found"));
        return toViewDto(artist);
    }

    /** Public search by name: APPROVED artists only. */
    @Override
    public Page<ArtistDTOMinified> searchArtists(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            log.warn("Empty search query provided, returning all approved artists");
            return artistRepository.findByStatus(ArtistStatus.APPROVED, pageable).map(this::toMinifiedDto);
        }

        String trimmedQuery = query.trim();
        log.info("Searching approved artists with query: '{}'", trimmedQuery);

        Page<Artist> results = artistRepository.findByNameContainingIgnoreCaseAndStatus(
                trimmedQuery,
                ArtistStatus.APPROVED,
                pageable
        );

        log.debug("Found {} artists matching query '{}'", results.getTotalElements(), trimmedQuery);

        return results.map(this::toMinifiedDto);
    }

    private AiTagsResponse buildTags(ArtistCreateRequestDTO dto) {
        if (dto.getBiography() != null && !dto.getBiography().isBlank()) {
            try {
                log.info("Requesting AI tags for artist: {}", dto.getName());

                // Отримуємо JSON рядок від вашого сервісу
                String jsonResponse = aiRequestService.generateArtistTags(
                        dto.getName(),
                        dto.getStyle(),
                        dto.getBiography()
                );

                // Перевіряємо, чи AI щось повернув (ваш сервіс повертає "" при помилці)
                if (jsonResponse != null && !jsonResponse.isBlank()) {
                    // Парсимо JSON у Java об'єкт
                    AiTagsResponse aiTags = objectMapper.readValue(jsonResponse, AiTagsResponse.class);
                    log.info("AI Tags applied: Music={}, Vibe={}", aiTags.musicTags(), aiTags.secondaryTags());
                    return aiTags;
                } else {
                    log.warn("AI returned empty tags response for {}", dto.getName());
                }
            } catch (Exception e) {
                log.error("Failed to parse or apply AI tags", e);
            }
        }
        return new AiTagsResponse(new ArrayList<>(), new ArrayList<>());
    }
}
