package com.nashi.place.seed;

import com.nashi.place.PlaceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceTypesTest {

    @Test
    void knownQidsMapToExpectedTypes() {
        assertThat(PlaceTypes.of("Q12131624")).contains(PlaceType.CITY);    // місто України
        assertThat(PlaceTypes.of("Q515")).contains(PlaceType.CITY);         // місто
        assertThat(PlaceTypes.of("Q4100864")).contains(PlaceType.TOWN);     // селище міського типу України
        assertThat(PlaceTypes.of("Q121366249")).contains(PlaceType.TOWN);   // селище України
        assertThat(PlaceTypes.of("Q21672098")).contains(PlaceType.VILLAGE); // село України
        assertThat(PlaceTypes.of("Q532")).contains(PlaceType.VILLAGE);      // село
        assertThat(PlaceTypes.of("Q2023000")).contains(PlaceType.VILLAGE);  // хутір
    }

    @Test
    void leavesOutWhatIsNotALivingSettlement() {
        assertThat(PlaceTypes.of("Q486972")).isEmpty();   // населений пункт (too generic)
        assertThat(PlaceTypes.of("Q22674925")).isEmpty(); // колишній населений пункт
        assertThat(PlaceTypes.of("Q634099")).isEmpty();   // сільське поселення Росії (a municipality)
        assertThat(PlaceTypes.of("Q3565075")).isEmpty();  // район міста України
        assertThat(PlaceTypes.of("Q604435")).isEmpty();   // мікрорайон
    }

    @Test
    void excludedTypesAreNotAlsoKept() {
        assertThat(PlaceTypes.excludedQids()).contains("Q22674925", "Q21507948", "Q350895", "Q74047")
                .doesNotContainAnyElementsOf(PlaceTypes.qids());
    }

    @Test
    void everyEntryIsLabelledAndNoneIsOther() {
        assertThat(PlaceTypes.TABLE).isNotEmpty().allSatisfy(e -> {
            assertThat(e.qid()).matches("Q[1-9][0-9]*");
            assertThat(e.label()).isNotBlank();
            assertThat(e.type()).isNotEqualTo(PlaceType.OTHER);
        });
        assertThat(PlaceTypes.qids()).hasSameSizeAs(PlaceTypes.TABLE);
    }
}
