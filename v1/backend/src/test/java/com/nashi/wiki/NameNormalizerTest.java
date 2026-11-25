package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameNormalizerTest {

    @Test
    void ignoresQuotesCaseAndSpaces() {
        assertEquals(NameNormalizer.normalize("«Океан Ельзи»"), NameNormalizer.normalize("  океан   ельзи "));
    }

    @Test
    void treatsAllApostrophesAsOne() {
        assertEquals(NameNormalizer.normalize("Кам’яний"), NameNormalizer.normalize("Кам'яний"));
        assertEquals(NameNormalizer.normalize("Кам`яний"), NameNormalizer.normalize("Камʼяний"));
    }

    @Test
    void keepsNullAsEmpty() {
        assertEquals("", NameNormalizer.normalize(null));
    }
}
