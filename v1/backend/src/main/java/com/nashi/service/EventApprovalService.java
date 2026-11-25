package com.nashi.service;

import com.nashi.dto.TelegramNewsPostDTO;

/** The approve/reject buttons in the admin chat. Both act on PENDING events only. */
public interface EventApprovalService {

    /** @throws com.nashi.api.exception.AlreadyProcessedException when the event is not PENDING */
    TelegramNewsPostDTO approveEvent(String eventId);

    /** @throws com.nashi.api.exception.AlreadyProcessedException when the event is not PENDING */
    void rejectEvent(String eventId);
}
