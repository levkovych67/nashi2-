package com.nashi.api;

/**
 * One invalid field in a {@code VALIDATION_FAILED} (or {@code UNSUPPORTED_MEDIA_TYPE}) problem: {@code errors[]} in
 * the response. {@code field} is the request field path ({@code summary}, {@code images[2]}); {@code message} is
 * Ukrainian and safe to show the user.
 */
public record FieldViolation(String field, String message) {
}
