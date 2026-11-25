package com.nashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupConfigGuardTest {

    private static final List<String> SECRET_KEYS = List.of(
            "spring.data.mongodb.uri",
            "spring.ai.openai.api-key",
            "aws.s3.access-key-id",
            "aws.s3.secret-access-key",
            "telegram.bot-token",
            "telegram.admin-chat-id",
            "telegram.channel-id");

    @Test
    void localProfileAcceptsLocalSettings() {
        assertEquals(List.of(), StartupConfigGuard.problems(localEnv()));
    }

    @Test
    void localProfileRejectsRemoteMongo() {
        MockEnvironment env = localEnv()
                .withProperty("spring.data.mongodb.uri", "mongodb+srv://u:Secr3tPw@cluster0.example.net/nashi");

        List<String> problems = StartupConfigGuard.problems(env);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("spring.data.mongodb.uri"));
        assertFalse(problems.get(0).contains("Secr3tPw"));
        assertFalse(problems.get(0).contains("cluster0"));
    }

    @Test
    void localProfileRejectsRemoteMongoHiddenBehindLocalLookingParts() {
        // The driver reads the host after the last '@' of the part before the first '/' or '?'.
        assertEquals(1, StartupConfigGuard.problems(localEnv()
                .withProperty("spring.data.mongodb.uri", "mongodb://localhost@db.example.net:27017/nashi")).size());
        assertEquals(1, StartupConfigGuard.problems(localEnv()
                .withProperty("spring.data.mongodb.uri", "mongodb://localhost:27017,db.example.net:27017/nashi")).size());
        assertEquals(1, StartupConfigGuard.problems(localEnv()
                .withProperty("spring.data.mongodb.uri", "not a uri")).size());
    }

    @Test
    void localProfileAcceptsDockerMongoHost() {
        MockEnvironment env = localEnv().withProperty("spring.data.mongodb.uri", "mongodb://mongo:27017/nashi");

        assertEquals(List.of(), StartupConfigGuard.problems(env));
    }

    @Test
    void localProfileRejectsTelegram() {
        List<String> telegramOn = StartupConfigGuard.problems(localEnv().withProperty("telegram.enabled", "true"));
        assertEquals(1, telegramOn.size());
        assertTrue(telegramOn.get(0).contains("telegram.enabled"));

        // The long-polling starter is on when telegrambots.enabled is missing.
        List<String> starterOn = StartupConfigGuard.problems(withoutKey(localEnv(), "telegrambots.enabled"));
        assertEquals(1, starterOn.size());
        assertTrue(starterOn.get(0).contains("telegrambots.enabled"));
    }

    @Test
    void localProfileRejectsRealLookingKeys() {
        List<String> openAi = StartupConfigGuard.problems(localEnv().withProperty("spring.ai.openai.api-key", "sk-abc123"));
        assertEquals(1, openAi.size());
        assertTrue(openAi.get(0).contains("spring.ai.openai.api-key"));
        assertFalse(openAi.get(0).contains("sk-abc123"));

        List<String> s3 = StartupConfigGuard.problems(localEnv().withProperty("aws.s3.access-key-id", "AKIAXXXXXXXX"));
        assertEquals(1, s3.size());
        assertTrue(s3.get(0).contains("aws.s3.access-key-id"));
        assertFalse(s3.get(0).contains("AKIAXXXXXXXX"));
    }

    @Test
    void productionReportsMissingKeysByName() {
        List<String> problems = StartupConfigGuard.problems(new MockEnvironment());

        assertEquals(SECRET_KEYS, problems.stream().map(p -> p.split(" ")[0]).toList());
    }

    @Test
    void productionAcceptsCompleteConfig() {
        MockEnvironment env = new MockEnvironment();
        SECRET_KEYS.forEach(key -> env.setProperty(key, "set-on-the-server"));

        assertEquals(List.of(), StartupConfigGuard.problems(env));
    }

    @Test
    void postProcessThrowsWithAllProblems() {
        MockEnvironment env = new MockEnvironment();
        List<String> problems = StartupConfigGuard.problems(env);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new StartupConfigGuard().postProcessEnvironment(env, null));

        problems.forEach(p -> assertTrue(e.getMessage().contains(p), "message lists: " + p));
        assertTrue(e.getMessage().contains("NASHI_SECRETS_FILE"));
    }

    @Test
    void repoPropertiesHoldNoSecrets() throws IOException {
        Properties props = load("/application.properties");

        for (String key : SECRET_KEYS) {
            String value = props.getProperty(key);
            // Never put the value in the assertion message: it would land in the test report.
            assertTrue(value == null || value.isBlank(), key + " must not have a value in application.properties");
        }
        String imports = props.getProperty("spring.config.import");
        assertNotNull(imports, "spring.config.import is missing");
        assertTrue(imports.contains("optional:file:${NASHI_SECRETS_FILE:/etc/nashi/nashi-backend.properties}"));
    }

    @Test
    void localPropertiesPassTheGuard() throws IOException {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        load("/application.properties").forEach((k, v) -> env.setProperty((String) k, (String) v));
        load("/application-local.properties").forEach((k, v) -> env.setProperty((String) k, (String) v));

        assertEquals(List.of(), StartupConfigGuard.problems(env));
    }

    @Test
    void guardIsRegisteredAsAnEnvironmentPostProcessor() throws IOException {
        List<String> registered = new ArrayList<>();
        // Only this project's file: the Spring Boot jars ship their own spring.factories.
        for (URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/spring.factories"))) {
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            Properties factories = new Properties();
            try (InputStream in = url.openStream()) {
                factories.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            String entry = factories.getProperty(EnvironmentPostProcessor.class.getName(), "");
            Arrays.stream(entry.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(registered::add);
        }

        assertTrue(registered.contains(StartupConfigGuard.class.getName()),
                "META-INF/spring.factories must register StartupConfigGuard, found: " + registered);
    }

    @Test
    void guardRunsAfterConfigDataIsLoaded() {
        // Before ConfigDataEnvironmentPostProcessor the secrets file is not imported yet and every key looks missing.
        assertTrue(new StartupConfigGuard().getOrder() > ConfigDataEnvironmentPostProcessor.ORDER);
    }

    private static MockEnvironment localEnv() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        return env
                .withProperty("spring.data.mongodb.uri", "mongodb://localhost:27017/nashi")
                .withProperty("telegram.enabled", "false")
                .withProperty("telegrambots.enabled", "false")
                .withProperty("spring.ai.openai.api-key", "local-disabled")
                .withProperty("aws.s3.access-key-id", "local-disabled");
    }

    private static MockEnvironment withoutKey(MockEnvironment env, String key) {
        ((Properties) env.getPropertySources().get(MockPropertySource.MOCK_PROPERTIES_PROPERTY_SOURCE_NAME).getSource())
                .remove(key);
        return env;
    }

    private static Properties load(String resource) throws IOException {
        try (InputStream in = StartupConfigGuardTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return props;
        }
    }
}
