package com.nashi.api;

import org.springframework.http.HttpStatus;

/** 503 {@code UNAVAILABLE}: the server cannot take this request right now; the client may try again later. */
public class UnavailableException extends ApiException {

    public UnavailableException(String detail) {
        super(detail);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    @Override
    public String code() {
        return "UNAVAILABLE";
    }
}
