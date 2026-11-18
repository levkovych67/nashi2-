package com.nashi.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegionTest {

    @Test
    void hasExactly27Regions() {
        assertThat(Region.values()).hasSize(27);
    }

    @Test
    void orderMatchesContract() {
        List<String> contract = List.of("CRIMEA", "VINNYTSIA", "VOLYN", "DNIPROPETROVSK", "DONETSK", "ZHYTOMYR",
                "ZAKARPATTIA", "ZAPORIZHZHIA", "IVANO_FRANKIVSK", "KYIV", "KIROVOHRAD", "LUHANSK", "LVIV", "MYKOLAIV",
                "ODESA", "POLTAVA", "RIVNE", "SUMY", "TERNOPIL", "KHARKIV", "KHERSON", "KHMELNYTSKYI", "CHERKASY",
                "CHERNIVTSI", "CHERNIHIV", "KYIV_CITY", "SEVASTOPOL_CITY");
        assertThat(Arrays.stream(Region.values()).map(Enum::name)).containsExactlyElementsOf(contract);
    }

    @Test
    void namesMatchContract() {
        assertThat(Region.KYIV_CITY.uaName()).isEqualTo("Київ");
        assertThat(Region.SEVASTOPOL_CITY.uaName()).isEqualTo("Севастополь");
        assertThat(Region.LVIV.uaName()).isEqualTo("Львівська область");
        assertThat(Region.CRIMEA.uaName()).isEqualTo("Автономна Республіка Крим");
    }
}
