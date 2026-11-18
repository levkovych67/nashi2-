package com.nashi.wiki;

import com.nashi.geo.Region;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nashi.wiki.WikiCategory.BAND;
import static com.nashi.wiki.WikiCategory.MUSICIAN;
import static org.junit.jupiter.api.Assertions.*;

class WikiArtistAssemblerTest {

    private final Map<String, WikiEntity> artists = WikidataEntityParser.parse(Fixtures.read("entities-artists.json"));
    private final Map<String, WikiEntity> kinds = WikidataEntityParser.parse(Fixtures.read("entities-kinds.json"));
    private final Map<String, WikiEntity> awarded = WikidataEntityParser.parse(Fixtures.read("entities-awarded.json"));
    private final Map<String, WikiEntity> genres = WikidataEntityParser.parse(Fixtures.read("entities-genres.json"));
    /** The recorded award items, also the awards their degrees belong to (P361). */
    private final Map<String, WikiEntity> awards = WikidataEntityParser.parse(Fixtures.read("entities-awards.json"));
    /**
     * The state honour classes as the collector fetches them: the recorded subclass trees of the four roots, and of
     * the departmental award of Russia.
     */
    private final StateHonours honours = new StateHonours(Stream.of("order", "medal", "state-decoration", "title")
            .flatMap(tree -> SparqlParser.parseIds(Fixtures.read("sparql-honours-" + tree + ".json"), "occ").stream())
            .toList(), SparqlParser.parseIds(Fixtures.read("sparql-honours-departmental.json"), "occ"));
    private final Set<String> russianAwards = awards.values().stream()
            .filter(award -> WikiArtistAssembler.isRussianAward(award, awards, honours))
            .map(WikiEntity::qid)
            .collect(Collectors.toSet());
    private final Map<String, WikiPlace> places = Map.of(
            "Q4149698", new WikiPlace("Q4149698", "Гриньки", 49.45, 33.03, Region.POLTAVA),
            "Q36036", new WikiPlace("Q36036", "Львів", 49.84, 24.03, Region.LVIV));
    /** Labels of every fetched place, in Ukraine or not. */
    private final Map<String, String> placeLabels = Map.of("Q4149698", "Гриньки", "Q36036", "Львів",
            "Q270", "Варшава", "Q2180620", "Моринці");
    /** The tree as the collector fetches it: the musician roots. */
    private final OccupationCategories occupations =
            OccupationCategories.fromMusicianTree(OccupationCategories.MUSICIAN_ROOTS);

    private Optional<WikiArtistData> assemble(WikiEntity e, CandidateGroup group) {
        return assemble(e, group, genres);
    }

    private Optional<WikiArtistData> assemble(WikiEntity e, CandidateGroup group, Map<String, WikiEntity> labels) {
        return WikiArtistAssembler.assemble(e, group, places, labels, occupations, placeLabels::get, russianAwards);
    }

    @Test
    void russianCitizenStillExcluded() {
        assertEquals(Optional.empty(), assemble(artists.get("Q1980296"), CandidateGroup.MUSIC));
    }

    @Test
    void russianAwardsAreStateHonoursOfTheRussianFederationOnly() {
        // Of the recorded awards, state honours of Russia: the orders of Friendship and of Honour, People's Artist of
        // Russia, the medal of the Order «For Merit to the Fatherland» and its 2nd degree (judged by the medal it is
        // part of). Not: awards of the USSR, the RSFSR (P17 = USSR, P131 = RSFSR; owner decision, 2026-11-09), the
        // Ukrainian SSR, the Russian Empire or Ukraine; awards of several countries (Miner's Glory, the 1995 Victory
        // medal); private and union prizes (Kuryokhin, Golden Gramophone, Nika and its categories, Korolenko); the RSFSR
        // honorary diploma; orders of the Russian Orthodox Church and their degrees; a title of Tatarstan; the Ushinsky
        // Medal of a ministry.
        assertEquals(Set.of("Q791135", "Q2268261", "Q1857712", "Q339717", "Q19030089"), russianAwards);
    }

    @Test
    void russianStateAwardExcludes() {
        // Yuriy Bogatikov, a Ukrainian citizen: the Russian Order of Friendship among Soviet and Ukrainian awards
        assertEquals(Optional.empty(), assemble(awarded.get("Q4089030"), CandidateGroup.MUSIC));

        WikiEntity title = TestEntities.person("Q10", "Співак", Set.of("Q212"), List.of(), List.of(), List.of());
        assertTrue(assemble(title, CandidateGroup.MUSIC).isPresent());
    }

    @Test
    void russianStateOrderExcludes() {
        // Sofia Rotaru: the Russian Order of Honour (a state order) and the Golden Gramophone (a radio station's prize)
        WikiEntity singer = TestEntities.person("Q20", "Співачка", Set.of("Q212"), List.of(), List.of(), List.of());

        assertEquals(Optional.empty(), assemble(withAwards(singer, "Q2268261", "Q4193488"), CandidateGroup.MUSIC));
        assertTrue(assemble(withAwards(singer, "Q4193488"), CandidateGroup.MUSIC).isPresent(),
                "the Golden Gramophone alone does not exclude");
    }

    @Test
    void peoplesArtistOfRussiaExcludes() {
        WikiEntity singer = TestEntities.person("Q21", "Співак", Set.of("Q212"), List.of(), List.of(), List.of());

        assertEquals(Optional.empty(), assemble(withAwards(singer, "Q1857712"), CandidateGroup.MUSIC));
    }

    @Test
    void rsfsrHonoraryTitleDoesNotExclude() {
        // Owner decision, 2026-11-09: titles of the RSFSR are Soviet titles, like those of the USSR. The full dry run
        // of 2026-11-08 had left out 157 people for them alone, Dovzhenko (People's Artist of the RSFSR, 1950) among
        // them. Reinhold Glière, born in Kyiv: People's Artist and Honoured Artist of the RSFSR.
        assertTrue(assemble(awarded.get("Q316795"), CandidateGroup.MUSIC).isPresent());

        WikiEntity person = TestEntities.person("Q11", "Режисер", Set.of("Q212"), List.of(), List.of(), List.of());
        assertTrue(assemble(withAwards(person, "Q47024", "Q46919", "Q13418138"), CandidateGroup.MUSIC).isPresent(),
                "People's Artist, Honoured Artist and Honoured Art Worker of the RSFSR");
    }

    @Test
    void titleOfARussianRegionDoesNotExclude() {
        // Honoured Art Worker of Tatarstan, held by the Crimean Tatar artist Diliaver Osmanov: a title of a federal
        // subject of Russia (Q4375517), not a state honour of Russia
        WikiEntity painter = TestEntities.person("Q14", "Художник", Set.of("Q212"), List.of(), List.of(), List.of());

        assertFalse(russianAwards.contains("Q24932254"));
        assertTrue(assemble(withAwards(painter, "Q24932254"), CandidateGroup.MUSIC).isPresent());
    }

    @Test
    void departmentalMedalDoesNotExclude() {
        // The Ushinsky Medal of the education ministry, held by Vasyl Sukhomlynskyi: a medal, but a departmental award
        // (Q6866472), not a state honour
        WikiEntity writer = TestEntities.person("Q15", "Педагог", Set.of("Q212"), List.of(), List.of(), List.of());

        assertFalse(russianAwards.contains("Q4287157"));
        assertTrue(assemble(withAwards(writer, "Q4287157"), CandidateGroup.MUSIC).isPresent());
    }

    @Test
    void privateRussianPrizeDoesNotExclude() {
        // DakhaBrakha's Sergey Kuryokhin award: a Russian contemporary-art prize, not a state honour
        WikiEntity band = TestEntities.entity("Q22").labelUk("Гурт").ukTitle("Гурт").instanceOf(Set.of("Q215380"))
                .awards(List.of("Q4377087")).build();

        assertTrue(assemble(band, CandidateGroup.ENSEMBLE).isPresent());
        assertTrue(assemble(withAwards(TestEntities.person("Q23", "Актор", Set.of("Q212"), List.of(), List.of(),
                List.of()), "Q16687981"), CandidateGroup.MUSIC).isPresent(), "a Nika category, judged by the Nika");
    }

    @Test
    void rsfsrHonoraryDiplomaDoesNotExclude() {
        // «Почесна грамота Президії Верховної Ради РРФСР», held by the Kyiv bandurist capella and the «Dumka» chapel
        WikiEntity chapel = TestEntities.entity("Q24").labelUk("Капела").ukTitle("Капела").instanceOf(Set.of("Q215380"))
                .awards(List.of("Q4375448")).build();

        assertTrue(assemble(chapel, CandidateGroup.ENSEMBLE).isPresent());
    }

    @Test
    void churchOrderDoesNotExclude() {
        // The Russian Orthodox Church's Order of St. Volodymyr, and the 2nd degree of its Order of St. Sergius
        WikiEntity painter = TestEntities.person("Q25", "Іконописець", Set.of("Q212"), List.of(), List.of(), List.of());

        assertTrue(assemble(withAwards(painter, "Q4336018", "Q29043608"), CandidateGroup.MUSIC).isPresent());
    }

    @Test
    void sovietAwardDoesNotExclude() {
        // Kateryna Bilokur: Order of the Badge of Honour (USSR) and two Ukrainian SSR titles
        assertTrue(assemble(kinds.get("Q4082561"), CandidateGroup.MUSIC).isPresent());

        WikiEntity person = TestEntities.person("Q12", "Співак", Set.of("Q212"), List.of(), List.of(), List.of());
        assertTrue(assemble(withAwards(person, "Q403569", "Q185493", "Q28975206", "Q783276"), CandidateGroup.MUSIC)
                .isPresent(), "People's Artist of the USSR, Order of Lenin, Miner's Glory, the 1995 Victory medal");
    }

    @Test
    void ukrainianPrizeMisstatedAsRussianDoesNotExclude() {
        // The Korolenko Prize is given by the National Writers' Union of Ukraine (uk.wikipedia), but its item states
        // Russia as country; it left out two Kyiv writers in the 2026-11-08 sample run. A literary prize is no state
        // honour.
        WikiEntity writer = TestEntities.person("Q19", "Письменниця", Set.of("Q212"), List.of(), List.of(), List.of());

        assertFalse(russianAwards.contains("Q97306182"));
        assertTrue(assemble(withAwards(writer, "Q97306182"), CandidateGroup.MUSIC).isPresent());
    }

    @Test
    void imperialAwardDoesNotExclude() {
        WikiEntity person = TestEntities.person("Q13", "Художник", Set.of("Q34266"), List.of(), List.of(), List.of());

        assertTrue(assemble(withAwards(person, "Q1147717", "Q15972370"), CandidateGroup.MUSIC).isPresent(),
                "Order of St. Anna");
    }

    @Test
    void personWithMusicianOccupationIsMusician() {
        WikiEntity singer = TestEntities.entity("Q15").labelUk("Співак").ukTitle("Співак").instanceOf(Set.of("Q5"))
                .occupations(List.of("Q177220")).build();

        assertEquals(List.of(MUSICIAN), assemble(singer, CandidateGroup.MUSIC).orElseThrow().categories());
    }

    @Test
    void taglineIsTheUkrainianWikidataDescription() {
        WikiEntity band = TestEntities.entity("Q16").labelUk("Гурт").ukTitle("Гурт").instanceOf(Set.of("Q215380"))
                .descriptionUk(" український рок-гурт ").build();
        WikiEntity bare = TestEntities.entity("Q17").labelUk("Інший").ukTitle("Інший").instanceOf(Set.of("Q215380"))
                .build();

        assertEquals("український рок-гурт", assemble(band, CandidateGroup.ENSEMBLE).orElseThrow().tagline());
        assertNull(assemble(bare, CandidateGroup.ENSEMBLE).orElseThrow().tagline());
    }

    @Test
    void bandIsBand() {
        assertEquals(List.of(BAND), assemble(artists.get("Q1008489"), CandidateGroup.ENSEMBLE).orElseThrow()
                .categories());
        assertEquals(List.of(BAND), assemble(artists.get("Q1008489"), CandidateGroup.MUSIC).orElseThrow()
                .categories(), "an ensemble is a band whichever query found it");
    }

    @Test
    void fallsBackToTheGroupWhenNoOccupationIsInATree() {
        // Lysenko's recorded item has no P106; an occupation outside every tree counts for nothing.
        assertEquals(List.of(MUSICIAN), assemble(artists.get("Q172425"), CandidateGroup.MUSIC).orElseThrow()
                .categories());
        WikiEntity critic = TestEntities.entity("Q14").labelUk("Критик").ukTitle("Критик").instanceOf(Set.of("Q5"))
                .occupations(List.of("Q99999999")).build();
        assertEquals(List.of(MUSICIAN), assemble(critic, CandidateGroup.MUSIC).orElseThrow().categories());
        assertEquals(List.of(BAND), assemble(critic, CandidateGroup.ENSEMBLE).orElseThrow().categories());
    }

    @Test
    void birthDateAndDeathDateCopied() {
        WikiArtistData shevchenko = assemble(kinds.get("Q134958"), CandidateGroup.MUSIC).orElseThrow();
        WikiArtistData jamala = assemble(kinds.get("Q2662517"), CandidateGroup.MUSIC).orElseThrow();

        assertEquals("1814-03-09", shevchenko.birthDate());
        assertEquals("1861-03-10", shevchenko.deathDate());
        assertEquals("1983-08-27", jamala.birthDate());
        assertNull(jamala.deathDate());
        assertNull(assemble(artists.get("Q1008489"), CandidateGroup.ENSEMBLE).orElseThrow().birthDate());
    }

    @Test
    void birthPlaceOutsideUkraineKeptAsLabel() {
        WikiEntity emigrant = TestEntities.person("Q1", "Співачка", Set.of("Q212"), List.of("Q270"), List.of("Q36036"),
                List.of());

        WikiArtistData data = assemble(emigrant, CandidateGroup.MUSIC).orElseThrow();

        assertEquals("Варшава", data.birthPlace());
        assertEquals(Region.LVIV, data.place().region());
        assertEquals(LocationBasis.RESIDENCE, data.locationBasis());
    }

    @Test
    void locationBasisBirthWhenBornInUkraine() {
        WikiArtistData lysenko = assemble(artists.get("Q172425"), CandidateGroup.MUSIC).orElseThrow();

        assertEquals(Region.POLTAVA, lysenko.place().region());
        assertEquals(LocationBasis.BIRTH, lysenko.locationBasis());
        assertEquals("Гриньки", lysenko.birthPlace());
        assertEquals("Моринці", assemble(kinds.get("Q134958"), CandidateGroup.MUSIC).orElseThrow().birthPlace());
    }

    @Test
    void locationBasisOfABandAWorkPlaceAndNoPlace() {
        WikiArtistData band = assemble(artists.get("Q1008489"), CandidateGroup.ENSEMBLE).orElseThrow();
        WikiEntity worker = TestEntities.person("Q15", "Художниця", Set.of("Q212"), List.of("Q270"), List.of(),
                List.of("Q36036"));
        WikiEntity nowhere = TestEntities.person("Q2", "Без місця", Set.of("Q212"), List.of("Q649"), List.of(),
                List.of());

        assertEquals(LocationBasis.FORMATION, band.locationBasis());
        assertNull(band.birthPlace());
        assertEquals(LocationBasis.WORK, assemble(worker, CandidateGroup.MUSIC).orElseThrow().locationBasis());
        WikiArtistData offTheMap = assemble(nowhere, CandidateGroup.MUSIC).orElseThrow();
        assertNull(offTheMap.place());
        assertNull(offTheMap.locationBasis());
        assertNull(offTheMap.birthPlace(), "a place that was not fetched has no label");
    }

    @Test
    void genresFromGenreAndMovementMaxFive() {
        Map<String, WikiEntity> labels = Map.of(
                "QG1", TestEntities.genre("QG1", "рок", "Rock"),
                "QG2", TestEntities.genre("QG2", null, "Punk"),
                "QM1", TestEntities.genre("QM1", "модернізм", "modernism"),
                "QM2", TestEntities.genre("QM2", "авангард", "avant-garde"),
                "QM3", TestEntities.genre("QM3", "футуризм", "futurism"),
                "QM4", TestEntities.genre("QM4", "кубізм", "cubism"),
                "QX", TestEntities.genre("QX", "рок", "Rock music"));
        WikiEntity artist = TestEntities.entity("Q16").labelUk("Митець").ukTitle("Митець").instanceOf(Set.of("Q5"))
                .genres(List.of("QG1", "QMISSING", "QG2", "QX")).movements(List.of("QG1", "QM1", "QM2", "QM3", "QM4"))
                .build();

        WikiArtistData data = assemble(artist, CandidateGroup.MUSIC, labels).orElseThrow();

        assertEquals(List.of("рок", "Punk", "модернізм", "авангард", "футуризм"), data.genres());
        assertEquals("рок", data.style());
        assertEquals(Set.of("rock", "punk", "rock music"), data.tags(), "tags stay the English genre labels");
    }

    @Test
    void assemblesLinksOnlyFromOwnItem() {
        WikiEntity first = TestEntities.entity("Q17").labelUk("Перший").ukTitle("Перший").instanceOf(Set.of("Q5"))
                .socialIds(Map.of("P1902", "spotify-one")).build();
        WikiEntity second = TestEntities.entity("Q18").labelUk("Другий").ukTitle("Другий").instanceOf(Set.of("Q5"))
                .socialIds(Map.of("P1902", "spotify-two")).build();

        List<String> one = urls(assemble(first, CandidateGroup.MUSIC).orElseThrow());
        List<String> two = urls(assemble(second, CandidateGroup.MUSIC).orElseThrow());

        assertTrue(one.contains("https://open.spotify.com/artist/spotify-one"), one.toString());
        assertTrue(two.contains("https://open.spotify.com/artist/spotify-two"), two.toString());
        assertTrue(Collections.disjoint(one, two), one + " / " + two);
        assertTrue(one.contains("https://www.wikidata.org/wiki/Q17"));
    }

    @Test
    void startsWithoutWorks() {
        WikiArtistData shevchenko = assemble(kinds.get("Q134958"), CandidateGroup.MUSIC).orElseThrow();

        assertEquals(List.of(), shevchenko.works());
    }

    @Test
    void placesAClassicWhereHeWasBorn() {
        WikiArtistData lysenko = assemble(artists.get("Q172425"), CandidateGroup.MUSIC).orElseThrow();

        assertEquals("Q172425", lysenko.qid());
        assertEquals("Микола Лисенко", lysenko.name());
        assertEquals("Лисенко Микола Віталійович", lysenko.wikiTitle());
        assertFalse(lysenko.ensemble());
        assertEquals(Region.POLTAVA, lysenko.place().region());
        assertEquals(1869, lysenko.foundationYear());
        assertNull(lysenko.disbandYear());
        assertTrue(lysenko.tags().contains("opera"));
        assertEquals("Лисенко Микола (cropped).jpg", lysenko.imageFile());
    }

    @Test
    void fallsBackToResidenceWhenBornAbroad() {
        WikiEntity emigrant = TestEntities.person("Q1", "Співачка", Set.of("Q212"), List.of("Q649"), List.of("Q36036"),
                List.of());

        assertEquals(Region.LVIV, assemble(emigrant, CandidateGroup.MUSIC).orElseThrow().place().region());
    }

    @Test
    void placesABandWhereItWasFormed() {
        WikiArtistData band = assemble(artists.get("Q1008489"), CandidateGroup.ENSEMBLE).orElseThrow();

        assertTrue(band.ensemble());
        assertEquals("Океан Ельзи", band.name());
        assertEquals(Region.LVIV, band.place().region());
        assertEquals("Львів", band.place().labelUk());
        assertEquals(1994, band.foundationYear());
    }

    @Test
    void writesGenresInEnglishForTagsAndInUkrainianForStyle() {
        Map<String, WikiEntity> rock = Map.of("QR", TestEntities.genre("QR", "рок", "Rock"));
        WikiEntity band = TestEntities.entity("Q3").labelUk("Гурт").ukTitle("Гурт").instanceOf(Set.of("Q215380"))
                .genres(List.of("QR")).build();

        WikiArtistData data = assemble(band, CandidateGroup.ENSEMBLE, rock).orElseThrow();

        assertEquals(Set.of("rock"), data.tags());
        assertEquals("рок", data.style());
        assertEquals(List.of("рок"), data.genres());
    }

    @Test
    void buildsSocialLinksAndTheArticleLink() {
        List<String> links = assemble(artists.get("Q1008489"), CandidateGroup.ENSEMBLE).orElseThrow()
                .socialLinks().stream().map(l -> l.getPlatform() + " " + l.getUrl()).toList();

        assertTrue(links.contains(link(WikiLink.Platform.WEBSITE, "https://okeanelzy.com")));
        assertTrue(links.contains(link(WikiLink.Platform.INSTAGRAM, "https://www.instagram.com/okeanelzy_official/")));
        assertTrue(links.contains(link(WikiLink.Platform.FACEBOOK, "https://www.facebook.com/okeanelzyofficial")));
        assertTrue(links.contains(link(WikiLink.Platform.YOUTUBE,
                "https://www.youtube.com/channel/UCjJ-oj2aJTINILYLDv2MFEQ")));
        assertTrue(links.contains(link(WikiLink.Platform.SPOTIFY, "https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS")));
        assertTrue(links.contains(link(WikiLink.Platform.SOUNDCLOUD, "https://soundcloud.com/okean-elzy")));
        assertTrue(links.contains(link(WikiLink.Platform.WIKI,
                "https://uk.wikipedia.org/wiki/%D0%9E%D0%BA%D0%B5%D0%B0%D0%BD_%D0%95%D0%BB%D1%8C%D0%B7%D0%B8")));
        assertTrue(links.contains(link(WikiLink.Platform.WIKIDATA, "https://www.wikidata.org/wiki/Q1008489")));
    }

    @Test
    void usesTheArticleTitleWithoutItsQualifierWhenThereIsNoLabel() {
        WikiEntity band = TestEntities.entity("Q4").ukTitle("Кому вниз (гурт)").instanceOf(Set.of("Q215380")).build();

        assertEquals("Кому вниз", assemble(band, CandidateGroup.ENSEMBLE).orElseThrow().name());
    }

    @Test
    void putsTheGivenNameFirstForEncyclopediaStyleNames() {
        WikiEntity full = TestEntities.person("Q5", "Чубай Тарас Григорович", Set.of("Q212"), List.of(), List.of(),
                List.of());
        WikiEntity shortLabel = TestEntities.entity("Q6").labelUk("Федорова Анна").ukTitle("Федорова Анна Борисівна")
                .instanceOf(Set.of("Q5")).citizenships(Set.of("Q212")).build();

        assertEquals("Тарас Чубай", assemble(full, CandidateGroup.MUSIC).orElseThrow().name());
        assertEquals("Анна Федорова", assemble(shortLabel, CandidateGroup.MUSIC).orElseThrow().name());
        assertEquals("Микола Лисенко", assemble(artists.get("Q172425"), CandidateGroup.MUSIC).orElseThrow().name());
        assertEquals("Океан Ельзи", assemble(artists.get("Q1008489"), CandidateGroup.ENSEMBLE).orElseThrow().name());
    }

    @Test
    void cleansNamesSeenInTheFullDryRun() {
        assertEquals("Аркадій Гендлер", WikiArtistAssembler.displayName("Аркадій Хунович Гендлер", null, false));
        assertEquals("Дмитро Гордон", WikiArtistAssembler.displayName("Гордон Дмитро Ілліч", null, false));
        assertEquals("Марiя Загайкевич", WikiArtistAssembler.displayName("Загайкевич Марiя Петрiвна", null, false));
        assertEquals("Фагот", WikiArtistAssembler.displayName("Фагот (співак)", null, false));
        assertEquals("Гуляйгород", WikiArtistAssembler.displayName("Гуляйгород (гурт)", null, true));
        assertEquals("Микола Лисенко",
                WikiArtistAssembler.displayName("Лисенко Микола Віталійович (молодший)", null, false));
        assertEquals("Євген Станкович", WikiArtistAssembler.displayName("Євген Станкович", null, false));
        assertEquals("Mad Heads", WikiArtistAssembler.displayName("Mad Heads", null, true));
    }

    private static WikiEntity withAwards(WikiEntity person, String... awards) {
        return TestEntities.entity(person.qid()).labelUk(person.labelUk()).ukTitle(person.ukTitle())
                .instanceOf(person.instanceOf()).citizenships(person.citizenships()).awards(List.of(awards)).build();
    }

    private static List<String> urls(WikiArtistData data) {
        return data.socialLinks().stream().map(WikiLink::getUrl).toList();
    }

    private static String link(WikiLink.Platform platform, String url) {
        return platform + " " + url;
    }
}
