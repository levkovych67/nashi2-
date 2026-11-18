package com.nashi.media;

import com.nashi.support.MongoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3FileStorageIT extends MongoIT {

    @Autowired
    FileStorage storage;

    @Autowired
    S3Properties props;

    @Test
    void putGetCopyDelete() {
        String id = UUID.randomUUID().toString();
        String from = "submissions/" + id + "/1.jpg";
        String to = "artists/" + id + "/1.jpg";
        byte[] bytes = "photo-bytes".getBytes(StandardCharsets.UTF_8);
        try {
            storage.put(from, bytes, "image/jpeg");
            assertThat(storage.get(from)).isEqualTo(bytes);

            storage.copy(from, to);
            assertThat(storage.get(to)).isEqualTo(bytes);

            storage.delete(from);
            // get() of a missing key throws NoSuchKeyException (documented on FileStorage.get)
            assertThatThrownBy(() -> storage.get(from)).isInstanceOf(NoSuchKeyException.class);
            assertThat(storage.get(to)).isEqualTo(bytes);
        } finally {
            storage.delete(from);
            storage.delete(to);
        }
    }

    @Test
    void publicUrlUsesBaseUrl() {
        assertThat(storage.publicUrl("artists/a1/1.jpg")).isEqualTo(props.publicBaseUrl() + "/artists/a1/1.jpg");
    }
}
