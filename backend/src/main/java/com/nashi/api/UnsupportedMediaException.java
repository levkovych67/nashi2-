package com.nashi.api;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 415 {@code UNSUPPORTED_MEDIA_TYPE} for one uploaded photo (the only upload in the public API, contract 2.10);
 * {@code errors[]} names its field ({@code images[2]}) with a message listing the accepted formats.
 */
public class UnsupportedMediaException extends ApiException {

    private final String field;

    public UnsupportedMediaException(String field) {
        super("Формат файлу не підтримується");
        this.field = field;
    }

    public String field() {
        return field;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
    }

    @Override
    public String code() {
        return "UNSUPPORTED_MEDIA_TYPE";
    }

    @Override
    public List<FieldViolation> errors() {
        return List.of(new FieldViolation(field, "Підтримуються лише JPEG, PNG і WebP"));
    }
}
