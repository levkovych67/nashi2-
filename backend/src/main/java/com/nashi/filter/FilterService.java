package com.nashi.filter;

import com.nashi.map.MapService;
import com.nashi.map.MapSnapshot;
import org.springframework.stereotype.Service;

import java.util.List;

/** Filter definitions of the list and the map. Phase 1 has one: the region, with all 27 options. */
@Service
public class FilterService {

    private final MapService map;

    public FilterService(MapService map) {
        this.map = map;
    }

    public List<FilterDefinition> definitions() {
        return definitions(map.snapshot());
    }

    /** Counts come from the given snapshot, so the response and its ETag agree. */
    public List<FilterDefinition> definitions(MapSnapshot snapshot) {
        List<FilterOption> options = snapshot.regions().stream()
                .map(r -> new FilterOption(r.code().name(), r.code().uaName(), r.count()))
                .toList();
        return List.of(new FilterDefinition("region", "Область", FilterType.MULTI_SELECT, options));
    }
}
