package com.nashi.wiki;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.geo.Region;
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

import static com.nashi.wiki.WikiCategory.MUSICIAN;
import static org.junit.jupiter.api.Assertions.*;

class WikiCollectorTest {

    private static final String SPARQL = "https://query.wikidata.org/sparql";
    private static final String MUSICIAN_ROOT = "Q639669";
    private static final Pattern VALUES = Pattern.compile("VALUES \\?occ \\{ ([^}]*) }");
    private static final Pattern TREE_ROOT = Pattern.compile("\\?occ wdt:P279\\* wd:(Q\\d+)\\.");
    private static final String NO_ROWS = "{\"head\":{\"vars\":[]},\"results\":{\"bindings\":[]}}";
    private static final String NO_MEMBERS = "{\"batchcomplete\":true,\"query\":{\"categorymembers\":[]}}";

    /** People of every recorded entities fixture in one answer; the artists fixture wins for Okean Elzy. */
    private static final String PEOPLE = merged("entities-kinds.json", "entities-awarded.json", "entities-artists.json");

    private final List<String> urls = new ArrayList<>();

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
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=labels|descriptions|claims|sitelinks")) {
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
        if (decoded.startsWith("https://commons.wikimedia.org/")) {
            return Fixtures.read("imageinfo.json");
        }
        throw new AssertionError("unexpected " + decoded);
    };

    /** A small world: Lysenko is a musician, Okean Elzy an ensemble. */
    private String smallWorld(String query) {
        if (query.contains("wd:Q2088357")) {
            return people("Q1008489", "Океан Ельзи");
        }
        if (query.contains("wd:" + MUSICIAN_ROOT + " ")) {
            return people("Q172425", "Лисенко Микола Віталійович");
        }
        return isTree(query) ? tree(rootOf(query)) : people();
    }

    @Test
    void listsEachCandidateOnceAcrossAllQueries() {
        List<WikiCandidate> candidates = new WikiCollector(http).listCandidates();

        assertEquals(List.of("Q1008489", "Q172425"), candidates.stream().map(WikiCandidate::qid).toList());
        List<String> peopleQueries = sparqlQueries().stream().filter(q -> !isTree(q)).toList();
        // musicians: citizens and born, one chunk each; the ensemble query
        assertEquals(3, peopleQueries.size());
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
                new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE)), candidates);
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
                WikiQueries.occupationTree("Q36834", List.of())),
                sparqlQueries().stream().filter(WikiCollectorTest::isTree).toList());
        assertSame(kinds, collector.occupationCategories());
        assertEquals(List.of(MUSICIAN), kinds.categoriesOf(List.of("Q49757", "Q36834")));
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
        // musicians (3: the three roots): citizens, born, one chunk each
        assertEquals(List.of(3, 3), chunks.stream().map(List::size).toList());
        assertEquals(List.of("Q177220", "Q36834", MUSICIAN_ROOT), chunks.get(0));
        assertTrue(peopleQueries.get(0).contains(WikiQueries.CITIZEN));
        assertTrue(peopleQueries.get(1).contains(WikiQueries.BORN));
        assertEquals(WikiQueries.ensembles(), peopleQueries.get(2));
        assertTrue(peopleQueries.stream().noneMatch(q -> q.contains("wdt:P106/wdt:P279*")),
                "no query walks the occupation subclasses per person: that shape times out");
    }

    @Test
    void keepsGoingAndCountsCandidateChunksThatFailed() {
        sparql = query -> {
            if (!isTree(query) && query.contains("wd:" + MUSICIAN_ROOT + " ")) {
                throw new WikiFetchException("HTTP 500 for https://query.wikidata.org/sparql…", 500);
            }
            return smallWorld(query);
        };
        WikiCollector collector = new WikiCollector(http);

        List<WikiCandidate> candidates = collector.listCandidates();

        // The musician root fails alone once per rule; the run goes on without it and with the ensembles.
        assertEquals(2, collector.failedCandidateChunks());
        assertEquals(List.of(new WikiCandidate("Q1008489", "Океан Ельзи", CandidateGroup.ENSEMBLE)), candidates);
    }

    @Test
    void skipsEveryOccupationWhenTheChunkedQueriesKeepFailing() {
        // The breaker of ChunkSplitter (12 failures in a row) is covered by CandidateListerTest; three occupations
        // fail 5 times per rule, 10 in all, so the run goes on with the ensembles only.
        sparql = query -> {
            if (query.contains("VALUES ?occ")) {
                throw new WikiFetchException("HTTP 503 for https://query.wikidata.org/sparql…", 503);
            }
            return smallWorld(query);
        };
        WikiCollector collector = new WikiCollector(http);

        List<WikiCandidate> candidates = collector.listCandidates();

        assertEquals(6, collector.failedCandidateChunks());
        assertEquals(List.of("Q1008489"), candidates.stream().map(WikiCandidate::qid).toList());
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
        assertEquals(List.of(WikiCategory.BAND), band.categories());
        assertEquals(LocationBasis.FORMATION, band.locationBasis());
        assertEquals(List.of(MUSICIAN), lysenko.categories());
        assertEquals(LocationBasis.BIRTH, lysenko.locationBasis());
        assertEquals("Гриньки", lysenko.birthPlace());
        assertEquals(List.of("опера", "національна музика", "art song", "симфонічна музика"), lysenko.genres());
    }

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
        return url -> url.contains("props%3Dlabels%7Cdescriptions%7Cclaims%7Csitelinks") || URLDecoder.decode(url, StandardCharsets.UTF_8)
                .contains("props=labels|descriptions|claims|sitelinks") ? people : http.get(url);
    }

    private List<WikiArtistData> fetchOkeanElzy(String people, MusicBrainzSource musicBrainz) {
        sparql = query -> isTree(query) ? smallWorld(query) : NO_ROWS;
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
        List<WikiLink> links = artists.get(0).socialLinks();
        assertEquals(List.of("https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS"), links.stream()
                .filter(l -> l.getPlatform() == WikiLink.Platform.SPOTIFY).map(WikiLink::getUrl).toList());
        assertEquals("https://www.youtube.com/channel/UCIBrOaQCEcmtUGyanu360Xg", links.stream()
                .filter(l -> l.getPlatform() == WikiLink.Platform.YOUTUBE).map(WikiLink::getUrl).findFirst().orElseThrow());
        assertEquals(1, links.stream().filter(l -> l.getPlatform() == WikiLink.Platform.YOUTUBE).count());
        assertTrue(links.stream().anyMatch(l -> l.getPlatform() == WikiLink.Platform.DEEZER));
    }

    @Test
    void skipsWhenBothPresent() {
        List<String> asked = new ArrayList<>();
        List<WikiArtistData> artists = fetchOkeanElzy(peopleWithMusicBrainz(), mbid -> {
            asked.add(mbid);
            return Fixtures.read("musicbrainz-url-rels.json");
        });

        assertEquals(List.of(), asked);
        assertTrue(artists.get(0).socialLinks().stream().noneMatch(l -> l.getPlatform() == WikiLink.Platform.DEEZER));
    }

    @Test
    void aMusicBrainzFailureKeepsTheArtist() {
        List<WikiArtistData> artists = fetchOkeanElzy(peopleWithMusicBrainz("P2397"), mbid -> {
            throw new WikiFetchException("HTTP 503", 503);
        });

        assertEquals(1, artists.size());
        assertTrue(artists.get(0).socialLinks().stream().noneMatch(l -> l.getPlatform() == WikiLink.Platform.YOUTUBE));
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
        sparql = query -> isTree(query) ? smallWorld(query) : NO_ROWS;
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
                new WikiCandidate("Q4082561", "Білокур Катерина Василівна", CandidateGroup.MUSIC));
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
                .filter(u -> u.contains("props=labels|descriptions|claims|sitelinks"))
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

    private List<String> sparqlQueries() {
        return urls.stream().filter(u -> u.startsWith(SPARQL)).map(WikiCollectorTest::queryOf).toList();
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
