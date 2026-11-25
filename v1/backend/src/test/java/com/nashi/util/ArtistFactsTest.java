package com.nashi.util;

import com.nashi.domain.Artist;
import com.nashi.domain.Member;
import com.nashi.enums.ArtistCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArtistFactsTest {

    private static final Member VOCALIST = Member.builder().firstName("Святослав").role("вокал").build();

    @Test
    void formBandWithMembersIsBand() {
        Artist band = Artist.builder().name("Гурт з форми").members(List.of(VOCALIST)).build();

        assertEquals(List.of(ArtistCategory.BAND), ArtistFacts.categoriesOf(band));
    }

    @Test
    void formSoloIsMusician() {
        Artist solo = Artist.builder().name("Соло з форми").build();
        Artist soloWithNullLists = Artist.builder().name("Соло з форми").members(null).categories(null).build();

        assertEquals(List.of(ArtistCategory.MUSICIAN), ArtistFacts.categoriesOf(solo));
        assertEquals(List.of(ArtistCategory.MUSICIAN), ArtistFacts.categoriesOf(soloWithNullLists));
    }

    @Test
    void storedCategoriesWin() {
        Artist artist = Artist.builder()
                .categories(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST))
                .members(List.of(VOCALIST))
                .build();

        assertEquals(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST), ArtistFacts.categoriesOf(artist));
    }

    @Test
    void storedGenresWin() {
        Artist artist = Artist.builder().style("Рок").genres(List.of("поп-рок", "брит-поп")).build();

        assertEquals(List.of("поп-рок", "брит-поп"), ArtistFacts.genresOf(artist));
    }

    @Test
    void genresFallBackToStyle() {
        Artist artist = Artist.builder().style("Рок").build();

        assertEquals(List.of("Рок"), ArtistFacts.genresOf(artist));
    }

    @Test
    void noStyleNoGenres() {
        assertEquals(List.of(), ArtistFacts.genresOf(Artist.builder().build()));
        assertEquals(List.of(), ArtistFacts.genresOf(Artist.builder().style("  ").genres(null).build()));
    }

    @Test
    void yearOfPersonIsBirthYear() {
        Artist writer = Artist.builder()
                .categories(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST))
                .birthDate("1814-03-09")
                .build();
        Artist musician = Artist.builder().birthDate("1975").foundationYear(1994).build();

        assertEquals(1814, ArtistFacts.yearOf(writer));
        assertEquals(1975, ArtistFacts.yearOf(musician));
    }

    @Test
    void yearOfBandIsFoundation() {
        Artist formBand = Artist.builder().members(List.of(VOCALIST)).foundationYear(1994).build();
        Artist storedBand = Artist.builder()
                .categories(List.of(ArtistCategory.BAND))
                .birthDate("1990-01-01")
                .foundationYear(2004)
                .build();

        assertEquals(1994, ArtistFacts.yearOf(formBand));
        assertEquals(2004, ArtistFacts.yearOf(storedBand));
    }

    @Test
    void yearOfPersonWithoutBirthFallsBackToFoundation() {
        Artist solo = Artist.builder().foundationYear(2015).build();
        Artist soloWithUnreadableBirth = Artist.builder().birthDate("невідомо").foundationYear(2016).build();

        assertEquals(2015, ArtistFacts.yearOf(solo));
        assertEquals(2016, ArtistFacts.yearOf(soloWithUnreadableBirth));
        assertNull(ArtistFacts.yearOf(Artist.builder().build()));
    }
}
