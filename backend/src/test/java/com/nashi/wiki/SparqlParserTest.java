package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparqlParserTest {

    @Test
    void readsIdsAndArticleTitles() {
        List<WikiCandidate> candidates = SparqlParser.parseCandidates(Fixtures.read("sparql-list.json"), CandidateGroup.MUSIC);

        assertEquals(List.of(
                new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.MUSIC),
                new WikiCandidate("Q172425", "Лисенко Микола Віталійович", CandidateGroup.MUSIC)), candidates);
    }

    @Test
    void parsesIds() {
        // The writer tree minus columnist and correspondent, as the live endpoint answered it (trimmed).
        List<String> ids = SparqlParser.parseIds(Fixtures.read("sparql-occupations.json"), "occ");

        assertEquals(28, ids.size());
        assertEquals(List.of("Q152182", "Q175301", "Q186370", "Q214917"), ids.subList(0, 4));
        assertTrue(ids.containsAll(List.of("Q36180", "Q49757", "Q822146", "Q12144794")), ids.toString());
        assertFalse(ids.contains("Q1086863"));
        assertFalse(ids.contains("Q3589290"));
    }

    @Test
    void parseIdsSkipsRowsWithoutTheVariable() {
        assertEquals(List.of(), SparqlParser.parseIds(Fixtures.read("sparql-list.json"), "occ"));
        assertEquals(List.of("Q1008489", "Q172425"), SparqlParser.parseIds(Fixtures.read("sparql-list.json"), "item"));
    }

    @Test
    void parsesVisualWorks() {
        // Pymonenko (Q2601651) and Murashko (Q4307907); Bilokur (Q4082561) was asked too and has no such works.
        List<WorkRow> rows = SparqlParser.parseWorks(Fixtures.read("sparql-works-visual.json"));

        assertEquals(13, rows.size());
        assertTrue(rows.stream().noneMatch(r -> r.creator().equals("Q4082561")));
        assertEquals(new WorkRow("Q2601651", "Q123614066", "Весілля в Київській губернії", 1891,
                "Пимоненко М Весілля в Київській губернії 1891.PNG", "Весілля в Київській губернії", null, false),
                work(rows, "Q123614066"));
        assertEquals(new WorkRow("Q4307907", "Q124338217", "Портрет Адріана Прахова", 1904,
                "Adrian Prakhov by Murashko (1904).jpg", null, null, false), work(rows, "Q124338217"));
        assertEquals(new WorkRow("Q2601651", "Q112066266", "Matchmakers", 1882, "Mykola Pymonenko-Svaty.jpg",
                null, null, false), work(rows, "Q112066266"), "an English label when there is no Ukrainian one");
        assertNull(work(rows, "Q112066285").year());
        assertEquals(List.of("Mikołaj K. Pimonienko - Study of a young boy in a straw hat - M.Ob.908 - National "
                        + "Museum in Warsaw.jpg", "Pymonenko A boy in a straw hat.jpg"),
                rows.stream().filter(r -> r.work().equals("Q25261971")).map(WorkRow::imageFile).toList(),
                "a work with two images comes as two rows");
    }

    @Test
    void parsesWrittenWorksWithNotableAndWikisource() {
        List<WorkRow> rows = SparqlParser.parseWorks(Fixtures.read("sparql-works-written.json"));

        assertEquals(26, rows.size());
        assertEquals(List.of(
                        new WorkRow("Q134958", "Q2658157", "Кобзар", 1840, null, "Кобзар (збірка)", "Кобзар", true),
                        new WorkRow("Q134958", "Q2658157", "Кобзар", 1840, null, "Кобзар (збірка)", "Кобзар", false)),
                rows.stream().filter(r -> r.work().equals("Q2658157")).toList(),
                "a notable work also found as authored comes twice");
        assertEquals(new WorkRow("Q298033", "Q131380149", "Місячна легенда", 1893, null, null, "Місячна легенда",
                false), work(rows, "Q131380149"), "only a text on Wikisource");
        assertEquals("Кобзарь (1876)/Том 2/Ляхам", work(rows, "Q9208105").wikisourceTitle());
        assertEquals(new WorkRow("Q298033", "Q113708658", "Давня весна", 1894, null, "Давня весна", null, false),
                work(rows, "Q113708658"), "only an article");
        assertEquals(4, rows.stream().filter(WorkRow::notable).count());
        assertTrue(rows.stream().allMatch(r -> r.imageFile() == null));
    }

    @Test
    void qidLabelIsNoTitle() {
        // The label service answers with the QID when the work has no label in uk or en.
        WorkRow selfPortrait = work(SparqlParser.parseWorks(Fixtures.read("sparql-works-visual.json")), "Q111334155");

        assertNull(selfPortrait.title());
        assertEquals("Mykola Pymonenko-Avtoportret.jpg", selfPortrait.imageFile());
        assertEquals(1886, selfPortrait.year());
    }

    private static WorkRow work(List<WorkRow> rows, String qid) {
        return rows.stream().filter(r -> r.work().equals(qid)).findFirst().orElseThrow();
    }
}
