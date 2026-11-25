package com.nashi.wiki;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WikiImportConfig {

    @Bean
    public CandidateSourceFactory wikiCandidateSourceFactory() {
        return () -> new WikiCollector(new PoliteWikiHttp(), new MusicBrainzClient());
    }
}
