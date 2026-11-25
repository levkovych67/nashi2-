package com.nashi.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.domain.ArtistWork;
import com.nashi.domain.ImageCredit;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.util.WikiLinks;
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
    static final int BIOGRAPHY_MAX = 1500;
    /** The API rounds 400 up to a standard 500 px thumbnail. */
    static final int PORTRAIT_WIDTH = 400;
    /** Creators per works query, as in the 2026-11-07 research (heaviest batch about 10 s). */
    static final int WORK_BATCH = 200;
    static final int MAX_WORKS = 12;
    /** A visual artist with fewer works than this on Wikidata gets more from their Commons category. */
    static final int COMMONS_BELOW = 4;

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
    private List<String> visualTree;
    private List<String> writerTree;
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
     * Musicians, ensembles, visual artists, writers. People are asked per rule, a chunk of their occupation tree at a
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
        addAll(byId, lister.list(CandidateGroup.VISUAL, visualTree, WikiQueries.CITIZEN,
                CandidateLister.VISUAL_CITIZEN_CHUNK));
        addAll(byId, lister.list(CandidateGroup.VISUAL, visualTree, WikiQueries.BORN,
                CandidateLister.VISUAL_BORN_CHUNK));
        addAll(byId, lister.list(CandidateGroup.WRITER, writerTree, WikiQueries.CITIZEN,
                CandidateLister.WRITER_CITIZEN_CHUNK));
        addAll(byId, lister.list(CandidateGroup.WRITER, writerTree, WikiQueries.BORN,
                CandidateLister.WRITER_BORN_CHUNK));
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
        visualTree = occupationTree(OccupationCategories.VISUAL_ROOT, OccupationCategories.VISUAL_EXCLUDED);
        writerTree = occupationTree(OccupationCategories.WRITER_ROOT, OccupationCategories.WRITER_EXCLUDED);
        occupationCategories = OccupationCategories.fromTrees(musicianTree, visualTree, writerTree);
        log.info("[WIKI] occupation trees: musician {}, visual artist {}, writer {}; {} occupations mapped to a kind",
                musicianTree.size(), visualTree.size(), writerTree.size(), occupationCategories.size());
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
     * Wikidata items with their places, genres, awards, kinds, article intro, photo and works. Items linked to Russia
     * (citizenship, or an award of {@link WikiArtistAssembler#isRussianAward}) are left out and counted.
     */
    @Override
    public List<WikiArtistData> fetchDetails(List<WikiCandidate> candidates) {
        Map<String, CandidateGroup> groups = new LinkedHashMap<>();
        candidates.forEach(candidate -> groups.putIfAbsent(candidate.qid(), candidate.group()));
        Map<String, WikiEntity> items = entities(groups.keySet(), "labels|claims|sitelinks", true);

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
        Map<String, ImageCredit> credits = new HashMap<>();
        Map<String, String> thumbs = new HashMap<>();
        images(files, PORTRAIT_WIDTH, credits, thumbs);

        Map<String, List<ArtistCategory>> categoriesByQid = new LinkedHashMap<>();
        Map<String, String> commonsCategoryByQid = new HashMap<>();
        for (WikiArtistData artist : assembled) {
            categoriesByQid.put(artist.qid(), artist.categories());
            String category = items.get(artist.qid()).commonsCategory();
            if (category != null) {
                commonsCategoryByQid.put(artist.qid(), category);
            }
        }
        Map<String, List<ArtistWork>> works = works(categoriesByQid, commonsCategoryByQid);

        return assembled.stream()
                .map(a -> a.withMedia(
                                MediaWikiParser.truncate(intros.get(a.wikiTitle()), BIOGRAPHY_MAX),
                                a.imageFile() == null ? null : thumbs.get(a.imageFile()),
                                a.imageFile() == null ? null : credits.get(a.imageFile()))
                        .withWorks(works.getOrDefault(a.qid(), List.of())))
                .map(this::withMusicBrainzLinks)
                .toList();
    }

    /**
     * A musician or band with a MusicBrainz link but no YouTube or Spotify gets the music platforms MusicBrainz knows
     * and the item lacks. A failure for one artist is logged and the artist is kept as it is.
     */
    private WikiArtistData withMusicBrainzLinks(WikiArtistData artist) {
        if (musicBrainz == null || musicBrainzFailuresInARow >= MUSICBRAINZ_MAX_FAILURES
                || artist.socialLinks() == null
                || !(artist.categories().contains(ArtistCategory.MUSICIAN) || artist.categories().contains(ArtistCategory.BAND))) {
            return artist;
        }
        Set<SocialLink.Platform> have = artist.socialLinks().stream().map(SocialLink::getPlatform)
                .collect(Collectors.toSet());
        if (have.contains(SocialLink.Platform.YOUTUBE) && have.contains(SocialLink.Platform.SPOTIFY)) {
            return artist;
        }
        String mbid = artist.socialLinks().stream()
                .filter(l -> l.getPlatform() == SocialLink.Platform.MUSICBRAINZ)
                .map(l -> l.getUrl().substring(l.getUrl().lastIndexOf('/') + 1))
                .findFirst().orElse(null);
        if (mbid == null || !MBID.matcher(mbid).matches()) {
            return artist;
        }
        try {
            List<SocialLink> links = new ArrayList<>(artist.socialLinks());
            String json = musicBrainz.get(mbid);
            musicBrainzFailuresInARow = 0;
            for (SocialLink found : MusicBrainzParser.links(json)) {
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

    /**
     * Works of the given people, keyed by QID in the order of {@code categoriesByQid}; people without any are left
     * out.
     * <ul>
     *   <li>{@code VISUAL_ARTIST}: works with an image on Wikidata (P170 + P18); with fewer than {@value
     *   #COMMONS_BELOW} of them and a Commons category, files of its works subcategories ({@link CommonsWorks}) follow,
     *   {@value #MAX_WORKS} works in all.</li>
     *   <li>{@code WRITER}: notable works (P800) and works (P50) with a uk.wikipedia article or a uk.wikisource text.
     *   </li>
     * </ul>
     * Each kind is chosen by {@link WorkSelector}, at most {@value #MAX_WORKS}. A person of both kinds gets both, taking
     * turns in the order of their categories, {@value #MAX_WORKS} in all; a work found as both is kept as the visual
     * one. Wikidata is asked {@value #WORK_BATCH} people at a time; a batch that looks too heavy is halved, as in
     * {@link ChunkSplitter}. Images are Commons thumbnails of {@value WorkImages#FULL_WIDTH} px with a preview from
     * {@link WorkImages#previewUrl}; a visual work whose image cannot be found is left out, a gallery tile without an
     * image being of no use.
     *
     * @param categoriesByQid      the kinds of each person, in their order
     * @param commonsCategoryByQid P373 of the people who have one
     */
    public Map<String, List<ArtistWork>> works(Map<String, List<ArtistCategory>> categoriesByQid,
                                               Map<String, String> commonsCategoryByQid) {
        List<String> painters = withCategory(categoriesByQid, ArtistCategory.VISUAL_ARTIST);
        List<String> writers = withCategory(categoriesByQid, ArtistCategory.WRITER);
        ChunkSplitter splitter = new ChunkSplitter();
        Map<String, List<WorkRow>> visual = WorkSelector.select(splitter.fetchAll("works of visual artists", painters,
                WORK_BATCH, chunk -> SparqlParser.parseWorks(sparql(WikiQueries.visualWorks(chunk)))), MAX_WORKS);
        Map<String, List<WorkRow>> written = WorkSelector.select(splitter.fetchAll("works of writers", writers,
                WORK_BATCH, chunk -> SparqlParser.parseWorks(sparql(WikiQueries.writtenWorks(chunk)))), MAX_WORKS);

        CommonsWorks commons = new CommonsWorks(this::get);
        Map<String, List<String>> commonsFiles = new HashMap<>();
        List<String> files = new ArrayList<>();
        for (String qid : painters) {
            List<WorkRow> own = visual.getOrDefault(qid, List.of());
            Set<String> shown = own.stream().map(WorkRow::imageFile).collect(Collectors.toSet());
            files.addAll(shown);
            String category = commonsCategoryByQid.get(qid);
            if (category != null && own.size() < COMMONS_BELOW) {
                // Asked for 12, so files already shown by a Wikidata work do not leave the total short.
                List<String> more = commons.files(category, MAX_WORKS).stream()
                        .filter(file -> !shown.contains(file))
                        .limit(MAX_WORKS - own.size())
                        .toList();
                commonsFiles.put(qid, more);
                files.addAll(more);
            }
        }
        Map<String, ImageCredit> credits = new HashMap<>();
        Map<String, String> thumbs = new HashMap<>();
        images(files, WorkImages.FULL_WIDTH, credits, thumbs);

        Map<String, List<ArtistWork>> works = new LinkedHashMap<>();
        int fromCommons = 0;
        for (Map.Entry<String, List<ArtistCategory>> entry : categoriesByQid.entrySet()) {
            String qid = entry.getKey();
            List<ArtistWork> paintings = new ArrayList<>();
            visual.getOrDefault(qid, List.of()).stream()
                    .filter(row -> thumbs.get(row.imageFile()) != null)
                    .forEach(row -> paintings.add(work(row, thumbs, credits)));
            for (String file : commonsFiles.getOrDefault(qid, List.of())) {
                if (thumbs.get(file) != null) {
                    paintings.add(commonsWork(file, thumbs, credits));
                    fromCommons++;
                }
            }
            Set<String> painted = paintings.stream().map(ArtistWork::getWikidataId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<ArtistWork> writings = written.getOrDefault(qid, List.of()).stream()
                    .filter(row -> !painted.contains(row.work()))
                    .map(row -> work(row, thumbs, credits))
                    .toList();
            List<List<ArtistWork>> kinds = new ArrayList<>();
            for (ArtistCategory category : new LinkedHashSet<>(categoriesOf(entry))) {
                if (category == ArtistCategory.VISUAL_ARTIST) {
                    kinds.add(paintings);
                } else if (category == ArtistCategory.WRITER) {
                    kinds.add(writings);
                }
            }
            List<ArtistWork> mixed = WorkSelector.interleave(kinds, MAX_WORKS);
            if (!mixed.isEmpty()) {
                works.put(qid, mixed);
            }
        }
        log.info("[WIKI] works: {} visual artists and {} writers asked, {} have works ({} works from Commons); "
                + "{} creators skipped after failed queries", painters.size(), writers.size(), works.size(),
                fromCommons, splitter.skippedItems());
        return works;
    }

    private static List<String> withCategory(Map<String, List<ArtistCategory>> categoriesByQid,
                                             ArtistCategory category) {
        return categoriesByQid.entrySet().stream()
                .filter(entry -> categoriesOf(entry).contains(category))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<ArtistCategory> categoriesOf(Map.Entry<String, List<ArtistCategory>> entry) {
        return entry.getValue() == null ? List.of() : entry.getValue();
    }

    /** The article about the work, else its text on Wikisource. */
    private static ArtistWork work(WorkRow row, Map<String, String> thumbs, Map<String, ImageCredit> credits) {
        String full = row.imageFile() == null ? null : thumbs.get(row.imageFile());
        return ArtistWork.builder()
                .wikidataId(row.work())
                .title(row.title())
                .year(row.year())
                .imageUrl(WorkImages.previewUrl(full))
                .fullImageUrl(full)
                .imageCredit(row.imageFile() == null ? null : credits.get(row.imageFile()))
                .articleUrl(row.ukTitle() != null
                        ? WikiLinks.articleUrl(row.ukTitle())
                        : WikiLinks.wikisourceUrl(row.wikisourceTitle()))
                .build();
    }

    /** A work known only by its file: no QID, year or article. */
    private static ArtistWork commonsWork(String file, Map<String, String> thumbs, Map<String, ImageCredit> credits) {
        String full = thumbs.get(file);
        return ArtistWork.builder()
                .title(CommonsWorks.workTitle(file))
                .imageUrl(WorkImages.previewUrl(full))
                .fullImageUrl(full)
                .imageCredit(credits.get(file))
                .build();
    }

    private void images(List<String> files, int width, Map<String, ImageCredit> credits, Map<String, String> thumbs) {
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
