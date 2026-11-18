package com.nashi.wiki;

import com.nashi.telegram.Html;
import com.nashi.telegram.TelegramGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WikiImportConfig {

    /** A fresh collector per run; all Wikimedia requests go through the shared polite client. */
    @Bean
    public CandidateSourceFactory wikiCandidateSourceFactory(
            WikiHttp wikiHttp,
            @Value("${nashi.http.user-agent:" + PoliteWikiHttp.DEFAULT_USER_AGENT + "}") String userAgent) {
        return () -> new WikiCollector(wikiHttp, new MusicBrainzClient(userAgent));
    }

    /** The import's report goes to the moderation group as plain text. */
    @Bean
    public AdminNotifier wikiAdminNotifier(TelegramGateway gateway) {
        return text -> gateway.sendText(Html.escape(text));
    }
}
