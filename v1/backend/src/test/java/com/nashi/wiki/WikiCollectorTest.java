package com.nashi.wiki;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.domain.ArtistWork;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import com.nashi.util.WikiLinks;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.nashi.enums.ArtistCategory.MUSICIAN;
import static com.nashi.enums.ArtistCategory.VISUAL_ARTIST;
import static com.nashi.enums.ArtistCategory.WRITER;
import static org.junit.jupiter.api.Assertions.*;

class WikiCollectorTest {

    private static final String SPARQL = "https://query.wikidata.org/sparql";
    private static final String MUSICIAN_ROOT = "Q639669";
    private static final String PAINTER = "Q1028181";
    private static final String POET = "Q49757";
    private static final Pattern VALUES = Pattern.compile("VALUES \\?occ \\{ ([^}]*) }");
    private static final Pattern TREE_ROOT = Pattern.compile("\\?occ wdt:P279\\* wd:(Q\\d+)\\.");
    private static final Pattern CREATORS = Pattern.compile("VALUES \\?creator \\{ ([^}]*) }");
    private static final String NO_ROWS = "{\"head\":{\"vars\":[]},\"results\":{\"bindings\":[]}}";
    private static final String NO_MEMBERS = "{\"batchcomplete\":true,\"query\":{\"categorymembers\":[]}}";

    /** People of every recorded entities fixture in one answer; the artists fixture wins for Okean Elzy. */
    private static final String PEOPLE = merged("entities-kinds.json", "entities-awarded.json", "entities-artists.json");

    private final List<String> urls = new ArrayList<>();

    /** Made-up files some tests use; the works imageinfo answer gets a stand-in image for each of them. */
    private final Set<String> madeUpImages = new HashSet<>();

    /** SPARQL answers by query text: by default each occupation tree is just its root, people get the recorded list. */
    private Function<String, String> sparql = query -> isTree(query)
            ? tree(rootOf(query))
            : Fixtures.read("sparql-list.json");

    /** Recorded subclass trees of the state honour roots, by root. */
    private static final Map<String, String> HONOUR_TREES = Map.of(
            StateHonours.ORDER, "sparql-honours-order.json",
            StateHonours.MEDAL, "sparql-honours-medal.json",
            StateHonours.STATE_DECORATION, "sparql-honours-state-decoration.json",
            StateHonours.HONORARY_TITLE, "sparql-honours-title.json",
            StateHonours.DEPARTMENTAL, "sparql-honours-departmental.json");

    /** Answers like the real APIs, from recorded responses. */
    private final WikiHttp http = url -> {
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        urls.add(decoded);
        if (decoded.startsWith(SPARQL)) {
            return sparql.apply(queryOf(decoded));
        }
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=labels|claims|sitelinks")) {
            return PEOPLE;
        }
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=claims&")) {
            return onlyRequested(Fixtures.read("entities-awards.json"), decoded);
        }
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=labels|claims")) {
            return Fixtures.read("entities-places.json");
        }
        if (decoded.contains("action=wbgetentities")) {
            return Fixtures.read("entities-genres.json");
        }
        if (decoded.startsWith("https://uk.wikipedia.org/")) {
            return Fixtures.read("extracts.json");
        }
        if (decoded.startsWith("https://commons.wikimedia.org/") && decoded.contains("list=categorymembers")) {
            return categoryMembers(decoded);
        }
        if (decoded.startsWith("https://commons.wikimedia.org/") && decoded.contains("iiurlwidth=1280")) {
            return withMadeUpImages(Fixtures.read("imageinfo-works.json"), decoded);
        }
        if (decoded.startsWith("https://commons.wikimedia.org/")) {
            return Fixtures.read("imageinfo.json");
        }
        throw new AssertionError("unexpected " + decoded);
    };

    /**
     * A small world: Lysenko is a musician who also turns up among visual artists, Okean Elzy an ensemble, Shevchenko
     * a painter who also turns up among writers (through poet), Lesya Ukrainka a writer.
     */
    private String smallWorld(String query) {
        if (isTree(query)) {
            return switch (rootOf(query)) {
                case OccupationCategories.VISUAL_ROOT -> occupations(OccupationCategories.VISUAL_ROOT, PAINTER);
                case OccupationCategories.WRITER_ROOT -> Fixtures.read("sparql-occupations.json");
                default -> tree(rootOf(query));
            };
        }
        if (query.contains("wd:Q2088357")) {
            return people("Q1008489", "Океан Ельзи");
        }
        if (query.contains("wd:" + MUSICIAN_ROOT + " ")) {
            return people("Q172425", "Лисенко Микола Віталійович");
        }
        if (query.contains("wd:" + PAINTER + " ")) {
            return people("Q172425", "Лисенко Микола Віталійович", "Q134958", "Шевченко Тарас Григорович");
        }
        if (query.contains("wd:" + POET + " ")) {
            return people("Q134958", "Шевченко Тарас Григорович", "Q298033", "Леся Українка");
        }
        return people();
    }

    /** Works answers: the recorded visual works of Pymonenko and Murashko, written works of Shevchenko and Lesya. */
    private String worksWorld(String query) {
        if (query.contains("wdt:P170 ?creator")) {
            return Fixtures.read("sparql-works-visual.json");
        }
        if (query.contains("wdt:P800 ?work")) {
            return Fixtures.read("sparql-works-written.json");
        }
        throw new AssertionError("unexpected " + query);
    }

    /** Ivan Trush's Commons category and its «Paintings by Ivan Trush», as recorded; every other category is empty. */
    private static String categoryMembers(String decodedUrl) {
        if (decodedUrl.contains("cmtitle=Category:Ivan Trush (painter)")) {
            return Fixtures.read("commons-subcats.json");
        }
        if (decodedUrl.contains("cmtitle=Category:Paintings by Ivan Trush")) {
            return Fixtures.read("commons-files.json");
        }
        return NO_MEMBERS;
    }

    @Test
    void listsEachCandidateOnceAcrossAllQueries() {
        List<WikiCandidate> candidates = new WikiCollector(http).listCandidates();

        assertEquals(List.of("Q1008489", "Q172425"), candidates.stream().map(WikiCandidate::qid).toList());
        List<String> peopleQueries = sparqlQueries().stream().filter(q -> !isTree(q)).toList();
        // musicians, the ensemble query, visual artists, writers: citizens and born, one chunk each
        assertEquals(7, peopleQueries.size());
        assertTrue(peopleQueries.stream().allMatch(q -> q.contains("FILTER NOT EXISTS { ?item wdt:P27 wd:Q159 }")
                || q.contains("Q2088357")), "people queries leave out citizens of Russia");
    }

    @Test
    void candidatesKeepTheirFirstGroup() {
        sparql = this::smallWorld;
        WikiCollector collector = new WikiCollector(http);

        List<WikiCandidate> candidates = collector.listCandidates();

        assertEquals(List.of(
                new WikiCandidate("Q172425", "Лисенко Микола Віталійович", CandidateGroup.MUSIC),
                new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE),
                new WikiCandidate("Q134958", "Шевченко Тарас Григорович", CandidateGroup.VISUAL),
                new WikiCandidate("Q298033", "Леся Українка", CandidateGroup.WRITER)), candidates);
        assertEquals(0, collector.failedCandidateChunks());
    }

    @Test
    void fetchesTheOccupationTreesOncePerInstance() {
        sparql = this::smallWorld;
        WikiCollector collector = new WikiCollector(http);

        OccupationCategories kinds = collector.occupationCategories();
        collector.listCandidates();
        collector.listCandidates();

        assertEquals(List.of(
                WikiQueries.occupationTree("Q639669", List.of()),
                WikiQueries.occupationTree("Q177220", List.of()),
                WikiQueries.occupationTree("Q36834", List.of()),
                WikiQueries.occupationTree("Q3391743", List.of("Q1414443", "Q18545066", "Q935666", "Q1734662")),
                WikiQueries.occupationTree("Q36180", List.of("Q1086863", "Q3589290"))),
                sparqlQueries().stream().filter(WikiCollectorTest::isTree).toList());
        assertSame(kinds, collector.occupationCategories());
        assertEquals(List.of(WRITER, VISUAL_ARTIST, MUSICIAN), kinds.categoriesOf(List.of(POET, PAINTER, "Q36834")));
    }

    @Test
    void asksForEveryGroupInChunksOfItsTree() {
        sparql = this::smallWorld;

        new WikiCollector(http).listCandidates();

        List<String> peopleQueries = sparqlQueries().stream().filter(q -> !isTree(q)).toList();
        List<List<String>> chunks = peopleQueries.stream()
                .filter(q -> q.contains("VALUES ?occ"))
                .map(WikiCollectorTest::valuesOf)
                .toList();
        // musicians (3: the three roots), visual artists (2): citizens, born, one chunk each;
        // writers (28): citizens in one chunk of 40, the born in 20 + 8
        assertEquals(List.of(3, 3, 2, 2, 28, 20, 8), chunks.stream().map(List::size).toList());
        assertEquals(List.of("Q177220", "Q36834", MUSICIAN_ROOT), chunks.get(0));
        assertTrue(peopleQueries.get(0).contains(WikiQueries.CITIZEN));
        assertTrue(peopleQueries.get(1).contains(WikiQueries.BORN));
        assertEquals(WikiQueries.ensembles(), peopleQueries.get(2));
        assertEquals(List.of(PAINTER, "Q3391743"), chunks.get(2));
        assertTrue(chunks.get(4).contains(POET));
        assertTrue(peopleQueries.get(peopleQueries.size() - 1).contains(WikiQueries.BORN));
        assertTrue(peopleQueries.stream().noneMatch(q -> q.contains("wdt:P106/wdt:P279*")),
                "no query walks the occupation subclasses per person: that shape times out");
    }

    @Test
    void keepsGoingAndCountsCandidateChunksThatFailed() {
        sparql = query -> {
            if (!isTree(query) && (query.contains("wd:" + POET + " ") || query.contains("wd:" + MUSICIAN_ROOT + " "))) {
                throw new WikiFetchException("HTTP 500 for https://query.wikidata.org/sparql…", 500);
            }
            return smallWorld(query);
        };
        WikiCollector collector = new WikiCollector(http);

        List<WikiCandidate> candidates = collector.listCandidates();

        // Musician and poet each fail alone once per rule; the run goes on without them. Lysenko is still found
        // among visual artists; Lesya Ukrainka was only found through poet.
        assertEquals(4, collector.failedCandidateChunks());
        assertEquals(List.of(
                new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE),
                new WikiCandidate("Q172425", "Лисенко Микола Віталійович", CandidateGroup.VISUAL),
                new WikiCandidate("Q134958", "Шевченко Тарас Григорович", CandidateGroup.VISUAL)), candidates);
    }

    @Test
    void stopsWhenWikidataKeepsFailing() {
        // Every chunked query fails. The single ensembles query answers: a failure there would stop the run on its
        // own, before the breaker, which counts across the groups.
        sparql = query -> {
            if (query.contains("VALUES ?occ")) {
                throw new WikiFetchException("HTTP 503 for https://query.wikidata.org/sparql…", 503);
            }
            return smallWorld(query);
        };

        WikiFetchException e = assertThrows(WikiFetchException.class, () -> new WikiCollector(http).listCandidates());

        assertTrue(e.getMessage().contains("12 запитів поспіль не вдалися"), e.getMessage());
        assertEquals(12, sparqlQueries().stream().filter(q -> q.contains("VALUES ?occ")).count());
    }

    @Test
    void collectsDetailsForTheCandidates() {
        List<WikiCandidate> candidates = List.of(
                new WikiCandidate("Q172425", "Лисенко Микола Віталійович", CandidateGroup.MUSIC),
                new WikiCandidate("Q1980296", "Кобзон Йосип Давидович", CandidateGroup.MUSIC),
                new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE));

        WikiCollector collector = new WikiCollector(http);
        Map<String, WikiArtistData> byId = collector.fetchDetails(candidates).stream()
                .collect(Collectors.toMap(WikiArtistData::qid, Function.identity()));

        assertEquals(2, byId.size());
        assertFalse(byId.containsKey("Q1980296"));
        assertEquals(1, collector.russiaLinkedExcluded(), "Kobzon is a citizen of Russia");
        assertTrue(sparqlQueries().stream().allMatch(WikiCollectorTest::isTree), "no works asked for musicians");

        WikiArtistData lysenko = byId.get("Q172425");
        assertEquals(Region.POLTAVA, lysenko.place().region());
        assertTrue(lysenko.biography().startsWith("Микола"));
        assertTrue(lysenko.biography().length() <= 1501);
        assertNotNull(lysenko.imageUrl());
        assertEquals("Public domain", lysenko.imageCredit().getLicense());

        WikiArtistData band = byId.get("Q1008489");
        assertEquals(Region.LVIV, band.place().region());
        assertTrue(band.biography().contains("рок-гурт"));
        assertEquals("Alexander Khanin", band.imageCredit().getAuthor());
        assertTrue(band.imageUrl().contains("OkeanElzi25.jpg"));
        assertEquals(List.of(ArtistCategory.BAND), band.categories());
        assertEquals(LocationBasis.FORMATION, band.locationBasis());
        assertEquals(List.of(MUSICIAN), lysenko.categories());
        assertEquals(LocationBasis.BIRTH, lysenko.locationBasis());
        assertEquals("Гриньки", lysenko.birthPlace());
        assertEquals(List.of("опера", "національна музика", "art song", "симфонічна музика"), lysenko.genres());
    }

    @Test
    void fetchDetailsAssignsKindsAndAttachesWorks() {
        sparql = query -> isTree(query) ? smallWorld(query) : worksWorld(query);
        List<WikiCandidate> candidates = List.of(
                new WikiCandidate("Q134958", "Шевченко Тарас Григорович", CandidateGroup.VISUAL),
                new WikiCandidate("Q298033", "Леся Українка", CandidateGroup.WRITER),
                new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE));

        Map<String, WikiArtistData> byId = new WikiCollector(http).fetchDetails(candidates).stream()
                .collect(Collectors.toMap(WikiArtistData::qid, Function.identity()));

        WikiArtistData shevchenko = byId.get("Q134958");
        assertEquals(List.of(VISUAL_ARTIST, WRITER), shevchenko.categories());
        assertEquals("1814-03-09", shevchenko.birthDate());
        // the recorded visual works are not his and his Commons category has no works subcategory: writings only
        assertEquals(12, shevchenko.works().size());
        assertEquals("Кобзар", shevchenko.works().get(0).getTitle());
        assertEquals(List.of(WRITER), byId.get("Q298033").categories());
        assertFalse(byId.get("Q298033").works().isEmpty());
        assertEquals(List.of(), byId.get("Q1008489").works());
        assertTrue(shevchenko.socialLinks().stream()
                .anyMatch(l -> l.getUrl().equals("https://commons.wikimedia.org/wiki/Category:Taras_Shevchenko")));

        List<String> works = sparqlQueries().stream().filter(q -> !isTree(q)).toList();
        assertEquals(List.of(List.of("Q134958"), List.of("Q134958", "Q298033")),
                works.stream().map(WikiCollectorTest::creatorsOf).toList());
        assertTrue(urls.stream().anyMatch(u -> u.contains("cmtitle=Category:Taras Shevchenko")), urls.toString());
    }

    /** Okean Elzy with the given Wikidata properties dropped and a MusicBrainz id added. */
    private static String peopleWithMusicBrainz(String... dropped) {
        return peopleWithMbid("0b1c2d3e-4f50-4a61-8b72-93a4b5c6d7e8", dropped);
    }

    private static String peopleWithMbid(String mbid, String... dropped) {
        JsonObject root = JsonParser.parseString(PEOPLE).getAsJsonObject();
        JsonObject claims = root.getAsJsonObject("entities").getAsJsonObject("Q1008489").getAsJsonObject("claims");
        for (String property : dropped) {
            claims.remove(property);
        }
        JsonObject mb = claims.has("P1902")
                ? claims.getAsJsonArray("P1902").get(0).getAsJsonObject().deepCopy()
                : claims.getAsJsonArray("P2397").get(0).getAsJsonObject().deepCopy();
        mb.getAsJsonObject("mainsnak").addProperty("property", "P434");
        mb.getAsJsonObject("mainsnak").getAsJsonObject("datavalue").addProperty("value", mbid);
        mb.remove("id");
        claims.add("P434", JsonParser.parseString("[" + mb + "]"));
        return root.toString();
    }

    private WikiHttp withPeople(String people) {
        return url -> url.contains("props%3Dlabels%7Cclaims%7Csitelinks") || URLDecoder.decode(url, StandardCharsets.UTF_8)
                .contains("props=labels|claims|sitelinks") ? people : http.get(url);
    }

    private List<WikiArtistData> fetchOkeanElzy(String people, MusicBrainzSource musicBrainz) {
        sparql = query -> isTree(query) ? smallWorld(query) : worksWorld(query);
        return new WikiCollector(withPeople(people), musicBrainz)
                .fetchDetails(List.of(new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE)));
    }

    @Test
    void addsOnlyMissingPlatforms() {
        List<String> asked = new ArrayList<>();
        // the item has Spotify of its own but no YouTube
        List<WikiArtistData> artists = fetchOkeanElzy(peopleWithMusicBrainz("P2397"), mbid -> {
            asked.add(mbid);
            return Fixtures.read("musicbrainz-url-rels.json");
        });

        assertEquals(List.of("0b1c2d3e-4f50-4a61-8b72-93a4b5c6d7e8"), asked);
        List<SocialLink> links = artists.get(0).socialLinks();
        assertEquals(List.of("https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS"), links.stream()
                .filter(l -> l.getPlatform() == SocialLink.Platform.SPOTIFY).map(SocialLink::getUrl).toList());
        assertEquals("https://www.youtube.com/channel/UCIBrOaQCEcmtUGyanu360Xg", links.stream()
                .filter(l -> l.getPlatform() == SocialLink.Platform.YOUTUBE).map(SocialLink::getUrl).findFirst().orElseThrow());
        assertEquals(1, links.stream().filter(l -> l.getPlatform() == SocialLink.Platform.YOUTUBE).count());
        assertTrue(links.stream().anyMatch(l -> l.getPlatform() == SocialLink.Platform.DEEZER));
    }

    @Test
    void skipsWhenBothPresent() {
        List<String> asked = new ArrayList<>();
        List<WikiArtistData> artists = fetchOkeanElzy(peopleWithMusicBrainz(), mbid -> {
            asked.add(mbid);
            return Fixtures.read("musicbrainz-url-rels.json");
        });

        assertEquals(List.of(), asked);
        assertTrue(artists.get(0).socialLinks().stream().noneMatch(l -> l.getPlatform() == SocialLink.Platform.DEEZER));
    }

    @Test
    void aMusicBrainzFailureKeepsTheArtist() {
        List<WikiArtistData> artists = fetchOkeanElzy(peopleWithMusicBrainz("P2397"), mbid -> {
            throw new WikiFetchException("HTTP 503", 503);
        });

        assertEquals(1, artists.size());
        assertTrue(artists.get(0).socialLinks().stream().noneMatch(l -> l.getPlatform() == SocialLink.Platform.YOUTUBE));
    }

    @Test
    void skipsInvalidMbid() {
        List<String> asked = new ArrayList<>();
        List<WikiArtistData> artists = fetchOkeanElzy(peopleWithMbid("../x?y=1", "P2397"), mbid -> {
            asked.add(mbid);
            return Fixtures.read("musicbrainz-url-rels.json");
        });

        assertEquals(List.of(), asked);
        assertEquals(1, artists.size());
    }

    @Test
    void stopsAskingMusicBrainzAfterEightFailuresInARow() {
        JsonObject root = JsonParser.parseString(peopleWithMusicBrainz("P2397")).getAsJsonObject();
        JsonObject entities = root.getAsJsonObject("entities");
        List<WikiCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String qid = "Q90000" + (10 + i);
            JsonObject copy = entities.getAsJsonObject("Q1008489").deepCopy();
            copy.addProperty("id", qid);
            copy.getAsJsonObject("claims").getAsJsonArray("P434").get(0).getAsJsonObject()
                    .getAsJsonObject("mainsnak").getAsJsonObject("datavalue")
                    .addProperty("value", "0b1c2d3e-4f50-4a61-8b72-93a4b5c6d7" + (10 + i));
            entities.add(qid, copy);
            candidates.add(new WikiCandidate(qid, "Гурт " + i, CandidateGroup.ENSEMBLE));
        }
        sparql = query -> isTree(query) ? smallWorld(query) : worksWorld(query);
        AtomicInteger asked = new AtomicInteger();

        List<WikiArtistData> artists = new WikiCollector(withPeople(root.toString()), mbid -> {
            asked.incrementAndGet();
            throw new WikiFetchException("HTTP 503", 503);
        }).fetchDetails(candidates);

        assertEquals(8, asked.get());
        assertTrue(artists.size() >= 12);
    }

    @Test
    void excludesRussiaLinkedArtistsAndCountsThem() {
        List<WikiCandidate> candidates = List.of(
                new WikiCandidate("Q4089030", "Богатиков Юрій Йосипович", CandidateGroup.MUSIC),
                new WikiCandidate("Q316795", "Глієр Рейнгольд Моріцевич", CandidateGroup.MUSIC),
                new WikiCandidate("Q1980296", "Кобзон Йосип Давидович", CandidateGroup.MUSIC),
                new WikiCandidate("Q4082561", "Білокур Катерина Василівна", CandidateGroup.VISUAL));
        sparql = query -> isTree(query) ? smallWorld(query) : NO_ROWS;
        WikiCollector collector = new WikiCollector(http);

        List<WikiArtistData> kept = collector.fetchDetails(candidates);
        List<WikiArtistData> again = collector.fetchDetails(candidates);

        // Bogatikov: the Russian Order of Friendship; Kobzon: a citizen of Russia. Glière's titles are of the RSFSR,
        // Bilokur's awards Soviet and of the Ukrainian SSR: both are kept.
        assertEquals(List.of("Q316795", "Q4082561"), kept.stream().map(WikiArtistData::qid).toList());
        assertEquals(List.of("Q316795", "Q4082561"), again.stream().map(WikiArtistData::qid).toList());
        assertEquals(4, collector.russiaLinkedExcluded(), "two per call");
        List<String> awardRequests = urls.stream().filter(u -> u.contains("props=claims&")).toList();
        assertEquals(1, awardRequests.size(), "awards are fetched once per run: " + awardRequests);
        assertTrue(awardRequests.get(0).contains("Q791135") && awardRequests.get(0).contains("Q47024"));
    }

    @Test
    void judgesADegreeByTheAwardItBelongsToAndFetchesTheHonourTreesOnce() {
        sparql = query -> isTree(query) ? smallWorld(query) : NO_ROWS;
        String twoPeople = "{\"entities\":{" + person("Q900100", "Q19030089") + "," + person("Q900101", "Q29043608")
                + "}}";
        WikiHttp withTwoPeople = url -> URLDecoder.decode(url, StandardCharsets.UTF_8).contains("ids=Q900100")
                ? twoPeople
                : http.get(url);
        WikiCollector collector = new WikiCollector(withTwoPeople);

        List<WikiArtistData> kept = collector.fetchDetails(List.of(
                new WikiCandidate("Q900100", "Митець Q900100", CandidateGroup.MUSIC),
                new WikiCandidate("Q900101", "Митець Q900101", CandidateGroup.MUSIC)));
        collector.fetchDetails(List.of(new WikiCandidate("Q4089030", "Богатиков Юрій Йосипович", CandidateGroup.MUSIC)));

        // The 2nd degree of the medal of the Order «For Merit to the Fatherland» is judged by that state medal; the
        // 2nd degree of the Order of St. Sergius by that order of the Russian Orthodox Church, no state honour.
        assertEquals(List.of("Q900101"), kept.stream().map(WikiArtistData::qid).toList());
        List<String> awardRequests = urls.stream().filter(u -> u.contains("props=claims&")).toList();
        assertEquals(3, awardRequests.size(), awardRequests.toString());
        assertTrue(awardRequests.get(1).contains("Q339717") && awardRequests.get(1).contains("Q3355529"),
                "the awards the degrees belong to: " + awardRequests.get(1));
        List<String> honourTrees = sparqlQueries().stream().filter(WikiCollectorTest::isTree)
                .filter(q -> HONOUR_TREES.containsKey(rootOf(q))).toList();
        assertEquals(List.of(
                WikiQueries.occupationTree(StateHonours.ORDER, List.of()),
                WikiQueries.occupationTree(StateHonours.MEDAL, List.of()),
                WikiQueries.occupationTree(StateHonours.STATE_DECORATION, List.of()),
                WikiQueries.occupationTree(StateHonours.HONORARY_TITLE,
                        List.of("Q209896", "Q987080", "Q1326966", "Q3562482", "Q4375517")),
                WikiQueries.occupationTree(StateHonours.DEPARTMENTAL, List.of())), honourTrees, "once per run");
        assertEquals(2, collector.russiaLinkedExcluded(), "Q900100 and Bogatikov");
    }

    @Test
    void asksForEntitiesFiftyAtATimeAndForArticlesTwentyAtATime() {
        List<WikiCandidate> many = IntStream.range(0, 120)
                .mapToObj(i -> new WikiCandidate("Q" + (900000 + i), "Стаття " + i, CandidateGroup.MUSIC))
                .toList();

        new WikiCollector(http).fetchDetails(many);

        List<Integer> entityBatches = urls.stream()
                .filter(u -> u.contains("props=labels|claims|sitelinks"))
                .map(u -> u.replaceAll(".*[?&]ids=([^&]*).*", "$1").split("\\|").length)
                .toList();
        assertEquals(List.of(50, 50, 20), entityBatches);
    }

    @Test
    void failsLoudlyWhenAnApiReportsAnErrorInsteadOfSavingArtistsWithoutBiographies() {
        WikiHttp broken = url -> URLDecoder.decode(url, StandardCharsets.UTF_8).startsWith("https://uk.wikipedia.org/")
                ? "{\"error\":{\"code\":\"internal_api_error_DBQueryError\",\"info\":\"Database query error.\"}}"
                : http.get(url);

        WikiFetchException e = assertThrows(WikiFetchException.class, () -> new WikiCollector(broken)
                .fetchDetails(List.of(new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE))));

        assertTrue(e.getMessage().contains("internal_api_error_DBQueryError"), e.getMessage());
    }

    @Test
    void failsLoudlyWhenWikidataReportsAnError() {
        WikiHttp broken = url -> URLDecoder.decode(url, StandardCharsets.UTF_8).contains("action=wbgetentities")
                ? "{\"error\":{\"code\":\"maxlag\",\"info\":\"Waiting for a database server.\"},\"servedby\":\"mw1\"}"
                : http.get(url);

        assertThrows(WikiFetchException.class, () -> new WikiCollector(broken)
                .fetchDetails(List.of(new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE))));
    }

    @Test
    void collectsWorksForVisualArtistsAndWriters() {
        sparql = this::worksWorld;
        Map<String, List<ArtistCategory>> categories = new LinkedHashMap<>();
        categories.put("Q2601651", List.of(VISUAL_ARTIST));
        categories.put("Q172425", List.of(MUSICIAN));
        categories.put("Q4307907", List.of(VISUAL_ARTIST));
        categories.put("Q134958", List.of(WRITER));
        categories.put("Q298033", List.of(WRITER));
        Map<String, String> commons = Map.of("Q2601651", "Mykola Pymonenko", "Q4307907", "Oleksandr Murashko");

        Map<String, List<ArtistWork>> works = new WikiCollector(http).works(categories, commons);

        assertEquals(List.of("Q2601651", "Q4307907", "Q134958", "Q298033"), List.copyOf(works.keySet()));

        List<ArtistWork> pymonenko = works.get("Q2601651");
        // with an article (by year), with a title (by year, then without one), without a title
        // Q112066266 is chosen too, but the recorded imageinfo has no such file: a painting without an image is dropped
        assertEquals(List.of("Q123614066", "Q4179712", "Q111089816", "Q99322583", "Q25261971",
                "Q112066285", "Q111334155"), ids(pymonenko));
        ArtistWork wedding = pymonenko.get(0);
        assertEquals("Весілля в Київській губернії", wedding.getTitle());
        assertEquals(1891, wedding.getYear());
        assertTrue(wedding.getFullImageUrl().startsWith("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a3/"),
                wedding.getFullImageUrl());
        assertTrue(wedding.getFullImageUrl().contains("/1280px-"), wedding.getFullImageUrl());
        assertFalse(wedding.getFullImageUrl().contains("?"), "tracking parameters are dropped");
        assertEquals(wedding.getFullImageUrl().replace("/1280px-", "/500px-"), wedding.getImageUrl());
        assertEquals("Пимоненко, Николай Корнильевич", wedding.getImageCredit().getAuthor());
        assertEquals("Public domain", wedding.getImageCredit().getLicense());
        assertEquals(WikiLinks.articleUrl("Весілля в Київській губернії"), wedding.getArticleUrl());

        ArtistWork ford = work(pymonenko, "Q99322583");
        assertEquals("Брід", ford.getTitle());
        assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mykola_Pymonenko-Brod.jpg"
                + "/1280px-Mykola_Pymonenko-Brod.jpg", ford.getFullImageUrl());
        assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mykola_Pymonenko-Brod.jpg"
                + "/500px-Mykola_Pymonenko-Brod.jpg", ford.getImageUrl());
        assertEquals("https://commons.wikimedia.org/wiki/File:Mykola_Pymonenko-Brod.jpg",
                ford.getImageCredit().getFilePageUrl());
        assertNull(ford.getArticleUrl());
        assertNull(work(pymonenko, "Q111334155").getTitle(), "a QID label is no title");
        assertTrue(pymonenko.stream().allMatch(w -> w.getImageUrl() != null), "every painting has an image");

        assertEquals(List.of("Q111042861", "Q124338217", "Q111088900", "Q111082122"), ids(works.get("Q4307907")));

        List<ArtistWork> shevchenko = works.get("Q134958");
        assertEquals(12, shevchenko.size());
        assertEquals(List.of("Кобзар", "Сон", "Гайдамаки"),
                shevchenko.subList(0, 3).stream().map(ArtistWork::getTitle).toList());
        assertEquals(WikiLinks.articleUrl("Кобзар (збірка)"), shevchenko.get(0).getArticleUrl());
        assertEquals(1840, shevchenko.get(0).getYear());
        assertNull(shevchenko.get(0).getImageUrl());
        assertEquals(WikiLinks.wikisourceUrl("Кобзарь (1876)/Том 1/Наймичка"),
                work(shevchenko, "Q12132246").getArticleUrl(), "a text on Wikisource when there is no article");

        ArtistWork legend = work(works.get("Q298033"), "Q131380149");
        assertEquals("https://uk.wikisource.org/wiki/%D0%9C%D1%96%D1%81%D1%8F%D1%87%D0%BD%D0%B0_"
                + "%D0%BB%D0%B5%D0%B3%D0%B5%D0%BD%D0%B4%D0%B0", legend.getArticleUrl());
        assertEquals(1893, legend.getYear());

        List<String> queries = sparqlQueries();
        assertEquals(List.of(List.of("Q2601651", "Q4307907"), List.of("Q134958", "Q298033")),
                queries.stream().map(WikiCollectorTest::creatorsOf).toList());
        assertEquals(WikiQueries.visualWorks(List.of("Q2601651", "Q4307907")), queries.get(0));
        assertEquals(WikiQueries.writtenWorks(List.of("Q134958", "Q298033")), queries.get(1));
        assertTrue(urls.stream().noneMatch(u -> u.contains("list=categorymembers")),
                "Pymonenko and Murashko have 4 works or more on Wikidata");
        List<String> imageinfo = urls.stream().filter(u -> u.contains("prop=imageinfo")).toList();
        assertFalse(imageinfo.isEmpty());
        assertTrue(imageinfo.stream().allMatch(u -> u.contains("iiurlwidth=1280")), imageinfo.toString());
    }

    @Test
    void commonsFallbackForAPainterWithoutWikidataWorks() {
        sparql = this::worksWorld;
        Map<String, List<ArtistCategory>> categories = new LinkedHashMap<>();
        categories.put("Q1364775", List.of(VISUAL_ARTIST));
        categories.put("Q4082561", List.of(VISUAL_ARTIST));
        categories.put("Q900001", List.of(VISUAL_ARTIST));
        Map<String, String> commons = Map.of("Q1364775", "Ivan Trush (painter)", "Q4082561", "Katerina Bilokur");

        Map<String, List<ArtistWork>> works = new WikiCollector(http).works(categories, commons);

        // Of the 12 files of «Paintings by Ivan Trush» only the Sunbeam is in the recorded imageinfo; a Commons work
        // without an image is left out. Bilokur's category has no works subcategory; Q900001 has no category.
        assertEquals(List.of("Q1364775"), List.copyOf(works.keySet()));
        assertEquals(1, works.get("Q1364775").size());
        ArtistWork sunbeam = works.get("Q1364775").get(0);
        assertNull(sunbeam.getWikidataId());
        assertEquals("Ivan Trush - Sunbeam", sunbeam.getTitle());
        assertNull(sunbeam.getYear());
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/f/fe/Ivan_Trush_-_Sunbeam.jpg",
                sunbeam.getFullImageUrl());
        assertEquals(sunbeam.getFullImageUrl(), sunbeam.getImageUrl(), "a small original is its own preview");
        assertEquals("Ivan Trush, Ukrainian painter, died March 1941", sunbeam.getImageCredit().getAuthor());
        assertNull(sunbeam.getArticleUrl());

        List<String> members = urls.stream().filter(u -> u.contains("list=categorymembers")).toList();
        assertEquals(3, members.size(), members.toString());
        assertTrue(members.get(0).contains("cmtype=subcat") && members.get(0).contains("Category:Ivan Trush (painter)"));
        assertTrue(members.get(1).contains("cmtype=file") && members.get(1).contains("Category:Paintings by Ivan Trush")
                && members.get(1).contains("cmlimit=12"), members.get(1));
        assertTrue(members.get(2).contains("cmtype=subcat") && members.get(2).contains("Category:Katerina Bilokur"));
    }

    @Test
    void commonsWorksFollowTheWikidataOnesAndSkipTheirImages() {
        sparql = query -> visualAnswer("Q1364775", "Q20000001", "Портрет Івана Франка", "Ivan Franko 2.jpg");
        madeUpImages.add("Ivan Franko 2.jpg");
        Map<String, List<ArtistCategory>> categories = Map.of("Q1364775", List.of(VISUAL_ARTIST));

        List<ArtistWork> works = new WikiCollector(http)
                .works(categories, Map.of("Q1364775", "Ivan Trush (painter)")).get("Q1364775");

        assertEquals(Arrays.asList("Q20000001", null), ids(works));
        assertEquals("Портрет Івана Франка", works.get(0).getTitle());
        assertEquals("Ivan Trush - Sunbeam", works.get(1).getTitle());
        // 12 files are listed, so the one already shown by the Wikidata work still leaves 11 for Commons.
        assertTrue(urls.stream().anyMatch(u -> u.contains("cmtype=file") && u.contains("cmlimit=12")), urls.toString());
        List<String> asked = urls.stream().filter(u -> u.contains("prop=imageinfo"))
                .flatMap(u -> Arrays.stream(u.substring(u.indexOf("titles=") + "titles=".length()).split("\\|")))
                .toList();
        assertEquals(12, asked.size(), asked.toString());
        assertEquals(1, asked.stream().filter(t -> t.equals("File:Ivan Franko 2.jpg")).count());
    }

    @Test
    void asksForWorksTwoHundredCreatorsAtATime() {
        sparql = query -> NO_ROWS;
        Map<String, List<ArtistCategory>> categories = new LinkedHashMap<>();
        for (int i = 0; i < 450; i++) {
            categories.put("Q" + (700000 + i), List.of(VISUAL_ARTIST));
        }
        categories.put("Q134958", List.of(WRITER, VISUAL_ARTIST));
        categories.put("Q298033", List.of(WRITER));

        Map<String, List<ArtistWork>> works = new WikiCollector(http).works(categories, Map.of());

        assertEquals(Map.of(), works);
        List<String> queries = sparqlQueries();
        assertEquals(List.of(200, 200, 51, 2), queries.stream().map(q -> creatorsOf(q).size()).toList());
        assertTrue(queries.subList(0, 3).stream().allMatch(q -> q.contains("wdt:P170 ?creator")));
        assertTrue(queries.get(3).contains("wdt:P800 ?work"));
    }

    @Test
    void splitsAWorksBatchThatIsTooHeavy() {
        sparql = query -> {
            List<String> creators = creatorsOf(query);
            if (creators.size() > 100) {
                throw new WikiFetchException("HTTP 504 for https://query.wikidata.org/sparql…", 504);
            }
            return creators.contains("Q2601651") ? Fixtures.read("sparql-works-visual.json") : NO_ROWS;
        };
        Map<String, List<ArtistCategory>> categories = new LinkedHashMap<>();
        for (int i = 0; i < 249; i++) {
            categories.put("Q" + (700000 + i), List.of(VISUAL_ARTIST));
        }
        categories.put("Q2601651", List.of(VISUAL_ARTIST));

        Map<String, List<ArtistWork>> works = new WikiCollector(http).works(categories, Map.of());

        assertEquals(List.of(200, 100, 100, 50), sparqlQueries().stream().map(q -> creatorsOf(q).size()).toList());
        assertEquals(7, works.get("Q2601651").size());
    }

    @Test
    void worksStopOnARefusalInsteadOfSplitting() {
        WikiFetchException limited = new WikiFetchException("HTTP 429 for https://query.wikidata.org/sparql…", 429);
        sparql = query -> {
            throw limited;
        };
        Map<String, List<ArtistCategory>> categories = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) {
            categories.put("Q" + (700000 + i), List.of(VISUAL_ARTIST));
        }

        assertSame(limited, assertThrows(WikiFetchException.class,
                () -> new WikiCollector(http).works(categories, Map.of())));
        assertEquals(1, sparqlQueries().size());
    }

    @Test
    void aWriterWhoPaintsGetsBothKindsWithinTwelve() {
        // Shevchenko: 14 recorded written works, and here 10 paintings; one painting is the item of «Гайдамаки».
        List<String> paintings = new ArrayList<>(List.of("Q18551805", "Гайдамаки (малюнок)", "Shevchenko Haidamaky.jpg"));
        madeUpImages.add("Shevchenko Haidamaky.jpg");
        for (int i = 1; i <= 9; i++) {
            paintings.addAll(List.of("Q" + (30000000 + i), "Картина " + i, "Shevchenko painting " + i + ".jpg"));
            madeUpImages.add("Shevchenko painting " + i + ".jpg");
        }
        sparql = query -> query.contains("wdt:P170 ?creator")
                ? visualAnswer("Q134958", paintings.toArray(String[]::new))
                : Fixtures.read("sparql-works-written.json");
        Map<String, List<ArtistCategory>> categories = Map.of("Q134958", List.of(WRITER, VISUAL_ARTIST));

        List<ArtistWork> works = new WikiCollector(http).works(categories, Map.of()).get("Q134958");

        // The kinds take turns in the order of his categories, so neither crowds the other out.
        assertEquals(List.of("Кобзар", "Гайдамаки (малюнок)", "Сон", "Картина 1"),
                works.subList(0, 4).stream().map(ArtistWork::getTitle).toList());
        assertEquals(12, works.size());
        assertEquals(5, works.stream().filter(w -> w.getTitle().startsWith("Картина")).count());
        assertEquals(works.size(), works.stream().map(ArtistWork::getWikidataId).distinct().count(),
                "a work found as written and as painted is kept once, as the painting");
    }

    private List<String> sparqlQueries() {
        return urls.stream().filter(u -> u.startsWith(SPARQL)).map(WikiCollectorTest::queryOf).toList();
    }

    private static List<String> creatorsOf(String query) {
        Matcher matcher = CREATORS.matcher(query);
        assertTrue(matcher.find(), query);
        return List.of(matcher.group(1).replace("wd:", "").trim().split(" "));
    }

    private static List<String> ids(List<ArtistWork> works) {
        return works.stream().map(ArtistWork::getWikidataId).toList();
    }

    private static ArtistWork work(List<ArtistWork> works, String qid) {
        return works.stream().filter(w -> qid.equals(w.getWikidataId())).findFirst().orElseThrow();
    }

    /** A visual-works answer in the endpoint's shape, from (work, title, file) triples of one creator. */
    private static String visualAnswer(String creator, String... worksTitlesAndFiles) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < worksTitlesAndFiles.length; i += 3) {
            String file = URLEncoder.encode(worksTitlesAndFiles[i + 2], StandardCharsets.UTF_8).replace("+", "%20");
            rows.add("{\"creator\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + creator + "\"},"
                    + "\"work\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + worksTitlesAndFiles[i] + "\"},"
                    + "\"title\":{\"xml:lang\":\"uk\",\"type\":\"literal\",\"value\":\"" + worksTitlesAndFiles[i + 1] + "\"},"
                    + "\"image\":{\"type\":\"uri\",\"value\":\"http://commons.wikimedia.org/wiki/Special:FilePath/" + file
                    + "\"}}");
        }
        return "{\"head\":{\"vars\":[\"creator\",\"work\",\"title\",\"year\",\"image\",\"ukTitle\"]},"
                + "\"results\":{\"bindings\":[" + String.join(",", rows) + "]}}";
    }

    private static String queryOf(String decodedUrl) {
        return decodedUrl.substring(decodedUrl.indexOf("query=") + "query=".length());
    }

    private static List<String> valuesOf(String query) {
        Matcher matcher = VALUES.matcher(query);
        assertTrue(matcher.find(), query);
        return List.of(matcher.group(1).replace("wd:", "").trim().split(" "));
    }

    private static boolean isTree(String query) {
        return query.startsWith("SELECT DISTINCT ?occ ");
    }

    private static String rootOf(String query) {
        Matcher matcher = TREE_ROOT.matcher(query);
        assertTrue(matcher.find(), query);
        return matcher.group(1);
    }

    /** A tree answer in the endpoint's shape. */
    private static String occupations(String... ids) {
        return Arrays.stream(ids)
                .map(id -> "{\"occ\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + id + "\"}}")
                .collect(Collectors.joining(",", "{\"head\":{\"vars\":[\"occ\"]},\"results\":{\"bindings\":[", "]}}"));
    }

    /** A people answer in the endpoint's shape, from (qid, title) pairs. */
    private static String people(String... qidsAndTitles) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < qidsAndTitles.length; i += 2) {
            rows.add("{\"item\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + qidsAndTitles[i] + "\"},"
                    + "\"title\":{\"xml:lang\":\"uk\",\"type\":\"literal\",\"value\":\"" + qidsAndTitles[i + 1] + "\"}}");
        }
        return "{\"head\":{\"vars\":[\"item\",\"title\"]},\"results\":{\"bindings\":[" + String.join(",", rows) + "]}}";
    }

    /** One wbgetentities answer with the entities of all the given fixtures; a later fixture wins. */
    private static String merged(String... fixtures) {
        JsonObject entities = new JsonObject();
        for (String fixture : fixtures) {
            JsonParser.parseString(Fixtures.read(fixture)).getAsJsonObject().getAsJsonObject("entities").entrySet()
                    .forEach(entry -> entities.add(entry.getKey(), entry.getValue()));
        }
        JsonObject root = new JsonObject();
        root.add("entities", entities);
        return root.toString();
    }

    /** The recorded imageinfo answer plus a page with a stand-in image for each requested made-up file. */
    private String withMadeUpImages(String json, String decodedUrl) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String titles = decodedUrl.substring(decodedUrl.indexOf("titles=") + "titles=".length());
        for (String title : titles.split("\\|")) {
            String file = title.substring("File:".length());
            if (madeUpImages.contains(file)) {
                String url = "https://upload.wikimedia.org/wikipedia/commons/x/xx/" + file.replace(' ', '_');
                root.getAsJsonObject("query").getAsJsonArray("pages").add(JsonParser.parseString(
                        "{\"title\":\"" + title + "\",\"imageinfo\":[{\"url\":\"" + url + "\"}]}"));
            }
        }
        return root.toString();
    }

    /** The recorded occupation or honour tree of the root; any other tree is just its root. */
    private static String tree(String root) {
        return HONOUR_TREES.containsKey(root) ? Fixtures.read(HONOUR_TREES.get(root)) : occupations(root);
    }

    /** The entities of a recorded wbgetentities answer that the URL asks for, as the API answers. */
    private static String onlyRequested(String json, String decodedUrl) {
        Set<String> ids = Set.copyOf(Arrays.asList(decodedUrl.replaceAll(".*[?&]ids=([^&]*).*", "$1").split("\\|")));
        JsonObject all = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("entities");
        JsonObject entities = new JsonObject();
        ids.stream().filter(all::has).forEach(id -> entities.add(id, all.get(id)));
        JsonObject root = new JsonObject();
        root.add("entities", entities);
        return root.toString();
    }

    /** A hand-made human, a citizen of Ukraine with a uk article, holding the given awards. */
    private static String person(String qid, String... awards) {
        String held = Arrays.stream(awards).map(award -> claim("P166", award)).collect(Collectors.joining(","));
        return "\"" + qid + "\":{\"type\":\"item\",\"id\":\"" + qid + "\","
                + "\"labels\":{\"uk\":{\"language\":\"uk\",\"value\":\"Митець " + qid + "\"}},"
                + "\"sitelinks\":{\"ukwiki\":{\"site\":\"ukwiki\",\"title\":\"Митець " + qid + "\"}},"
                + "\"claims\":{\"P31\":[" + claim("P31", "Q5") + "],\"P27\":[" + claim("P27", "Q212") + "],"
                + "\"P166\":[" + held + "]}}";
    }

    private static String claim(String property, String item) {
        return "{\"mainsnak\":{\"snaktype\":\"value\",\"property\":\"" + property + "\",\"datavalue\":{\"value\":"
                + "{\"entity-type\":\"item\",\"id\":\"" + item + "\"},\"type\":\"wikibase-entityid\"}},"
                + "\"type\":\"statement\",\"rank\":\"normal\"}";
    }
}
