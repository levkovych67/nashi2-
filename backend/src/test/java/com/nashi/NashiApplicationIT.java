package com.nashi;

import com.nashi.config.NashiProperties;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The application context MongoIT starts: containers wired in, test-profile switches applied. */
class NashiApplicationIT extends MongoIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private NashiProperties properties;

    @Test
    void mongoIsTheContainer() {
        assertThat(mongo.getDb().getName()).isEqualTo(DATABASE);
        assertThat(context.getEnvironment().getProperty("spring.mongodb.uri")).isEqualTo(MONGO.getReplicaSetUrl(DATABASE));
    }

    @Test
    void auditingFillsTimestamps() {
        Instant before = Instant.now();

        AuditProbe saved = mongo.save(new AuditProbe());

        assertThat(saved.createdAt).isNotNull().isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(saved.updatedAt).isNotNull();
        mongo.remove(saved);
    }

    @Test
    void minioIsReachableAtTheConfiguredEndpoint() throws Exception {
        String endpoint = context.getEnvironment().getProperty("nashi.s3.endpoint");
        assertThat(endpoint).isEqualTo(MINIO.getS3URL());

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> live = client.send(
                    HttpRequest.newBuilder(URI.create(endpoint + "/minio/health/live")).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(live.statusCode()).isEqualTo(200);
        }
    }

    @Test
    void testProfileSwitchesAreApplied() {
        assertThat(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isFalse();
        assertThat(context.getEnvironment().getProperty("nashi.telegram.polling-enabled")).isEqualTo("false");
        assertThat(context.getEnvironment().getProperty("nashi.telegram.sending-enabled")).isEqualTo("false");
        assertThat(properties.cors().allowedOrigins()).isEqualTo(List.of("http://localhost:5173"));
        assertThat(properties.audio().fragmentSeconds()).isEqualTo(15);
    }

    @Test
    void namedExecutorsExist() {
        assertThat(context.containsBean("telegramExecutor")).isTrue();
        assertThat(context.containsBean("jobsExecutor")).isTrue();
    }

    @Document("it_audit_probe")
    static class AuditProbe {
        @Id
        String id;
        @CreatedDate
        Instant createdAt;
        @LastModifiedDate
        Instant updatedAt;
    }
}
