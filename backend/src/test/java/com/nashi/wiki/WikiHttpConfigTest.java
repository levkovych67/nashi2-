package com.nashi.wiki;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WikiHttpConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(WikiHttpConfig.class);

    @Test
    void defaultsToTheProjectUserAgentAndOneSecondGap() {
        runner.run(context -> {
            PoliteWikiHttp http = context.getBean(PoliteWikiHttp.class);
            assertThat(http.userAgent()).isEqualTo("NashiBot/2.0 (+https://nashi.example/about)");
            assertThat(http.requestGap()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void readsUserAgentAndGapFromProperties() {
        runner.withPropertyValues("nashi.http.user-agent=NashiBot/2.0 (+https://nashi.org.ua/about)",
                        "nashi.wiki.request-gap=1500ms")
                .run(context -> {
                    PoliteWikiHttp http = context.getBean(PoliteWikiHttp.class);
                    assertThat(http.userAgent()).isEqualTo("NashiBot/2.0 (+https://nashi.org.ua/about)");
                    assertThat(http.requestGap()).isEqualTo(Duration.ofMillis(1500));
                });
    }

    @Test
    void refusesAGapThatIsNegative() {
        runner.withPropertyValues("nashi.wiki.request-gap=-1s")
                .run(context -> assertThat(context).hasFailed());
    }
}
