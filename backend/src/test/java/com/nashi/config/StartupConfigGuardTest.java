package com.nashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
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

    /** Keys production must get from the secrets file, in the order the guard reports them. */
    private static final List<String> PRODUCTION_KEYS = List.of(
            "spring.mongodb.uri",
            "nashi.s3.bucket",
            "nashi.s3.access-key-id",
            "nashi.s3.secret-access-key",
            "nashi.s3.region",
            "nashi.s3.public-base-url",
            "nashi.telegram.bot-token",
            "nashi.telegram.moderation.chat-id",
            "nashi.security.ip-hash-secret",
            "nashi.cors.allowed-origins",
            "nashi.site-base-url");

    @Test
    void prodRequiresAllSecrets() {
        List<String> problems = StartupConfigGuard.problems(new MockEnvironment());

        assertEquals(PRODUCTION_KEYS, problems.stream().map(p -> p.split(" ")[0]).toList());
    }

    @Test
    void prodAcceptsCompleteConfig() {
        assertEquals(List.of(), StartupConfigGuard.problems(productionEnv()));
    }

    @Test
    void prodTreatsBlankAsMissing() {
        List<String> problems = StartupConfigGuard.problems(productionEnv().withProperty("nashi.s3.bucket", "  "));

        assertEquals(List.of("nashi.s3.bucket is not set"), problems);
    }

    @Test
    void localAcceptsLocalSettings() {
        assertEquals(List.of(), StartupConfigGuard.problems(localEnv("local")));
    }

    @Test
    void localRejectsRemoteMongo() {
        MockEnvironment env = localEnv("local")
                .withProperty("spring.mongodb.uri", "mongodb+srv://u:Secr3tPw@cluster0.example.net/nashi_v2");

        List<String> problems = StartupConfigGuard.problems(env);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).startsWith("spring.mongodb.uri "));
        assertFalse(problems.get(0).contains("Secr3tPw"));
        assertFalse(problems.get(0).contains("cluster0"));
    }

    @Test
    void localRejectsRemoteMongoHiddenBehindLocalLookingParts() {
        // The driver reads the host after the last '@' of the part before the first '/' or '?'.
        for (String uri : List.of(
                "mongodb://localhost@db.example.net:27017/nashi_v2",
                "mongodb://localhost:27017,db.example.net:27017/nashi_v2",
                "mongodb://localhost.example.net:27017/nashi_v2",
                "not a uri",
                "")) {
            List<String> problems = StartupConfigGuard.problems(localEnv("local").withProperty("spring.mongodb.uri", uri));
            assertEquals(1, problems.size(), "one problem for: " + uri);
            assertTrue(problems.get(0).startsWith("spring.mongodb.uri "));
        }
    }

    @Test
    void localAcceptsDockerAndTestcontainersMongoHosts() {
        for (String uri : List.of(
                "mongodb://mongo:27017/nashi_v2",
                "mongodb://127.0.0.1:27018/nashi_v2",
                "mongodb://host.docker.internal:27017/nashi_v2",
                "mongodb://localhost:55123/nashi_v2")) {
            assertEquals(List.of(), StartupConfigGuard.problems(localEnv("local").withProperty("spring.mongodb.uri", uri)),
                    "no problems for: " + uri);
        }
    }

    @Test
    void localRejectsTelegramPolling() {
        List<String> on = StartupConfigGuard.problems(localEnv("local").withProperty("nashi.telegram.polling-enabled", "true"));
        assertEquals(List.of("nashi.telegram.polling-enabled must be false under the local and test profiles"), on);

        // Missing is not off: the switch must be written down.
        List<String> missing = StartupConfigGuard.problems(withoutKey(localEnv("local"), "nashi.telegram.polling-enabled"));
        assertEquals(1, missing.size());
        assertTrue(missing.get(0).startsWith("nashi.telegram.polling-enabled "));
    }

    @Test
    void localRejectsTelegramSending() {
        List<String> problems = StartupConfigGuard.problems(localEnv("local").withProperty("nashi.telegram.sending-enabled", "true"));

        assertEquals(List.of("nashi.telegram.sending-enabled must be false under the local and test profiles"), problems);
    }

    @Test
    void localRejectsRealS3Endpoint() {
        // An empty endpoint means the SDK default, i.e. real AWS S3.
        for (String endpoint : List.of(
                "",
                "https://s3.eu-north-1.amazonaws.com",
                "https://localhost:9000",
                "http://localhost.example.net:9000",
                "http://minio.example.net",
                "not a url")) {
            List<String> problems = StartupConfigGuard.problems(localEnv("local").withProperty("nashi.s3.endpoint", endpoint));
            assertEquals(1, problems.size(), "one problem for: '" + endpoint + "'");
            assertTrue(problems.get(0).startsWith("nashi.s3.endpoint "));
        }
        List<String> missing = StartupConfigGuard.problems(withoutKey(localEnv("local"), "nashi.s3.endpoint"));
        assertEquals(1, missing.size());
        assertTrue(missing.get(0).startsWith("nashi.s3.endpoint "));
    }

    @Test
    void localAcceptsLocalS3Endpoints() {
        for (String endpoint : List.of("http://localhost:9000", "http://127.0.0.1:9000", "http://minio:9000", "http://localhost:32771/")) {
            assertEquals(List.of(), StartupConfigGuard.problems(localEnv("local").withProperty("nashi.s3.endpoint", endpoint)),
                    "no problems for: " + endpoint);
        }
    }

    @Test
    void localRejectsRemoteMigrationSources() {
        List<String> remoteMongo = StartupConfigGuard.problems(localEnv("local")
                .withProperty("nashi.migration.v1.mongo-uri", "mongodb+srv://u:Secr3tPw@cluster0.example.net/nashi"));
        assertEquals(1, remoteMongo.size());
        assertTrue(remoteMongo.get(0).startsWith("nashi.migration.v1.mongo-uri "));
        assertFalse(remoteMongo.get(0).contains("Secr3tPw"));

        for (String endpoint : List.of("https://s3.eu-north-1.amazonaws.com", "http://minio.example.net", "not a url")) {
            List<String> problems = StartupConfigGuard.problems(localEnv("test")
                    .withProperty("nashi.migration.v1.s3-endpoint", endpoint));
            assertEquals(1, problems.size(), "one problem for: " + endpoint);
            assertTrue(problems.get(0).startsWith("nashi.migration.v1.s3-endpoint "));
        }
        // A bucket without an endpoint would read real AWS S3
        List<String> bucketOnly = StartupConfigGuard.problems(localEnv("local")
                .withProperty("nashi.migration.v1.s3-bucket", "nashi-v1"));
        assertEquals(1, bucketOnly.size());
        assertTrue(bucketOnly.get(0).startsWith("nashi.migration.v1.s3-endpoint "));
    }

    @Test
    void localAcceptsLocalOrUnsetMigrationSources() {
        assertEquals(List.of(), StartupConfigGuard.problems(localEnv("local")));
        assertEquals(List.of(), StartupConfigGuard.problems(localEnv("local")
                .withProperty("nashi.migration.v1.mongo-uri", "mongodb://localhost:27017/nashi")
                .withProperty("nashi.migration.v1.s3-bucket", "nashi-v1")
                .withProperty("nashi.migration.v1.s3-endpoint", "http://localhost:9000")));
    }

    @Test
    void productionDoesNotNeedMigrationSettings() {
        assertEquals(List.of(), StartupConfigGuard.problems(productionEnv()));
    }

    @Test
    void testProfileHasSameRulesAsLocal() {
        MockEnvironment safe = new MockEnvironment();
        safe.setActiveProfiles("test");
        safe.withProperty("spring.mongodb.uri", "mongodb://localhost:27017/nashi_v2")
                .withProperty("nashi.telegram.polling-enabled", "false")
                .withProperty("nashi.telegram.sending-enabled", "false")
                .withProperty("nashi.s3.endpoint", "http://localhost:9000");

        assertEquals(List.of(), StartupConfigGuard.problems(safe));

        List<String> unsafe = StartupConfigGuard.problems(localEnv("test")
                .withProperty("spring.mongodb.uri", "mongodb+srv://cluster0.example.net/nashi_v2")
                .withProperty("nashi.telegram.polling-enabled", "true")
                .withProperty("nashi.telegram.sending-enabled", "true")
                .withProperty("nashi.s3.endpoint", ""));
        assertEquals(List.of("spring.mongodb.uri", "nashi.telegram.polling-enabled", "nashi.telegram.sending-enabled",
                "nashi.s3.endpoint"), unsafe.stream().map(p -> p.split(" ")[0]).toList());
    }

    @Test
    void messagesNeverContainValues() {
        String secret = "abc123";
        MockEnvironment local = localEnv("local")
                .withProperty("spring.mongodb.uri", "mongodb://user:" + secret + "@" + secret + ".example.net/nashi_v2")
                .withProperty("nashi.telegram.polling-enabled", secret)
                .withProperty("nashi.telegram.sending-enabled", secret)
                .withProperty("nashi.s3.endpoint", "https://" + secret + ".s3.amazonaws.com");
        MockEnvironment prod = new MockEnvironment()
                .withProperty("nashi.s3.access-key-id", secret)
                .withProperty("nashi.telegram.bot-token", secret);

        List<String> problems = new ArrayList<>(StartupConfigGuard.problems(local));
        problems.addAll(StartupConfigGuard.problems(prod));

        assertEquals(4 + PRODUCTION_KEYS.size() - 2, problems.size());
        // Never put the value in an assertion message: it would land in the test report.
        problems.forEach(p -> assertFalse(p.contains(secret), "a problem message quotes a value"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new StartupConfigGuard().postProcessEnvironment(local, null));
        assertFalse(e.getMessage().contains(secret), "the startup error quotes a value");
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
    void postProcessPassesWhenThereAreNoProblems() {
        new StartupConfigGuard().postProcessEnvironment(productionEnv(), null);
    }

    @Test
    void repoPropertiesHoldNoProductionValues() throws IOException {
        Properties props = load("/application.properties");

        for (String key : PRODUCTION_KEYS) {
            // Never put the value in the assertion message: it would land in the test report.
            assertFalse(props.containsKey(key), key + " must come from the secrets file, not application.properties");
        }
        String imports = props.getProperty("spring.config.import");
        assertNotNull(imports, "spring.config.import is missing");
        assertTrue(imports.contains("optional:file:${NASHI_SECRETS_FILE:/etc/nashi-v2/backend.properties}"));
    }

    @Test
    void localPropertiesPassTheGuard() throws IOException {
        assertEquals(List.of(), StartupConfigGuard.problems(envFromFiles("local")));
    }

    @Test
    void testPropertiesPassTheGuard() throws IOException {
        assertEquals(List.of(), StartupConfigGuard.problems(envFromFiles("test")));
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
            // Boot 4 key; the Boot 3 key org.springframework.boot.env.EnvironmentPostProcessor is deprecated.
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

    private static MockEnvironment productionEnv() {
        MockEnvironment env = new MockEnvironment();
        PRODUCTION_KEYS.forEach(key -> env.setProperty(key, "set-on-the-server"));
        return env;
    }

    private static MockEnvironment localEnv(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env
                .withProperty("spring.mongodb.uri", "mongodb://localhost:27017/nashi_v2")
                .withProperty("nashi.telegram.polling-enabled", "false")
                .withProperty("nashi.telegram.sending-enabled", "false")
                .withProperty("nashi.s3.endpoint", "http://localhost:9000");
    }

    private static MockEnvironment envFromFiles(String profile) throws IOException {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        load("/application.properties").forEach((k, v) -> env.setProperty((String) k, (String) v));
        load("/application-" + profile + ".properties").forEach((k, v) -> env.setProperty((String) k, (String) v));
        return env;
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
