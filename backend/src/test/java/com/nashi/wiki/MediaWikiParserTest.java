package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MediaWikiParserTest {

    @Test
    void wikibaseItemOfAPage() {
        assertEquals("Q1008489", MediaWikiParser.parseWikibaseItem("{\"batchcomplete\":true,\"query\":{\"pages\":["
                + "{\"pageid\":63871,\"ns\":0,\"title\":\"Океан Ельзи\",\"pageprops\":{\"wikibase_item\":\"Q1008489\"}}]}}"));
        assertNull(MediaWikiParser.parseWikibaseItem(
                "{\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Нема\",\"missing\":true}]}}"));
        assertThrows(WikiFetchException.class, () -> MediaWikiParser.parseWikibaseItem(
                "{\"error\":{\"code\":\"maxlag\",\"info\":\"Waiting for a database server.\"}}"));
    }

    @Test
    void introOfARedirectedTitleIsKeyedByTheRequestedTitle() {
        Map<String, String> intros = MediaWikiParser.parseIntros(Fixtures.read("extracts.json"));

        assertTrue(intros.get("Микола Лисенко").startsWith("Микола Віталійович Лисенко"),
                "stress marks are removed: " + intros.get("Микола Лисенко"));
        assertTrue(intros.get("Лисенко Микола Віталійович").startsWith("Микола"));
        assertTrue(intros.get("Океан Ельзи").contains("рок-гурт"));
    }

    @Test
    void stripsGluedIsoDates() {
        // The hidden microformat date of the birth-date template stays glued to the text in the extract.
        String json = "{\"batchcomplete\":true,\"query\":{\"pages\":[{\"pageid\":1,\"ns\":0,"
                + "\"title\":\"Тест\",\"extract\":\"Іван Петрук (нар. 14 травня 1975(1975-05-14) , Мукачево) — співак.\"}]}}";

        assertEquals("Іван Петрук (нар. 14 травня 1975, Мукачево) — співак.", MediaWikiParser.parseIntros(json).get("Тест"));
        assertEquals("нар. 14 травня 1975,", MediaWikiParser.cleanIntro("нар. 14 травня 1975(1975-05-14) ,"));
        assertEquals("нар. 6 січня 1978, Броди", MediaWikiParser.cleanIntro("нар. 6 січня 1978(19780106), Броди"));
        // A date in brackets of its own, after a space, is the author's text and stays.
        assertEquals("дата (1975-05-14) у тексті", MediaWikiParser.cleanIntro("дата (1975-05-14) у тексті"));
    }

    @Test
    void truncatesAtTheLastSentenceThatFits() {
        String text = "Перше речення про гурт. ".repeat(100).trim();

        String cut = MediaWikiParser.truncate(text, 1500);

        assertTrue(cut.endsWith(".…"), cut);
        assertTrue(cut.length() <= 1501, "length " + cut.length());
        assertTrue(cut.length() > 1400, "length " + cut.length());
    }

    @Test
    void truncatesAtAWordWhenNoSentenceEndFits() {
        String text = "слово ".repeat(400).trim();

        String cut = MediaWikiParser.truncate(text, 1500);

        assertTrue(cut.endsWith("слово…"), cut);
        assertTrue(cut.length() <= 1501);
    }

    @Test
    void leavesShortTextAlone() {
        assertEquals("Коротко.", MediaWikiParser.truncate("Коротко.", 1500));
    }

    @Test
    void readsPhotoCreditsWithoutHtml() {
        Map<String, WikiImageCredit> credits = MediaWikiParser.parseImageCredits(Fixtures.read("imageinfo.json"));

        WikiImageCredit band = credits.get("OkeanElzi25.jpg");
        assertEquals("Alexander Khanin", band.getAuthor());
        assertEquals("CC BY-SA 4.0", band.getLicense());
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0", band.getLicenseUrl());
        assertTrue(band.getFilePageUrl().startsWith("https://commons.wikimedia.org/wiki/File:"));

        WikiImageCredit lysenko = credits.get("Лисенко Микола (cropped).jpg");
        assertEquals("Public domain", lysenko.getLicense());
        assertNull(lysenko.getAuthor());
    }

    @Test
    void readsThumbnailUrls() {
        Map<String, String> thumbs = MediaWikiParser.parseThumbUrls(Fixtures.read("imageinfo.json"));

        assertTrue(thumbs.get("OkeanElzi25.jpg").startsWith("https://"));
        assertTrue(thumbs.get("OkeanElzi25.jpg").contains("OkeanElzi25.jpg"));
        assertFalse(thumbs.get("OkeanElzi25.jpg").contains("?"), "tracking parameters are dropped");
        assertNotNull(thumbs.get("Лисенко Микола (cropped).jpg"));
    }

    @Test
    void readsWorkThumbnailsAndCredits() {
        String json = Fixtures.read("imageinfo-works.json");

        Map<String, String> thumbs = MediaWikiParser.parseThumbUrls(json);
        Map<String, WikiImageCredit> credits = MediaWikiParser.parseImageCredits(json);

        assertTrue(thumbs.get("Пимоненко М Весілля в Київській губернії 1891.PNG").endsWith(
                "/1280px-%D0%9F%D0%B8%D0%BC%D0%BE%D0%BD%D0%B5%D0%BD%D0%BA%D0%BE_%D0%9C_%D0%92%D0%B5%D1%81%D1%96"
                        + "%D0%BB%D0%BB%D1%8F_%D0%B2_%D0%9A%D0%B8%D1%97%D0%B2%D1%81%D1%8C%D0%BA%D1%96%D0%B9_%D0%B3"
                        + "%D1%83%D0%B1%D0%B5%D1%80%D0%BD%D1%96%D1%97_1891.PNG"));
        assertEquals("Пимоненко, Николай Корнильевич",
                credits.get("Пимоненко М Весілля в Київській губернії 1891.PNG").getAuthor());
        assertEquals("Ivan Trush, Ukrainian painter, died March 1941",
                credits.get("Ivan Trush - Sunbeam.jpg").getAuthor());
        assertEquals("Public domain", credits.get("Mykola Pymonenko-Brod.jpg").getLicense());
        assertEquals("https://commons.wikimedia.org/wiki/File:Mykola_Pymonenko-Brod.jpg",
                credits.get("Mykola Pymonenko-Brod.jpg").getFilePageUrl());
    }
}
