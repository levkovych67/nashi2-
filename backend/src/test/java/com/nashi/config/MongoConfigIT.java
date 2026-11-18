package com.nashi.config;

import com.mongodb.client.MongoClient;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot applies the customizers when it builds the MongoClient (on top of its MongoClientSettings bean). */
class MongoConfigIT extends MongoIT {

    @Autowired
    private MongoClient client;

    @Test
    void testProfileTimeoutReachesTheRealClient() {
        assertThat(client.getClusterDescription().getClusterSettings().getServerSelectionTimeout(TimeUnit.SECONDS)).isEqualTo(3);
    }
}
