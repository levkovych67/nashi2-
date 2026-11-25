package com.nashi.config;

import com.mongodb.ConnectionString;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stops startup, before any bean reaches a database, bot or API, when the configuration is incomplete
 * (production without its secrets file) or unsafe (the {@code local} profile pointing at a live service).
 * Runs after config data, including the imported secrets file, is loaded. Messages name the key and the rule,
 * never the value. Registered in {@code META-INF/spring.factories}.
 */
public class StartupConfigGuard implements EnvironmentPostProcessor, Ordered {

    static final String LOCAL_PROFILE = "local";
    static final String LOCAL_PLACEHOLDER = "local-disabled";

    private static final String MONGO_URI = "spring.data.mongodb.uri";

    /** Keys production reads from the secrets file. */
    private static final List<String> REQUIRED_KEYS = List.of(
            MONGO_URI,
            "spring.ai.openai.api-key",
            "aws.s3.access-key-id",
            "aws.s3.secret-access-key",
            "telegram.bot-token",
            "telegram.admin-chat-id",
            "telegram.channel-id");

    /** Under the local profile these must be false: one stops outgoing messages, the other long polling. */
    private static final List<String> LOCAL_OFF_SWITCHES = List.of("telegram.enabled", "telegrambots.enabled");

    /** Under the local profile these may only hold the placeholder. */
    private static final List<String> LOCAL_PLACEHOLDER_KEYS = List.of(
            "spring.ai.openai.api-key",
            "aws.s3.access-key-id",
            "aws.s3.secret-access-key");

    private static final Set<String> LOCAL_MONGO_HOSTS = Set.of("localhost", "127.0.0.1", "mongo", "host.docker.internal");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Nashi will not start:\n  - " + String.join("\n  - ", problems)
                    + "\nProduction reads its secrets from the file named by NASHI_SECRETS_FILE"
                    + " (default /etc/nashi/nashi-backend.properties). Local runs need SPRING_PROFILES_ACTIVE=local.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static List<String> problems(ConfigurableEnvironment env) {
        return env.acceptsProfiles(Profiles.of(LOCAL_PROFILE)) ? localProblems(env) : productionProblems(env);
    }

    private static List<String> productionProblems(ConfigurableEnvironment env) {
        return REQUIRED_KEYS.stream()
                .filter(key -> isBlank(env.getProperty(key)))
                .map(key -> key + " is not set")
                .toList();
    }

    private static List<String> localProblems(ConfigurableEnvironment env) {
        List<String> problems = new ArrayList<>();
        if (!isLocalMongo(env.getProperty(MONGO_URI))) {
            problems.add(MONGO_URI + " must be a mongodb:// URI whose hosts are localhost, 127.0.0.1, mongo"
                    + " or host.docker.internal under the local profile");
        }
        for (String key : LOCAL_OFF_SWITCHES) {
            String value = env.getProperty(key);
            if (value == null || !"false".equalsIgnoreCase(value.trim())) {
                problems.add(key + " must be false under the local profile");
            }
        }
        for (String key : LOCAL_PLACEHOLDER_KEYS) {
            String value = env.getProperty(key);
            if (!isBlank(value) && !LOCAL_PLACEHOLDER.equals(value.trim())) {
                problems.add(key + " must be " + LOCAL_PLACEHOLDER + " under the local profile (no real keys locally)");
            }
        }
        return problems;
    }

    /** True only when every host the driver would contact is local. mongodb+srv always resolves to remote hosts. */
    private static boolean isLocalMongo(String uri) {
        if (isBlank(uri) || !uri.trim().startsWith("mongodb://")) {
            return false;
        }
        try {
            List<String> hosts = new ConnectionString(uri.trim()).getHosts();
            return !hosts.isEmpty() && hosts.stream().allMatch(h -> LOCAL_MONGO_HOSTS.contains(hostName(h)));
        } catch (RuntimeException e) {
            // Unparsable is never local. The driver's message may quote the URI, so it is dropped.
            return false;
        }
    }

    private static String hostName(String hostAndPort) {
        int colon = hostAndPort.indexOf(':');
        return (colon < 0 ? hostAndPort : hostAndPort.substring(0, colon)).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
