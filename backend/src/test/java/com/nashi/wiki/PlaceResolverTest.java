package com.nashi.wiki;

import com.nashi.geo.Region;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PlaceResolverTest {

    private final Map<String, WikiEntity> world = new HashMap<>(
            WikidataEntityParser.parse(Fixtures.read("entities-places.json")));
    private final List<String> fetched = new ArrayList<>();

    private PlaceResolver resolver() {
        return new PlaceResolver((Collection<String> ids) -> {
            fetched.addAll(ids);
            return ids.stream().filter(world::containsKey).collect(Collectors.toMap(id -> id, world::get));
        });
    }

    @Test
    void resolvesAVillageToItsOblastThroughTheDistrict() {
        WikiPlace hrynky = resolver().resolve(List.of("Q4149698")).get("Q4149698");

        assertEquals(Region.POLTAVA, hrynky.region());
        assertEquals("Гриньки", hrynky.labelUk());
        assertEquals(49.4575, hrynky.lat(), 0.001);
    }

    @Test
    void resolvesACityThatIsItselfARegion() {
        world.put("Q1899", TestEntities.place("Q1899", "Київ", 50.45, 30.52, "Q212"));

        assertEquals(Region.KYIV_CITY, resolver().resolve(List.of("Q1899")).get("Q1899").region());
    }

    @Test
    void leavesOutPlacesOutsideUkraine() {
        world.put("Q649", TestEntities.place("Q649", "Москва", 55.75, 37.62, "Q159"));
        world.put("Q159", TestEntities.place("Q159", "Росія", 60.0, 90.0));

        assertFalse(resolver().resolve(List.of("Q649")).containsKey("Q649"));
    }

    @Test
    void stopsOnALoopOfParents() {
        world.put("QA", TestEntities.place("QA", "А", 50.0, 30.0, "QB"));
        world.put("QB", TestEntities.place("QB", "Б", 50.0, 30.0, "QA"));

        assertTrue(resolver().resolve(List.of("QA")).isEmpty());
    }

    @Test
    void leavesOutPlacesWithoutCoordinates() {
        world.put("QX", TestEntities.place("QX", "Без координат", null, null, "Q169501"));

        assertTrue(resolver().resolve(List.of("QX")).isEmpty());
    }

    @Test
    void fetchesEveryEntityOnce() {
        PlaceResolver resolver = resolver();
        resolver.resolve(List.of("Q4149698", "Q36036", "Q2415330"));
        resolver.resolve(List.of("Q4149698", "Q36036"));

        Map<String, Long> counts = fetched.stream().collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        counts.forEach((id, count) -> assertEquals(1L, count, id));
        assertFalse(fetched.contains("Q169501"), "an oblast in the table needs no fetch");
    }

    @Test
    void labelsAnyFetchedPlaceAlsoOutsideUkraine() {
        world.put("Q649", TestEntities.place("Q649", "Москва", 55.75, 37.62, "Q159"));
        world.put("Q270", TestEntities.entity("Q270").labelEn("Warsaw").build());
        PlaceResolver resolver = resolver();

        resolver.resolve(List.of("Q4149698", "Q649", "Q270", "Q404"));

        assertEquals("Гриньки", resolver.label("Q4149698"));
        assertEquals("Москва", resolver.label("Q649"));
        assertEquals("Warsaw", resolver.label("Q270"), "the English label when there is no Ukrainian one");
        assertNull(resolver.label("Q404"), "not on Wikidata");
        assertNull(resolver.label("Q1899"), "never fetched");
        assertNull(resolver.label(null));
    }
}
