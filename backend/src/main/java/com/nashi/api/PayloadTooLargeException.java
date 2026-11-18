package com.nashi.api;

import org.springframework.http.HttpStatus;

/** 413 {@code PAYLOAD_TOO_LARGE}: the request as a whole is over the limit. */
public class PayloadTooLargeException extends ApiException {

    static final String DETAIL = "Запит завеликий: зменште кількість або розмір файлів";

    public PayloadTooLargeException() {
        super(DETAIL);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONTENT_TOO_LARGE;
    }

    @Override
    public String code() {
        return "PAYLOAD_TOO_LARGE";
    }
}
