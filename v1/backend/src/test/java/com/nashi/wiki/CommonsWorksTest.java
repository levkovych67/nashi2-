package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CommonsWorksTest {

    private final List<String> urls = new ArrayList<>();

    @Test
    void worksCategoryPrefixes() {
        for (String prefix : List.of("Paintings by ", "Works by ", "Drawings by ", "Sculptures by ", "Prints by ",
                "Artworks by ", "Graphic works by ")) {
            assertTrue(CommonsWorks.isWorksCategory("Category:" + prefix + "Ivan Trush"), prefix);
            assertTrue(CommonsWorks.isWorksCategory(prefix + "Ivan Trush"), prefix + "without the namespace");
        }
        // The name is not checked: the subcategory already sits in the artist's own category.
        assertTrue(CommonsWorks.isWorksCategory("Category:Works by Mykhailo Boichuk"));
    }

    @Test
    void portraitCategoryIsNotWorks() {
        assertFalse(CommonsWorks.isWorksCategory("Category:Portraits of Tetiana Yablonska"));
        assertFalse(CommonsWorks.isWorksCategory("Category:Taras Shevchenko in art"));
        assertFalse(CommonsWorks.isWorksCategory("Category:Photographs of Ivan Trush"));
        // Shevchenko's category on Commons, 2026-11-08
        assertFalse(CommonsWorks.isWorksCategory("Category:Works about Taras Shevchenko"));
        assertFalse(CommonsWorks.isWorksCategory("Category:Literary works by Taras Shevchenko"));
        assertFalse(CommonsWorks.isWorksCategory("Category:Illustrations to works by Taras Shevchenko"));
        assertFalse(CommonsWorks.isWorksCategory("Category:Google Art Project works by Arkhip Kuindzhi"));
        assertFalse(CommonsWorks.isWorksCategory("Category:Grave of Ivan Trush"));
    }

    @Test
    void titleFromFileName() {
        assertEquals("Kateryna Bilokur - Bouquet of flowers (1959)",
                CommonsWorks.workTitle("Kateryna_Bilokur_-_Bouquet_of_flowers_(1959).jpg"));
        assertEquals("Foo", CommonsWorks.workTitle("Foo_(Q12345).jpg"));
        assertEquals("Ivan Trush - Sunbeam", CommonsWorks.workTitle("Ivan Trush - Sunbeam.jpg"));
        assertEquals("Mykola Pymonenko. Meeting", CommonsWorks.workTitle("Mykola Pymonenko. Meeting.jpg"));
        assertEquals("Annunciation (1907-08)", CommonsWorks.workTitle("Annunciation (1907-08).jpg"));
        assertEquals("Landscape", CommonsWorks.workTitle("Landscape (National Art Museum of Ukraine).tif"));
        assertEquals("Ab (Q1)", CommonsWorks.workTitle("Ab_(Q1).png"), "too little would be left");
    }

    @Test
    void listsFilesOfTheWorksSubcategoriesOnly() {
        // Ivan Trush (Q1364775): 3 works with an image on Wikidata; his category holds «Paintings by Ivan Trush»
        // among plaques, a grave and a street.
        CommonsWorks commons = new CommonsWorks(answering(Fixtures.read("commons-subcats.json"),
                Fixtures.read("commons-files.json")));

        List<String> files = commons.files("Ivan Trush (painter)", 12);

        assertEquals(12, files.size());
        assertEquals("Trush Nasturtiums III.jpg", files.get(0));
        assertEquals("Iwan Trusz - Krajobraz z obrzeżami Neapolu.jpg", files.get(11));
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("list=categorymembers"), urls.get(0));
        assertTrue(urls.get(0).contains("cmtype=subcat"), urls.get(0));
        assertTrue(urls.get(0).contains("cmtitle=Category:Ivan Trush (painter)"), urls.get(0));
        assertTrue(urls.get(1).contains("cmtype=file"), urls.get(1));
        assertTrue(urls.get(1).contains("cmtitle=Category:Paintings by Ivan Trush"), urls.get(1));
    }

    @Test
    void capsAtTwelveAcrossSubcategories() {
        CommonsWorks commons = new CommonsWorks(answering(
                members("Category:Paintings by X", "Category:Portraits of X", "Category:Drawings by X",
                        "Category:Prints by X"),
                members("File:A1.jpg", "File:A2.jpg", "File:A3.jpg", "File:A4.jpg", "File:A5.jpg"),
                Fixtures.read("commons-files.json")));

        List<String> files = commons.files("X", 12);

        assertEquals(12, files.size());
        assertEquals(List.of("A1.jpg", "A2.jpg", "A3.jpg", "A4.jpg", "A5.jpg"), files.subList(0, 5));
        assertEquals(List.of("Trush Nasturtiums III.jpg", "Ivan Franko 2.jpg", "Ivan franko by trush.jpg",
                "Ivan Trush - Italian Landscape.jpg", "Ivan Trush - Little pond.jpg", "Ivan Trush - Sunbeam.jpg",
                "Ivan Trush Evening sun.jpg"), files.subList(5, 12));
        // subcategories, Paintings (12 asked), Drawings (the 7 still missing); Prints is never asked
        assertEquals(3, urls.size());
        assertTrue(urls.get(1).contains("cmtitle=Category:Paintings by X") && urls.get(1).contains("cmlimit=12"),
                urls.get(1));
        assertTrue(urls.get(2).contains("cmtitle=Category:Drawings by X") && urls.get(2).contains("cmlimit=7"),
                urls.get(2));
    }

    @Test
    void skipsFilesThatAreNotImagesAndFilesSeenBefore() {
        CommonsWorks commons = new CommonsWorks(answering(
                members("Category:Paintings by X", "Category:Works by X"),
                members("File:A1.jpg", "File:Catalogue.pdf", "File:Interview.ogg", "File:A2.TIF"),
                members("File:A1.jpg", "File:A3.png")));

        assertEquals(List.of("A1.jpg", "A2.TIF", "A3.png"), commons.files("X", 12));
    }

    @Test
    void noRequestWhenNothingIsMissing() {
        CommonsWorks commons = new CommonsWorks(answering());

        assertEquals(List.of(), commons.files("X", 0));
        assertEquals(List.of(), urls);
    }

    @Test
    void categoryWithoutWorksSubcategoriesAsksOnce() {
        CommonsWorks commons = new CommonsWorks(answering(members("Category:Grave of X", "Category:X Street")));

        assertEquals(List.of(), commons.files("X", 12));
        assertEquals(1, urls.size());
    }

    /** Answers the requests in turn, recording the decoded URLs. */
    private Function<String, String> answering(String... answers) {
        List<String> queue = new ArrayList<>(Arrays.asList(answers));
        return url -> {
            urls.add(URLDecoder.decode(url, StandardCharsets.UTF_8));
            assertTrue(url.startsWith("https://commons.wikimedia.org/w/api.php?"), url);
            assertFalse(queue.isEmpty(), "unexpected " + url);
            return queue.remove(0);
        };
    }

    /** A categorymembers answer in the API's shape. */
    private static String members(String... titles) {
        return Arrays.stream(titles)
                .map(title -> "{\"ns\":" + (title.startsWith("File:") ? 6 : 14) + ",\"title\":\"" + title + "\"}")
                .collect(Collectors.joining(",", "{\"batchcomplete\":true,\"query\":{\"categorymembers\":[", "]}}"));
    }
}
