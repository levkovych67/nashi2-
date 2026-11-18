package com.nashi.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nashi.http.HttpFetchException;
import com.nashi.http.PoliteHttp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * GETs a JSON object from a public API (Deezer, iTunes) politely: the project User-Agent
 * ({@code nashi.http.user-agent}), at most one request per second per host, {@code Retry-After} honoured.
 */
@Component
public class HttpJson {

    public static final String DEFAULT_USER_AGENT = "NashiBot/2.0 (+https://nashi.example/about)";

    private final PoliteHttp http;

    @Autowired
    public HttpJson(@Value("${nashi.http.user-agent:" + DEFAULT_USER_AGENT + "}") String userAgent) {
        this(new PoliteHttp(userAgent, Duration.ofSeconds(1)));
    }

    public HttpJson(PoliteHttp http) {
        this.http = http;
    }

    /** @throws HttpFetchException when the call fails or the body is not a JSON object */
    public JsonObject get(String url) {
        String body = http.get(url, "application/json");
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new HttpFetchException("not a JSON object from " + java.net.URI.create(url).getHost(), e);
        }
    }
}
