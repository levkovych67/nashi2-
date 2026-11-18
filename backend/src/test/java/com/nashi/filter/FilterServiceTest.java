package com.nashi.filter;

import com.nashi.geo.Region;
import com.nashi.map.MapService;
import com.nashi.map.MapSnapshot;
import com.nashi.map.RegionStat;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilterServiceTest {

    @Test
    void regionFilterHas27OptionsWithCounts() {
        List<RegionStat> stats = Arrays.stream(Region.values())
                .map(r -> new RegionStat(r, r.uaName(), new double[] {30, 50}, new double[] {20, 44, 40, 53},
                        r == Region.LVIV ? 2 : 0))
                .toList();
        MapService map = mock(MapService.class);
        when(map.snapshot()).thenReturn(new MapSnapshot("abcd1234", List.of(), stats, List.of()));

        List<FilterDefinition> defs = new FilterService(map).definitions();

        assertThat(defs).hasSize(1);
        FilterDefinition region = defs.get(0);
        assertThat(region.key()).isEqualTo("region");
        assertThat(region.label()).isEqualTo("Область");
        assertThat(region.type()).isEqualTo(FilterType.MULTI_SELECT);
        assertThat(region.options()).hasSize(27);
        FilterOption lviv = region.options().stream().filter(o -> o.value().equals("LVIV")).findFirst().orElseThrow();
        assertThat(lviv.label()).isEqualTo("Львівська область");
        assertThat(lviv.count()).isEqualTo(2);
        assertThat(region.options()).filteredOn(o -> !o.value().equals("LVIV")).allMatch(o -> o.count() == 0);
    }
}
