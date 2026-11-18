package com.nashi.submission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpHasherTest {

    @Test
    void sameIpSameHashDifferentSecretDifferentHash() {
        var a = new IpHasher("secret-a");
        assertThat(a.hash("203.0.113.7")).isEqualTo(a.hash("203.0.113.7"));
        assertThat(a.hash("203.0.113.7")).isNotEqualTo(a.hash("203.0.113.8"));
        assertThat(a.hash("203.0.113.7")).isNotEqualTo(new IpHasher("secret-b").hash("203.0.113.7"));
    }

    @Test
    void hashNeverContainsIp() {
        String hash = new IpHasher("secret-a").hash("203.0.113.7");
        assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("203").doesNotContain("113");
    }
}
