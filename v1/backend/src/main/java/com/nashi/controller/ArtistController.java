package com.nashi.controller;


import com.nashi.domain.Artist;
import com.nashi.dto.artist.ArtistCreateRequestDTO;
import com.nashi.dto.artist.ArtistDTOMinified;
import com.nashi.dto.artist.ArtistViewDTO;
import com.nashi.enums.Region;
import com.nashi.service.ArtistService;
import com.nashi.service.impl.TelegramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;
    private final TelegramService telegramService;

    /**
     * Endpoint to suggest a new artist.
     * Consumes multipart/form-data to handle both JSON data and Files.
     *
     * @param requestDto The JSON object containing artist details (Name, Bio, Members, etc.)
     * @param images     List of image files (optional)
     * @param demoTracks List of audio files (optional)
     * @return 201 Created if successful
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> suggestArtist(
            @RequestPart("data") @Valid ArtistCreateRequestDTO requestDto,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "demoTracks", required = false) List<MultipartFile> demoTracks
    ) {
        log.info("Received suggestion for artist: {}", requestDto.getName());

        Artist suggestion = artistService.createSuggestion(requestDto, images, demoTracks);
        telegramService.sendArtistForApproval(suggestion);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Artist suggestion submitted successfully. It is now PENDING review.");
    }


    /** APPROVED artists only. */
    @GetMapping
    public ResponseEntity<Page<ArtistDTOMinified>> getArtists(
            @RequestParam(value = "region", required = false) Region region,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ArtistDTOMinified> artists = artistService.getAllArtists(region, pageable);
        return ResponseEntity.ok(artists);
    }

    /** 404 unless the artist is APPROVED. */
    @GetMapping("/{id}")
    public ResponseEntity<ArtistViewDTO> getArtistById(@PathVariable String id) {
        ArtistViewDTO artist = artistService.getArtistById(id);
        return ResponseEntity.ok(artist);
    }

    /** 404 unless the artist is APPROVED. */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ArtistViewDTO> getArtistBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(artistService.getArtistBySlug(slug));
    }

    /**
     * Search endpoint for artists
     * Searches APPROVED artists by name (case-insensitive partial match)
     *
     * @param query Search query string
     * @param pageable Pagination parameters
     * @return Page of matching artists
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ArtistDTOMinified>> searchArtists(
            @RequestParam(value = "q", required = true) String query,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC, size = 20) Pageable pageable
    ) {
        log.info("Search request - query: '{}'", query);
        Page<ArtistDTOMinified> results = artistService.searchArtists(query, pageable);
        log.info("Found {} results for query '{}'", results.getTotalElements(), query);
        return ResponseEntity.ok(results);
    }
}
