package com.nashi.telegram;

/** Who may press the moderation buttons. Only configuration here; the callback handler applies it. */
public enum ApproverPolicy {
    CHAT_ADMINS,
    ANY_MEMBER
}
