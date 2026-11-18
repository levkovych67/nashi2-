package com.nashi.support;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class of every {@code *IT}: the whole application on a random port, profile {@code test}, against one MongoDB 8
 * and one MinIO container. Inject {@code RestTestClient} for HTTP calls.
 * <p>
 * The containers are singletons, started once per JVM and removed by Testcontainers when it exits; Spring's cached
 * context is shared by all ITs and keeps pointing at them. (A JUnit {@code @Container} field would stop the
 * containers after each IT class while the cached context still used them.) Tests share the database and bucket, so
 * each test creates its own data.
 * <p>
 * The {@code test} profile's static values pass {@code StartupConfigGuard}; the container addresses below are added
 * after the guard has run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureRestTestClient
public abstract class MongoIT {

    public static final String DATABASE = "nashi_v2";
    public static final String BUCKET = "nashi-v2-test";

    protected static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8"));

    /**
     * MinIO no longer publishes images to Docker Hub; pgsty/minio is the community build of the same server
     * (same entrypoint, same API). The bucket is not created here: the S3 client that creates it comes with storage.
     */
    protected static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z").asCompatibleSubstituteFor("minio/minio"));

    static {
        Startables.deepStart(MONGO, MINIO).join();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl(DATABASE));
        registry.add("nashi.s3.endpoint", MINIO::getS3URL);
        registry.add("nashi.s3.access-key-id", MINIO::getUserName);
        registry.add("nashi.s3.secret-access-key", MINIO::getPassword);
        registry.add("nashi.s3.bucket", () -> BUCKET);
        registry.add("nashi.s3.public-base-url", () -> MINIO.getS3URL() + "/" + BUCKET);
    }
}
