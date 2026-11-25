package com.nashi.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when file upload or processing fails.
 */
public class FileUploadException extends BusinessException {
    
    public FileUploadException(String message) {
        super("FILE_UPLOAD_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public FileUploadException(String message, Throwable cause) {
        super("FILE_UPLOAD_ERROR", message, HttpStatus.BAD_REQUEST, cause);
    }
}
