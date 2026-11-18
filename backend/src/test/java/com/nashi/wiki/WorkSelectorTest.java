package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSelectorTest {

    private static WorkRow row(String creator, String work, String title, Integer year, String ukTitle,
                               String wikisourceTitle, boolean notable) {
        return new WorkRow(creator, work, title, year, null, ukTitle, wikisourceTitle, notable);
    }

    private static List<String> works(List<WorkRow> rows) {
        return rows.stream().map(WorkRow::work).toList();
    }

    @Test
    void notableFirstThenArticleThenTitleThenYear() {
        List<WorkRow> rows = List.of(
                row("Q1", "Q10", "Пізня", 1900, null, null, false),
                row("Q1", "Q11", "Рання", 1850, null, null, false),
                row("Q1", "Q12", null, 1800, null, null, false),
                row("Q1", "Q14", "Зі статтею", 1960, "Стаття", null, false),
                row("Q1", "Q15", null, null, null, null, true),
                row("Q1", "Q16", "Без року", null, null, null, false),
                row("Q1", "Q9", "Рання двійня", 1850, null, null, false));

        List<WorkRow> selected = WorkSelector.select(rows, 12).get("Q1");

        // Q9 before Q11: the same year, and QIDs compare as numbers, not as text
        assertEquals(List.of("Q15", "Q14", "Q9", "Q11", "Q10", "Q16", "Q12"), works(selected));
    }

    @Test
    void capsAtTwelve() {
        List<WorkRow> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            rows.add(row("Q1", "Q" + (100 + i), "Твір " + i, 1900 + i, null, null, false));
        }

        List<WorkRow> selected = WorkSelector.select(rows, 12).get("Q1");

        assertEquals(12, selected.size());
        assertEquals("Q101", selected.get(0).work());
        assertEquals("Q112", selected.get(11).work());
    }

    @Test
    void keepsWorksWithTheirCreator() {
        List<WorkRow> rows = List.of(
                row("QA", "Q1", "A1", 1901, null, null, false),
                row("QB", "Q2", "B1", 1902, null, null, false),
                row("QA", "Q3", "A2", 1903, null, null, false),
                row("QB", "Q4", "B2", 1904, null, null, false),
                row("QA", "Q5", "A3", 1905, "A3", null, false));

        Map<String, List<WorkRow>> selected = WorkSelector.select(rows, 12);

        assertEquals(List.of("QA", "QB"), List.copyOf(selected.keySet()));
        assertEquals(List.of("Q5", "Q1", "Q3"), works(selected.get("QA")));
        assertEquals(List.of("Q2", "Q4"), works(selected.get("QB")));
        assertTrue(selected.get("QA").stream().allMatch(r -> r.creator().equals("QA")));
        assertTrue(selected.get("QB").stream().allMatch(r -> r.creator().equals("QB")));
    }

    @Test
    void duplicateWorkRowsCollapsed() {
        // A work found as notable (P800) and again as authored (P50); a painting with two images; a work by two people.
        List<WorkRow> rows = List.of(
                row("Q1", "Q10", "Кобзар", 1840, "Кобзар (збірка)", "Кобзар", false),
                row("Q1", "Q10", "Кобзар", 1840, "Кобзар (збірка)", "Кобзар", true),
                new WorkRow("Q1", "Q11", "Картина", 1850, "a.jpg", null, null, false),
                new WorkRow("Q1", "Q11", "Картина", 1850, "b.jpg", null, null, false),
                row("Q2", "Q10", "Кобзар", 1840, "Кобзар (збірка)", "Кобзар", false));

        Map<String, List<WorkRow>> selected = WorkSelector.select(rows, 12);

        assertEquals(List.of("Q10", "Q11"), works(selected.get("Q1")));
        assertTrue(selected.get("Q1").get(0).notable(), "the better row of a work is kept");
        assertEquals("a.jpg", selected.get("Q1").get(1).imageFile(), "the first image in answer order is kept");
        assertEquals(List.of("Q10"), works(selected.get("Q2")), "a co-authored work stays with each author");
    }

    @Test
    void notableWorksOfTheRecordedWritersComeFirst() {
        Map<String, List<WorkRow>> selected =
                WorkSelector.select(SparqlParser.parseWorks(Fixtures.read("sparql-works-written.json")), 12);

        List<WorkRow> shevchenko = selected.get("Q134958");
        assertEquals(12, shevchenko.size(), "14 recorded works, capped");
        // Кобзар 1840, Сон 1865, Гайдамаки without a year
        assertEquals(List.of("Q2658157", "Q9335228", "Q18551805"), works(shevchenko.subList(0, 3)));
        assertTrue(shevchenko.subList(0, 3).stream().allMatch(WorkRow::notable));
        assertEquals(1, shevchenko.stream().filter(r -> r.work().equals("Q2658157")).count());

        List<WorkRow> lesya = selected.get("Q298033");
        assertEquals("Q11816946", lesya.get(0).work(), "Лісова пісня");
        assertTrue(lesya.get(0).notable());
        assertEquals(8, lesya.size());
    }
}
