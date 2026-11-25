package com.nashi.service;

import com.nashi.domain.Artist;
import com.nashi.dto.artist.ArtistCreateRequestDTO;
import com.nashi.dto.artist.ArtistDTOMinified;
import com.nashi.dto.artist.ArtistViewDTO;
import com.nashi.enums.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ArtistService {

    /**
     * Processes a new artist suggestion from a public user.
     * * @param requestDto The structured data (JSON).
     * @param images     Optional list of image files.
     * @param demoTracks Optional list of audio files.
     * @throws IllegalArgumentException if validation fails (e.g. invalid dates).
     */
    Artist createSuggestion(ArtistCreateRequestDTO requestDto,
                            List<MultipartFile> images,
                            List<MultipartFile> demoTracks);

    // The public reads below see APPROVED artists only; anything else is "not found".

    /** APPROVED artists, all of Ukraine or one region. */
    Page<ArtistDTOMinified> getAllArtists(Region region, Pageable pageable);

    /** @throws com.nashi.api.exception.ResourceNotFoundException when missing or not APPROVED */
    ArtistViewDTO getArtistById(String id);

    /** @throws com.nashi.api.exception.ResourceNotFoundException when missing or not APPROVED */
    ArtistViewDTO getArtistBySlug(String slug);

    /**
     * Search APPROVED artists by name (case-insensitive partial match).
     * @param query Search query
     * @param pageable Pagination
     */
    Page<ArtistDTOMinified> searchArtists(String query, Pageable pageable);
}
