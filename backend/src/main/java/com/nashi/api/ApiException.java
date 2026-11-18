package com.nashi.api;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * An error the API reports to the client as an RFC 9457 problem with a stable {@link #code()}
 * ({@code docs/02-contracts.md} section 1). The message is the problem's {@code detail}: Ukrainian and safe to show
 * the user, so it never carries internal details. {@link ProblemDetailsAdvice} renders it.
 */
public abstract class ApiException extends RuntimeException {

    protected ApiException(String detail) {
        super(detail);
    }

    public abstract HttpStatus status();

    public abstract String code();

    /** Field errors for {@code errors[]}; empty when the problem is not about particular fields. */
    public List<FieldViolation> errors() {
        return List.of();
    }
}
