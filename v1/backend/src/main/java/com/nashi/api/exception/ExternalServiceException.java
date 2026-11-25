package com.nashi.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when external service calls fail (AI, S3, Telegram, etc.)
 */
public class ExternalServiceException extends BusinessException {
    
    public ExternalServiceException(String serviceName, String message) {
        super(
            "EXTERNAL_SERVICE_ERROR",
            String.format("%s service error: %s", serviceName, message),
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(
            "EXTERNAL_SERVICE_ERROR",
            String.format("%s service error: %s", serviceName, message),
            HttpStatus.SERVICE_UNAVAILABLE,
            cause
        );
    }
}
