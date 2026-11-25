package com.nashi.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when custom validation fails.
 */
public class ValidationException extends BusinessException {
    
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, cause);
    }
}
