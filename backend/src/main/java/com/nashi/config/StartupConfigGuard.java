package com.nashi.config;

import com.mongodb.ConnectionString;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stops startup, before any bean reaches a database, bot or bucket, when the configuration is incomplete
 * (production without its secrets file) or unsafe (the {@code local} or {@code test} profile pointing at a live
 * service). Runs after config data, including the imported secrets file, is loaded, so it sees only static
 * properties: values added later, such as a test's {@code @DynamicPropertySource}, are not checked.
 * Messages name the key and the rule, never the value. Registered in {@code META-INF/spring.factories}.
 */
public class StartupConfigGuard implements EnvironmentPostProcessor, Ordered {

    static final String MONGO_URI = "spring.mongodb.uri";
    static final String S3_ENDPOINT = "nashi.s3.endpoint";

    /** Keys production reads from the secrets file. */
    private static final List<String> PRODUCTION_KEYS = List.of(
            MONGO_URI,
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

    /** Profiles that must never reach a live service. */
    private static final Profiles SAFE_PROFILES = Profiles.of("local", "test");

    /**
     * Must be written as false under the safe profiles: polling would steal the production bot's updates,
     * sending would post into the real moderation chat.
     */
    private static final List<String> SAFE_OFF_SWITCHES = List.of(
            "nashi.telegram.polling-enabled",
            "nashi.telegram.sending-enabled");

    private static final Set<String> LOCAL_MONGO_HOSTS = Set.of("localhost", "127.0.0.1", "mongo", "host.docker.internal");
    private static final Set<String> LOCAL_S3_HOSTS = Set.of("localhost", "127.0.0.1", "minio");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Nashi will not start:\n  - " + String.join("\n  - ", problems)
                    + "\nProduction reads its secrets from the file named by NASHI_SECRETS_FILE"
                    + " (default /etc/nashi-v2/backend.properties). Local runs need SPRING_PROFILES_ACTIVE=local.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static List<String> problems(ConfigurableEnvironment env) {
        return env.acceptsProfiles(SAFE_PROFILES) ? safeProfileProblems(env) : productionProblems(env);
    }

    private static List<String> productionProblems(ConfigurableEnvironment env) {
        return PRODUCTION_KEYS.stream()
                .filter(key -> isBlank(env.getProperty(key)))
                .map(key -> key + " is not set")
                .toList();
    }

    private static List<String> safeProfileProblems(ConfigurableEnvironment env) {
        List<String> problems = new ArrayList<>();
        if (!isLocalMongo(env.getProperty(MONGO_URI))) {
            problems.add(MONGO_URI + " must be a mongodb:// URI whose hosts are localhost, 127.0.0.1, mongo"
                    + " or host.docker.internal under the local and test profiles");
        }
        for (String key : SAFE_OFF_SWITCHES) {
            String value = env.getProperty(key);
            if (value == null || !"false".equalsIgnoreCase(value.trim())) {
                problems.add(key + " must be false under the local and test profiles");
            }
        }
        if (!isLocalS3(env.getProperty(S3_ENDPOINT))) {
            problems.add(S3_ENDPOINT + " must be an http:// URL whose host is localhost, 127.0.0.1 or minio"
                    + " under the local and test profiles (empty means real AWS S3)");
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

    /** True only for plain-http MinIO on this machine or the compose network; any other endpoint may be real S3. */
    private static boolean isLocalS3(String endpoint) {
        if (isBlank(endpoint)) {
            return false;
        }
        try {
            URI uri = new URI(endpoint.trim());
            return "http".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && uri.getHost() != null
                    && LOCAL_S3_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            // Unparsable is never local; the message would quote the value.
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
