package com.nashi.service;

import com.nashi.dto.TelegramNewsPostDTO;

/** The approve/reject buttons in the admin chat. Both act on PENDING artists only. */
public interface ArtistApprovalService {

    /** @throws com.nashi.api.exception.AlreadyProcessedException when the artist is not PENDING */
    TelegramNewsPostDTO approveArtist(String artistId);

    /** @throws com.nashi.api.exception.AlreadyProcessedException when the artist is not PENDING */
    void rejectArtist(String artistId);
}