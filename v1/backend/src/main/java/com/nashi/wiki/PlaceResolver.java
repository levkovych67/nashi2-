package com.nashi.wiki;

import com.nashi.enums.Region;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Finds which region a place belongs to by walking its current administrative parents (P131) up to an oblast.
 * Entities are fetched level by level for all places at once and cached across calls.
 */
public class PlaceResolver {

    static final int MAX_DEPTH = 8;

    private final Function<Collection<String>, Map<String, WikiEntity>> fetchEntities;
    private final Map<String, WikiEntity> cache = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private final Map<String, Optional<Region>> regionCache = new HashMap<>();

    public PlaceResolver(Function<Collection<String>, Map<String, WikiEntity>> fetchEntities) {
        this.fetchEntities = fetchEntities;
    }

    /** Places in Ukraine that have coordinates, keyed by their id; other places are left out. */
    public Map<String, WikiPlace> resolve(Collection<String> placeQids) {
        Set<String> places = new LinkedHashSet<>(placeQids);
        load(places);

        Map<String, Set<String>> frontier = new LinkedHashMap<>();
        Map<String, Set<String>> visited = new HashMap<>();
        Map<String, Region> found = new HashMap<>();
        for (String place : places) {
            WikiEntity entity = cache.get(place);
            if (entity == null || entity.lat() == null || entity.lon() == null) {
                continue;
            }
            Optional<Region> known = regionCache.get(place);
            if (known != null) {
                known.ifPresent(region -> found.put(place, region));
                continue;
            }
            frontier.put(place, new LinkedHashSet<>(List.of(place)));
            visited.put(place, new HashSet<>(List.of(place)));
        }

        for (int depth = 0; depth <= MAX_DEPTH && !frontier.isEmpty(); depth++) {
            Set<String> toLoad = new HashSet<>();
            frontier.values().forEach(nodes -> nodes.stream()
                    .filter(node -> UkraineRegions.fromQid(node).isEmpty())
                    .forEach(toLoad::add));
            load(toLoad);

            Map<String, Set<String>> next = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : frontier.entrySet()) {
                String place = entry.getKey();
                Set<String> parents = new LinkedHashSet<>();
                for (String node : entry.getValue()) {
                    Optional<Region> region = UkraineRegions.fromQid(node);
                    if (region.isPresent()) {
                        found.put(place, region.get());
                        parents.clear();
                        break;
                    }
                    WikiEntity entity = cache.get(node);
                    if (entity != null) {
                        for (String parent : entity.partOf()) {
                            if (visited.get(place).add(parent)) {
                                parents.add(parent);
                            }
                        }
                    }
                }
                if (!found.containsKey(place) && !parents.isEmpty()) {
                    next.put(place, parents);
                }
            }
            frontier = next;
        }

        Map<String, WikiPlace> result = new LinkedHashMap<>();
        for (String place : places) {
            WikiEntity entity = cache.get(place);
            if (entity == null || entity.lat() == null || entity.lon() == null) {
                continue;
            }
            Region region = found.get(place);
            regionCache.put(place, Optional.ofNullable(region));
            if (region != null) {
                String label = entity.labelUk() != null ? entity.labelUk() : entity.labelEn();
                result.put(place, new WikiPlace(place, label, entity.lat(), entity.lon(), region));
            }
        }
        return result;
    }

    /** Ukrainian (else English) label of any place fetched so far, in Ukraine or not; null when it was not fetched. */
    public String label(String qid) {
        WikiEntity entity = qid == null ? null : cache.get(qid);
        if (entity == null) {
            return null;
        }
        return entity.labelUk() != null ? entity.labelUk() : entity.labelEn();
    }

    private void load(Collection<String> ids) {
        List<String> wanted = ids.stream()
                .filter(id -> !cache.containsKey(id) && !missing.contains(id))
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return;
        }
        Map<String, WikiEntity> loaded = fetchEntities.apply(wanted);
        cache.putAll(loaded);
        wanted.stream().filter(id -> !loaded.containsKey(id)).forEach(missing::add);
    }
}
