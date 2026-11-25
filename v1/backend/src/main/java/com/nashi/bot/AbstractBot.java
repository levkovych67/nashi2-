package com.nashi.bot;

import lombok.extern.slf4j.Slf4j; // Changed from CommonsLog
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
public class AbstractBot {

    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_BACKOFF_MS = 1000;

    void execute(SendMessage sendMessage, TelegramClient telegramClient) {
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            log(e);
        }
    }

    // Helper method to log exceptions with full details
    protected void log(Exception e) {
        // SLF4J supports parameterized messages but for full stack trace
        // passing the exception as the last argument is standard.
        log.error("Exception: {}, Cause: {}, Suppressed: {}",
                e.getLocalizedMessage(),
                e.getCause(),
                e.getSuppressed(),
                e);
    }

    public void executeWithRetry(SendMediaGroup sendMediaGroup, TelegramClient telegramClient) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                telegramClient.execute(sendMediaGroup);
                log.info("Media group sent successfully.");
                return;
            } catch (TelegramApiRequestException e) {
                if (e.getErrorCode() == 429) {
                    Integer retryAfter = e.getParameters().getRetryAfter();
                    handleRateLimit(retryAfter);
                } else {
                    log.error("Telegram API error while sending media group, attempt {}: {}", attempt + 1, e.getMessage());
                    return;
                }
            } catch (Exception e) {
                log.error("Unexpected error while sending media group, attempt {}: {}", attempt + 1, e.getMessage(), e);
                return;
            }

            attempt++;
            backoff(attempt);
        }

        log.warn("Failed to send media group after {} attempts.", MAX_RETRIES);
    }

    public void executeWithRetry(SendAudio sendAudio, TelegramClient telegramClient) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                telegramClient.execute(sendAudio);
                log.info("Audio sent successfully.");
                return;
            } catch (TelegramApiRequestException e) {
                if (e.getErrorCode() == 429) {
                    Integer retryAfter = e.getParameters().getRetryAfter();
                    handleRateLimit(retryAfter);
                } else {
                    log.error("Telegram API error while sending audio, attempt {}: {}", attempt + 1, e.getMessage());
                    return;
                }
            } catch (Exception e) {
                log.error("Unexpected error while sending audio, attempt {}: {}", attempt + 1, e.getMessage(), e);
                return;
            }

            attempt++;
            backoff(attempt);
        }

        log.warn("Failed to send audio after {} attempts.", MAX_RETRIES);
    }

    private void handleRateLimit(Integer retryAfter) {
        if (retryAfter != null && retryAfter > 0) {
            try {
                log.warn("Rate limit exceeded. Retrying after {} seconds.", retryAfter);
                Thread.sleep(retryAfter * 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted while waiting to retry.", ex);
            }
        } else {
            log.error("Retry after parameter is null or invalid, proceeding without sleep.");
        }
    }

    private void backoff(int attempt) {
        try {
            long backoffTime = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
            log.info("Waiting for {} milliseconds before retrying...", backoffTime);
            Thread.sleep(backoffTime);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Backoff sleep interrupted.", ex);
        }
    }

    void executeWithRetry(SendMessage sendMessage, TelegramClient telegramClient) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                telegramClient.execute(sendMessage);
                log.info("Message sent successfully.");
                return;
            } catch (TelegramApiRequestException e) {
                log.error("Error while sending message on attempt {}: {}", attempt + 1, e.getMessage());

                if (e.getErrorCode() == 429) {
                    Integer retryAfter = e.getParameters().getRetryAfter();
                    if (retryAfter != null && retryAfter > 0) {
                        handleRateLimit(retryAfter);
                    } else {
                        log.error("Retry after parameter is null or invalid, proceeding without sleep.");
                        return;
                    }
                } else {
                    log.error("Non-retryable error occurred. Stopping attempts.");
                    return;
                }
            } catch (Exception e) {
                log.error("Unexpected error occurred while sending message, attempt {}: {}", attempt + 1, e.getMessage(), e);
                return;
            }

            attempt++;
        }

        log.warn("Failed to send message after {} attempts.", MAX_RETRIES);
    }

    void executeWithRetry(SendPhoto sendPhoto, TelegramClient telegramClient) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                telegramClient.execute(sendPhoto);
                log.info("Photo sent successfully.");
                return;
            } catch (TelegramApiRequestException e) {
                log.error("Error while sending photo on attempt {}: {}", attempt + 1, e.getMessage());

                if (e.getErrorCode() == 429) {
                    Integer retryAfter = e.getParameters().getRetryAfter();
                    if (retryAfter != null && retryAfter > 0) {
                        handleRateLimit(retryAfter);
                    } else {
                        log.error("Retry after parameter is null or invalid, proceeding without sleep.");
                        return;
                    }
                } else {
                    log.error("Non-retryable error occurred. Stopping attempts.");
                    return;
                }
            } catch (Exception e) {
                log.error("Unexpected error occurred while sending photo, attempt {}: {}", attempt + 1, e.getMessage(), e);
                return;
            }

            attempt++;
        }

        log.warn("Failed to send photo after {} attempts.", MAX_RETRIES);
    }
}