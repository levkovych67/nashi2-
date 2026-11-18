package com.nashi.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects artists from Wikidata, the Ukrainian Wikipedia and Wikimedia Commons. Has no Spring dependencies so the
 * dry run can use it. One instance caches occupation trees, places, genre labels and awards, so keep it for the whole
 * run.
 */
@Slf4j
public class WikiCollector implements ArtistCandidateSource {

    static final int ENTITY_BATCH = 50;
    static final int TITLE_BATCH = 20;
    /** The API rounds 400 up to a standard 500 px thumbnail. */
    static final int PORTRAIT_WIDTH = 400;
    /** The portrait on the musician's page. */
    static final int PORTRAIT_FULL_WIDTH = 800;
    /** Artists per album query; the 2026-11-18 query for three artists took about a second. */
    static final int ALBUM_BATCH = 200;
    static final int MAX_ALBUMS = 12;
    /** A Wikipedia qualifier at the end of an album label: "Міра (альбом)". */
    private static final Pattern ALBUM_QUALIFIER =
            Pattern.compile("\\s*\\((?:альбом|album)\\)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String SPARQL = "https://query.wikidata.org/sparql?format=json&query=";
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";
    private static final String UK_WIKI_API = "https://uk.wikipedia.org/w/api.php";
    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";

    private final WikiHttp http;
    /** After this many failures in a row MusicBrainz is given up for the rest of the run. */
    static final int MUSICBRAINZ_MAX_FAILURES = 8;
    private static final Pattern MBID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    private final MusicBrainzSource musicBrainz;
    private int musicBrainzFailuresInARow;
    private final PlaceResolver places;
    /** Genres and movements with their labels. */
    private final Map<String, WikiEntity> labelCache = new HashMap<>();
    /** Award → whether it is a state honour of Russia ({@link WikiArtistAssembler#isRussianAward}). */
    private final Map<String, Boolean> russianAwardCache = new HashMap<>();
    private List<String> musicianTree;
    private OccupationCategories occupationCategories;
    private StateHonours stateHonours;
    private int failedCandidateChunks;
    private int russiaLinkedExcluded;

    /** Without MusicBrainz: links come from Wikidata only. */
    public WikiCollector(WikiHttp http) {
        this(http, null);
    }

    /** @param musicBrainz fills music links Wikidata lacks; null to skip that */
    public WikiCollector(WikiHttp http, MusicBrainzSource musicBrainz) {
        this.http = http;
        this.musicBrainz = musicBrainz;
        this.places = new PlaceResolver(ids -> entities(ids, "labels|claims", false));
    }

    /**
     * Musicians and ensembles. People are asked per rule, a chunk of their occupation tree at a
     * time; ensembles are one query. An item found by several queries keeps the group of the first.
     */
    @Override
    public List<WikiCandidate> listCandidates() {
        loadOccupationTrees();
        Map<String, WikiCandidate> byId = new LinkedHashMap<>();
        CandidateLister lister = new CandidateLister(this::sparql);
        addAll(byId, lister.list(CandidateGroup.MUSIC, musicianTree, WikiQueries.CITIZEN,
                CandidateLister.MUSIC_CITIZEN_CHUNK));
        addAll(byId, lister.list(CandidateGroup.MUSIC, musicianTree, WikiQueries.BORN,
                CandidateLister.MUSIC_BORN_CHUNK));
        addAll(byId, SparqlParser.parseCandidates(sparql(WikiQueries.ensembles()), CandidateGroup.ENSEMBLE));
        failedCandidateChunks = lister.failedChunks();
        return new ArrayList<>(byId.values());
    }

    /** Occupations skipped by the last {@link #listCandidates()} because their query kept failing. */
    @Override
    public int failedCandidateChunks() {
        return failedCandidateChunks;
    }

    /** Items {@link #fetchDetails} left out so far as linked to Russia. */
    @Override
    public int russiaLinkedExcluded() {
        return russiaLinkedExcluded;
    }

    /** Occupation → kind, from the trees this instance fetched. */
    OccupationCategories occupationCategories() {
        loadOccupationTrees();
        return occupationCategories;
    }

    private void loadOccupationTrees() {
        if (occupationCategories != null) {
            return;
        }
        Set<String> musicians = new TreeSet<>();
        for (String root : OccupationCategories.MUSICIAN_ROOTS) {
            musicians.addAll(occupationTree(root, List.of()));
        }
        musicianTree = List.copyOf(musicians);
        occupationCategories = OccupationCategories.fromMusicianTree(musicianTree);
        log.info("[WIKI] occupation tree: musician {}", musicianTree.size());
    }

    /** Sorted, so the chunks are the same from run to run. */
    private List<String> occupationTree(String root, List<String> excludedRoots) {
        return SparqlParser.parseIds(sparql(WikiQueries.occupationTree(root, excludedRoots)), "occ").stream()
                .distinct()
                .sorted()
                .toList();
    }

    private static void addAll(Map<String, WikiCandidate> byId, List<WikiCandidate> found) {
        found.forEach(candidate -> byId.putIfAbsent(candidate.qid(), candidate));
        log.info("[WIKI] got {} items, {} unique so far", found.size(), byId.size());
    }

    private String sparql(String query) {
        return get(SPARQL + encode(query));
    }

    /**
     * Wikidata items with their places, genres, awards, kinds, article intro, photo and albums. Items linked to Russia
     * (citizenship, or an award of {@link WikiArtistAssembler#isRussianAward}) are left out and counted.
     */
    @Override
    public List<WikiArtistData> fetchDetails(List<WikiCandidate> candidates) {
        Map<String, CandidateGroup> groups = new LinkedHashMap<>();
        candidates.forEach(candidate -> groups.putIfAbsent(candidate.qid(), candidate.group()));
        Map<String, WikiEntity> items = entities(groups.keySet(), "labels|descriptions|claims|sitelinks", true);

        Set<String> placeIds = new LinkedHashSet<>();
        Set<String> labelIds = new LinkedHashSet<>();
        Set<String> awardIds = new LinkedHashSet<>();
        for (WikiEntity item : items.values()) {
            Stream.of(item.birthPlaces(), item.formationPlaces(), item.residences(), item.workPlaces())
                    .forEach(placeIds::addAll);
            labelIds.addAll(item.genres());
            labelIds.addAll(item.movements());
            awardIds.addAll(item.awards());
        }
        Map<String, WikiPlace> resolvedPlaces = places.resolve(placeIds);
        Map<String, WikiEntity> labels = labels(labelIds);
        Set<String> russianAwards = russianAwards(awardIds);
        OccupationCategories kinds = occupationCategories();

        List<WikiArtistData> assembled = new ArrayList<>();
        for (WikiEntity item : items.values()) {
            if (WikiArtistAssembler.russiaLinked(item, russianAwards)) {
                russiaLinkedExcluded++;
                log.info("[WIKI] left out {} ({}) as linked to Russia: {}", item.qid(), item.ukTitle(),
                        item.citizenships().contains(WikiArtistAssembler.RUSSIA)
                                ? "citizen of Russia"
                                : "awards " + item.awards().stream().filter(russianAwards::contains).toList());
                continue;
            }
            WikiArtistAssembler.assemble(item, groups.get(item.qid()), resolvedPlaces, labels, kinds, places::label,
                    russianAwards).ifPresent(assembled::add);
        }

        Map<String, String> intros = intros(assembled.stream().map(WikiArtistData::wikiTitle).toList());
        List<String> files = assembled.stream().map(WikiArtistData::imageFile).filter(f -> f != null).toList();
        Map<String, WikiImageCredit> credits = new HashMap<>();
        Map<String, String> thumbs = new HashMap<>();
        Map<String, String> fullImages = new HashMap<>();
        images(files, PORTRAIT_WIDTH, credits, thumbs);
        images(files, PORTRAIT_FULL_WIDTH, new HashMap<>(), fullImages);
        Map<String, List<WikiWork>> albums = albums(assembled.stream().map(WikiArtistData::qid).toList());

        return assembled.stream()
                .map(a -> a.withMedia(
                                intros.get(a.wikiTitle()),
                                a.imageFile() == null ? null : thumbs.get(a.imageFile()),
                                a.imageFile() == null ? null : fullImages.get(a.imageFile()),
                                a.imageFile() == null ? null : credits.get(a.imageFile()))
                        .withWorks(albums.getOrDefault(a.qid(), List.of())))
                .map(this::withMusicBrainzLinks)
                .toList();
    }

    /**
     * Albums of the artists keyed by QID, at most {@value #MAX_ALBUMS} each in {@link WorkSelector#ORDER}; albums
     * without a title are left out. Wikidata is asked {@value #ALBUM_BATCH} artists at a time; a chunk that looks too
     * heavy is halved, as in {@link ChunkSplitter}.
     */
    Map<String, List<WikiWork>> albums(List<String> artists) {
        if (artists.isEmpty()) {
            return Map.of();
        }
        List<WorkRow> rows = new ChunkSplitter().fetchAll("albums", artists, ALBUM_BATCH,
                chunk -> SparqlParser.parseWorks(sparql(WikiQueries.albums(chunk))));
        Map<String, List<WikiWork>> albums = new HashMap<>();
        WorkSelector.select(rows.stream().filter(row -> row.title() != null).toList(), MAX_ALBUMS)
                .forEach((artist, selected) -> albums.put(artist, selected.stream().map(WikiCollector::album).toList()));
        return albums;
    }

    private static WikiWork album(WorkRow row) {
        String title = ALBUM_QUALIFIER.matcher(row.title()).replaceFirst("");
        return new WikiWork(row.work(), title.isEmpty() ? row.title() : title, row.year(), null, null, null,
                WikiLinks.articleUrl(row.ukTitle()));
    }

    /**
     * A musician or band with a MusicBrainz link but no YouTube or Spotify gets the music platforms MusicBrainz knows
     * and the item lacks. A failure for one artist is logged and the artist is kept as it is.
     */
    private WikiArtistData withMusicBrainzLinks(WikiArtistData artist) {
        if (musicBrainz == null || musicBrainzFailuresInARow >= MUSICBRAINZ_MAX_FAILURES
                || artist.socialLinks() == null
                || !(artist.categories().contains(WikiCategory.MUSICIAN) || artist.categories().contains(WikiCategory.BAND))) {
            return artist;
        }
        Set<WikiLink.Platform> have = artist.socialLinks().stream().map(WikiLink::getPlatform)
                .collect(Collectors.toSet());
        if (have.contains(WikiLink.Platform.YOUTUBE) && have.contains(WikiLink.Platform.SPOTIFY)) {
            return artist;
        }
        String mbid = artist.socialLinks().stream()
                .filter(l -> l.getPlatform() == WikiLink.Platform.MUSICBRAINZ)
                .map(l -> l.getUrl().substring(l.getUrl().lastIndexOf('/') + 1))
                .findFirst().orElse(null);
        if (mbid == null || !MBID.matcher(mbid).matches()) {
            return artist;
        }
        try {
            List<WikiLink> links = new ArrayList<>(artist.socialLinks());
            String json = musicBrainz.get(mbid);
            musicBrainzFailuresInARow = 0;
            for (WikiLink found : MusicBrainzParser.links(json)) {
                if (have.add(found.getPlatform())) {
                    links.add(found);
                }
            }
            return links.size() == artist.socialLinks().size() ? artist : artist.withSocialLinks(links);
        } catch (RuntimeException e) {
            log.warn("[WIKI] MusicBrainz links skipped for {} ({}): {}", artist.qid(), mbid, e.getMessage());
            if (++musicBrainzFailuresInARow == MUSICBRAINZ_MAX_FAILURES) {
                log.warn("[WIKI] MusicBrainz failed {} times in a row, not asking it again in this run",
                        MUSICBRAINZ_MAX_FAILURES);
            }
            return artist;
        }
    }

    /** Entities in batches; with {@code onlyRequested} the extra entities some responses carry are dropped. */
    private Map<String, WikiEntity> entities(Collection<String> ids, String props, boolean onlyRequested) {
        Map<String, WikiEntity> result = new LinkedHashMap<>();
        for (List<String> batch : batches(List.copyOf(ids), ENTITY_BATCH)) {
            String url = WIKIDATA_API + "?action=wbgetentities&format=json&languages=uk%7Cen&sitefilter=ukwiki%7Cukwikisource"
                    + "&props=" + encode(props) + "&ids=" + encode(String.join("|", batch));
            Map<String, WikiEntity> parsed = WikidataEntityParser.parse(get(url));
            if (onlyRequested) {
                batch.stream().filter(parsed::containsKey).forEach(id -> result.put(id, parsed.get(id)));
            } else {
                result.putAll(parsed);
            }
        }
        return result;
    }

    private Map<String, WikiEntity> labels(Set<String> ids) {
        List<String> unknown = ids.stream().filter(id -> !labelCache.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            labelCache.putAll(entities(unknown, "labels", false));
        }
        Map<String, WikiEntity> result = new HashMap<>();
        ids.stream().filter(labelCache::containsKey).forEach(id -> result.put(id, labelCache.get(id)));
        return result;
    }

    /**
     * The given awards that are state honours of Russia; each award is fetched once per run. The state honour trees,
     * and the awards that degrees belong to, are fetched only for awards of Russia.
     */
    private Set<String> russianAwards(Set<String> ids) {
        List<String> unknown = ids.stream().filter(id -> !russianAwardCache.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            Map<String, WikiEntity> awards = new HashMap<>(entities(unknown, "claims", false));
            List<WikiEntity> ofRussia = awards.values().stream().filter(WikiArtistAssembler::isOfRussia).toList();
            if (!ofRussia.isEmpty()) {
                StateHonours honours = stateHonours();
                Set<String> wholes = ofRussia.stream()
                        .filter(honours::isDegree)
                        .flatMap(award -> award.wholes().stream())
                        .filter(id -> !awards.containsKey(id))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!wholes.isEmpty()) {
                    awards.putAll(entities(wholes, "claims", false));
                }
                for (WikiEntity award : ofRussia) {
                    russianAwardCache.put(award.qid(), WikiArtistAssembler.isRussianAward(award, awards, honours));
                }
            }
            unknown.forEach(id -> russianAwardCache.putIfAbsent(id, false));
        }
        return ids.stream().filter(id -> russianAwardCache.getOrDefault(id, false)).collect(Collectors.toSet());
    }

    /**
     * Classes of state honours, from the subclass trees of {@link StateHonours#ROOTS} and of
     * {@link StateHonours#DEPARTMENTAL}; fetched once per run.
     */
    StateHonours stateHonours() {
        if (stateHonours == null) {
            List<String> classes = new ArrayList<>();
            for (String root : StateHonours.ROOTS) {
                classes.addAll(SparqlParser.parseIds(
                        sparql(WikiQueries.occupationTree(root, StateHonours.excludedBelow(root))), "occ"));
            }
            List<String> departmental = SparqlParser.parseIds(
                    sparql(WikiQueries.occupationTree(StateHonours.DEPARTMENTAL, List.of())), "occ");
            stateHonours = new StateHonours(classes, departmental);
            log.info("[WIKI] state honour classes: {}", stateHonours.size());
        }
        return stateHonours;
    }

    private Map<String, String> intros(List<String> titles) {
        Map<String, String> intros = new HashMap<>();
        for (List<String> batch : batches(titles.stream().filter(t -> t != null).distinct().toList(), TITLE_BATCH)) {
            String url = UK_WIKI_API + "?action=query&format=json&formatversion=2&prop=extracts&exintro=1"
                    + "&explaintext=1&exlimit=" + TITLE_BATCH + "&redirects=1&titles=" + encode(String.join("|", batch));
            intros.putAll(MediaWikiParser.parseIntros(get(url)));
        }
        return intros;
    }

    private void images(List<String> files, int width, Map<String, WikiImageCredit> credits, Map<String, String> thumbs) {
        for (List<String> batch : batches(files.stream().filter(Objects::nonNull).distinct().toList(), TITLE_BATCH)) {
            String titles = String.join("|", batch.stream().map(f -> "File:" + f).toList());
            String url = COMMONS_API + "?action=query&format=json&formatversion=2&prop=imageinfo"
                    + "&iiprop=url%7Cextmetadata&iiurlwidth=" + width
                    + "&iiextmetadatafilter=Artist%7CLicenseShortName%7CLicenseUrl&titles=" + encode(titles);
            String json = get(url);
            credits.putAll(MediaWikiParser.parseImageCredits(json));
            thumbs.putAll(MediaWikiParser.parseThumbUrls(json));
        }
    }

    /**
     * MediaWiki and Wikidata report failures as {"error": {...}} with HTTP 200. Treating that as an empty answer would
     * save artists without a biography or photo for good, so the chunk fails instead and is retried next run.
     */
    private String get(String url) {
        String json = http.get(url);
        if (json.contains("\"error\"")) {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject() && root.getAsJsonObject().has("error")) {
                JsonObject error = root.getAsJsonObject().getAsJsonObject("error");
                String code = error.has("code") ? error.get("code").getAsString() : "unknown";
                String info = error.has("info") ? error.get("info").getAsString() : "";
                throw new WikiFetchException("API error " + code + ": " + info);
            }
        }
        return json;
    }

    static <T> List<List<T>> batches(List<T> items, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            batches.add(items.subList(i, Math.min(items.size(), i + size)));
        }
        return batches;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

}
