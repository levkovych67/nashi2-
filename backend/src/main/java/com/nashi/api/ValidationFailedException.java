package com.nashi.api;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 400 {@code VALIDATION_FAILED} with one entry in {@code errors[]} per invalid field. */
public class ValidationFailedException extends ApiException {

    private final List<FieldViolation> errors;

    public ValidationFailedException(List<FieldViolation> errors) {
        super(detailFor(errors));
        this.errors = List.copyOf(errors);
    }

    /** The problem detail for these field errors, e.g. «Перевірте поля: summary, images[2]». */
    static String detailFor(List<FieldViolation> errors) {
        List<String> fields = errors.stream().map(FieldViolation::field).distinct().toList();
        return fields.isEmpty() ? "Некоректний запит" : "Перевірте поля: " + String.join(", ", fields);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String code() {
        return "VALIDATION_FAILED";
    }

    @Override
    public List<FieldViolation> errors() {
        return errors;
    }
}
