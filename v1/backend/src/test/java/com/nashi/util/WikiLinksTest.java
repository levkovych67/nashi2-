package com.nashi.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WikiLinksTest {

    @Test
    void wikisourceUrlEncodesTheTitleLikeAnArticle() {
        assertEquals("https://uk.wikisource.org/wiki/Contra_spem_spero%21", WikiLinks.wikisourceUrl("Contra spem spero!"));
        // A subpage keeps working with an encoded slash (checked live on uk.wikisource.org, 2026-11-08); a question
        // mark must not start a query string.
        assertEquals("https://uk.wikisource.org/wiki/A_%281876%29%2FB", WikiLinks.wikisourceUrl("A (1876)/B"));
        assertEquals("https://uk.wikisource.org/wiki/Why%3F", WikiLinks.wikisourceUrl("Why?"));
        assertEquals("https://uk.wikisource.org/wiki/%D0%9C%D1%96%D0%B9_%D1%88%D0%BB%D1%8F%D1%85",
                WikiLinks.wikisourceUrl("Мій шлях"));
    }

    @Test
    void noTitleNoLink() {
        assertNull(WikiLinks.wikisourceUrl(null));
        assertNull(WikiLinks.wikisourceUrl(" "));
    }
}
