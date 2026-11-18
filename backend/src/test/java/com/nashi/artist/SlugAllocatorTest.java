package com.nashi.artist;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugAllocatorTest {

    @Test
    void allocateReturnsBaseWhenFree() {
        assertThat(SlugAllocator.allocate("Океан Ельзи", s -> false)).isEqualTo("okean-elzy");
    }

    @Test
    void allocateAddsNumericSuffix() {
        Set<String> taken = Set.of("okean-elzy", "okean-elzy-2");

        assertThat(SlugAllocator.allocate("Океан Ельзи", taken::contains)).isEqualTo("okean-elzy-3");
    }
}
