package com.nashi.api;

import org.springframework.http.HttpStatus;

/**
 * 404 {@code NOT_FOUND}. {@code what} names the missing thing for logs and tests ({@code "musician"}); the response
 * detail stays generic.
 */
public class NotFoundException extends ApiException {

    private final String what;

    public NotFoundException(String what) {
        super("Нічого не знайдено за цим запитом");
        this.what = what;
    }

    public String what() {
        return what;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }
}
