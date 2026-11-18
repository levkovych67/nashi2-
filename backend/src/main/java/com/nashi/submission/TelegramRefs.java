package com.nashi.submission;

import java.util.List;

/** Where the submission's messages are in the moderation chat. */
public record TelegramRefs(long chatId, Integer threadId, List<Integer> albumMessageIds, Integer controlMessageId) {
}
