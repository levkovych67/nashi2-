package com.nashi.telegram;

/** A Bot API call failed for good (after retries). */
public class TelegramGatewayException extends RuntimeException {

    public TelegramGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
