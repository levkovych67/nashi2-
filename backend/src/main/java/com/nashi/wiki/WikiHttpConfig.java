package com.nashi.wiki;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The one Wikimedia client of the application: {@code nashi.http.user-agent} (shared with the other outgoing HTTP
 * clients) and {@code nashi.wiki.request-gap} (pause between requests, at least one second by default).
 */
@Configuration
public class WikiHttpConfig {

    @Bean
    public PoliteWikiHttp wikiHttp(
            @Value("${nashi.http.user-agent:" + PoliteWikiHttp.DEFAULT_USER_AGENT + "}") String userAgent,
            @Value("${nashi.wiki.request-gap:1s}") Duration requestGap) {
        return new PoliteWikiHttp(userAgent, requestGap);
    }
}
