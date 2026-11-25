# Wikipedia Artist Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> Owner chose: native execution in one session, one fresh whole-branch review at the end (same as the Aero project).

**Goal:** a backend job that imports every Ukrainian music artist with a uk.wikipedia article that is not yet in the
database, and a map that shows thousands of artists smoothly, with license attribution on the artist page.

**Architecture:** a Spring-free collector in `com.nashi.wiki` lists candidates with a few SPARQL queries, fetches
details with `wbgetentities` (50 per call), intros from uk.wikipedia and photo credits from Commons, and assembles
`WikiArtistData`; `ArtistImportService` filters known artists, maps the rest to `Artist`, saves them as `APPROVED`,
and reports to the admin chat; a monthly `@Scheduled` trigger and a `/wiki_import` Telegram command start it. The
frontend clusters pins with `supercluster` and shows the attribution.

**Tech Stack:** Java 17, Spring Boot 3.4, MongoDB, Gson, `java.net.http.HttpClient`, JUnit 5 + Mockito (from
`spring-boot-starter-test`); React 19, react-map-gl/MapLibre, supercluster 8, `node:test`.

**Spec:** `docs/superpowers/specs/2026-11-04-wikipedia-import-design.md` (in `nashi-backend`)

**Repos:** backend `~/development/nashi/nashi-backend` on `feat/wiki-import`; frontend
`~/development/nashi/nashi-frontend` on `feat/wiki-artists-map`. Tasks 1–5 backend, 6–8 frontend.

## Global Constraints

- **Never start the app or a Spring context**: no `bootRun`, no `@SpringBootTest`, no `@DataMongoTest`, no slice tests.
  `application.properties` holds the live Telegram token and the live Mongo URI. Only plain JUnit 5 tests.
- Never print values from `application.properties`. Do not edit it; new settings have defaults in code.
- Backend commands run with `JAVA_HOME=$(/usr/libexec/java_home -v 17)`. Test: `./gradlew test`.
- No new backend dependencies. HTTP: `java.net.http.HttpClient`; JSON: Gson (already present).
- User-Agent on every Wikimedia request: `NashiWikiImport/1.0 (https://github.com/levkovych67/nashi-frontend)` —
  never a personal email.
- Requests strictly sequential, ≥200 ms between them; retry 429/503/504 up to 4 times, waiting `Retry-After` seconds
  if present, else 2 s, 4 s, 8 s, 16 s; SPARQL request timeout 70 s.
- Import never calls `ArtistApprovalService.approveArtist`, never posts to the Telegram channel, never sends one
  message per artist. One admin-chat report per run.
- Imported artist: `status = APPROVED`, `source = WIKIPEDIA`.
- People rule (owner's decision): human (Q5); occupation musician Q639669, singer Q177220 or composer Q36834 incl.
  subclasses; (citizen of Ukraine Q212 OR born in a place whose country P17 is Q212); NOT a citizen of Russia Q159.
- Ensemble rule: instance of musical ensemble Q2088357 incl. subclasses; country of origin P495 = Q212 OR formation
  place P740 has country Q212.
- Both rules require a uk.wikipedia article.
- Frontend: Frutiger Aero classes from `src/styles/aero.css`; Ukrainian copy; no new npm packages
  (`supercluster` is already installed).
- No push to `main` in either repo without the owner's word (push = production deploy).

## Review Focus

1. A Wikidata place whose `P131` chain loops or passes through a historical unit (e.g. a governorate) must not hang
   or throw; it resolves to no region and the next place is tried.
2. A SPARQL 504 or a Wikimedia outage mid-run must end the run with a failure report, keep artists already saved,
   and leave the lock released so the next run can start.
3. Two Wikipedia people with the same Ukrainian name in one run: both saved, the second slug gets `-q<number>`; no
   `DuplicateKeyException` escapes and stops the run.
4. A `/wiki_import` text from any chat other than the admin chat must not start an import.
5. ~4k pins including hundreds at one exact coordinate (Kyiv): the map renders only on-screen clusters, the stack
   list scrolls, nothing freezes on a phone.

---

### Task 1: Data model, API fields, null-safe list

**Files:**
- Create: `src/main/java/com/nashi/enums/ArtistSource.java`, `src/main/java/com/nashi/domain/ImageCredit.java`,
  `src/test/java/com/nashi/service/impl/artist/ArtistServiceImplTest.java`
- Modify: `domain/Artist.java`, `dto/artist/ArtistViewDTO.java`, `service/impl/artist/ArtistServiceImpl.java`
  (`toMinifiedDto`, `toViewDto`), `config/MongoIndexConfig.java`, `swagger.json`

**Interfaces:**
- Produces: `enum ArtistSource { WIKIPEDIA }` (null = added through the form);
  `ImageCredit { String author; String license; String licenseUrl; String filePageUrl; }` (Lombok `@Data @Builder
  @NoArgsConstructor @AllArgsConstructor`); `Artist.wikidataId: String`, `Artist.source: ArtistSource`,
  `Artist.imageCredit: ImageCredit`; `ArtistViewDTO.source: ArtistSource`, `ArtistViewDTO.wikiUrl: String`,
  `ArtistViewDTO.imageCredit: ImageCredit`.
- `wikiUrl` = `https://uk.wikipedia.org/wiki/` + title with spaces → `_`, URL-encoded (UTF-8); null when no `wikiTitle`.

- [ ] **Step 1: Write failing tests** in `ArtistServiceImplTest` (construct `ArtistServiceImpl` with Mockito mocks):
  - `getAllArtistsSkipsCoordinatesWhenArtistHasNoLocation`: repository returns a page with one artist, `location = null`
    → no exception; minified DTO latitude/longitude null.
  - `viewDtoExposesWikipediaSourceLinkAndImageCredit`: artist with `wikiTitle = "Океан Ельзи"`, `source = WIKIPEDIA`,
    an `ImageCredit` → DTO `wikiUrl = "https://uk.wikipedia.org/wiki/%D0%9E%D0%BA%D0%B5%D0%B0%D0%BD_%D0%95%D0%BB%D1%8C%D0%B7%D0%B8"`,
    `source = WIKIPEDIA`, same credit.
- [ ] **Step 2:** `./gradlew test` → FAIL (compile errors: missing fields).
- [ ] **Step 3:** add the enum, `ImageCredit`, the three `Artist` fields (`wikidataId` with
  `@Indexed(unique = true, sparse = true)`), DTO fields and mapping; make `toMinifiedDto` null-safe; in
  `MongoIndexConfig` create `wikidataId` index `unique + sparse`, name `wikidataId_unique`, in its own try/catch.
  Add the three fields to `ArtistViewDTO` and `ImageCredit` schema in `swagger.json`.
- [ ] **Step 4:** `./gradlew test` → PASS.
- [ ] **Step 5:** commit `feat(wiki): artist source, wikidata id and image credit`.

### Task 2: Polite HTTP client and response parsers

**Files:**
- Create in `src/main/java/com/nashi/wiki/`: `WikiHttp.java`, `PoliteWikiHttp.java`, `WikiEntity.java`,
  `WikidataEntityParser.java`, `MediaWikiParser.java`, `SparqlParser.java`
- Create tests in `src/test/java/com/nashi/wiki/` and fixtures in `src/test/resources/wiki/` (real responses
  captured with curl: `wbgetentities` for Q172425 Лисенко, Q1980296 Кобзон, Океан Ельзи and the place chain
  Гриньки → its `P131` parents; one extracts response; one Commons imageinfo response; one SPARQL list response)

**Interfaces:**
- `interface WikiHttp { String get(String url) throws WikiFetchException; }` — `WikiFetchException extends
  ExternalServiceException` (existing custom exception base).
- `PoliteWikiHttp(HttpClient client, Sleeper sleeper)`; `interface Sleeper { void sleep(long millis); }` so tests do
  not wait. Constants from Global Constraints.
- `record WikiEntity(String qid, String labelUk, String labelEn, String ukTitle, Set<String> instanceOf,
  Set<String> citizenships, List<String> birthPlaces, List<String> formationPlaces, List<String> residences,
  List<String> workPlaces, Integer inception, Integer dissolved, Integer workStart, Integer workEnd,
  String imageFile, List<String> genres, Map<String,String> socialIds, Double lat, Double lon,
  List<String> partOf)` — `socialIds` keys are property ids `P856 P2003 P2013 P2397 P1902 P3040`; `partOf` = `P131`
  values (preferred rank first, deprecated skipped); years from the time value's year.
- `WikidataEntityParser.parse(String wbgetentitiesJson): Map<String, WikiEntity>` (missing entities skipped).
- `MediaWikiParser.parseIntros(String json): Map<String,String>` keyed by the requested title (follow `normalized`
  and `redirects`); `MediaWikiParser.truncate(String text, int max): String` — cut at the last sentence end
  (`. ! ?`) ≤ max, append `…`; `parseImageCredits(String json): Map<String, ImageCredit>` keyed by file name
  without `File:`; author with HTML stripped; plus thumb URL: `parseThumbUrls(String json): Map<String,String>`.
- `SparqlParser.parseCandidates(String json): List<WikiCandidate>`; `record WikiCandidate(String qid, String ukTitle)`.

- [ ] **Step 1:** capture fixtures (curl with the User-Agent, `languages=uk|en`, `sitefilter=ukwiki`).
- [ ] **Step 2: Write failing tests:**
  - `PoliteWikiHttpTest` (JDK `com.sun.net.httpserver.HttpServer` on port 0): sends the exact User-Agent;
    429 with `Retry-After: 3` then 200 → returns body, sleeper saw 3000; five 503s → `WikiFetchException`;
    404 → `WikiFetchException` without retry.
  - `WikidataEntityParserTest`: Лисенко — citizenships contain Q34266 and not Q212, birth place Гриньки QID,
    ukTitle present; Кобзон — citizenships contain Q159; Океан Ельзи — instanceOf non-empty, inception 1994,
    a genre QID, `socialIds` has P856.
  - `MediaWikiParserTest`: intro for a redirected title is keyed by the requested title; `truncate` of a 2,000-char
    text with max 1,500 ends with `.…` and length ≤ 1,501; credit author has no `<`.
  - `SparqlParserTest`: QIDs extracted from entity URIs, titles kept.
- [ ] **Step 3:** `./gradlew test` → FAIL. **Step 4:** implement. **Step 5:** `./gradlew test` → PASS.
- [ ] **Step 6:** commit `feat(wiki): polite Wikimedia client and parsers`.

### Task 3: Regions, places and the artist assembler

**Files:** create in `com.nashi.wiki`: `UkraineRegions.java`, `PlaceResolver.java`, `WikiPlace.java`,
`WikiArtistData.java`, `WikiArtistAssembler.java`, `NameNormalizer.java`; tests alongside.

**Interfaces:**
- `UkraineRegions.fromQid(String qid): Optional<Region>` — the 27-row table: Q178269 IVANO_FRANKIVSK, Q756294 CRIMEA,
  Q167859 VOLYN, Q166709 VINNYTSIA, Q170672 DNIPROPETROVSK, Q2012050 DONETSK, Q40637 ZHYTOMYR, Q170213 ZAKARPATTIA,
  Q171334 ZAPORIZHZHIA, Q1899 KYIV, Q170036 KYIV, Q180981 KIROVOHRAD, Q171965 LUHANSK, Q164193 LVIV, Q181633 MYKOLAIV,
  Q171852 ODESA, Q169501 POLTAVA, Q174187 RIVNE, Q7525 CRIMEA, Q170446 SUMY, Q173407 TERNOPIL, Q170666 KHARKIV,
  Q163271 KHERSON, Q171331 KHMELNYTSKYI, Q161808 CHERKASY, Q168856 CHERNIVTSI, Q167874 CHERNIHIV.
- `record WikiPlace(String qid, String labelUk, double lat, double lon, Region region)`.
- `PlaceResolver(Function<Collection<String>, Map<String, WikiEntity>> fetchEntities)`:
  `Map<String, WikiPlace> resolve(Collection<String> placeQids)` — breadth-first up `partOf`, max depth 8, visited set,
  every entity fetched once (cache); a place resolves only if it has coordinates and its chain (or itself) hits the
  table.
- `record WikiArtistData(String qid, String name, String wikiTitle, boolean ensemble, String biography, String style,
  Set<String> tags, Integer foundationYear, Integer disbandYear, WikiPlace place, String imageFile,
  List<SocialLink> socialLinks, String imageUrl, ImageCredit imageCredit)` — the assembler leaves `biography`,
  `imageUrl`, `imageCredit` null; the collector fills them with `WikiArtistData withMedia(String biography,
  String imageUrl, ImageCredit imageCredit)`.
- `WikiArtistAssembler.assemble(WikiEntity e, Map<String,WikiPlace> places, Map<String,WikiEntity> genres):
  Optional<WikiArtistData>` — empty when `citizenships` contains Q159. Name: `labelUk`, else `ukTitle` without a
  trailing ` (…)`. Place: ensemble → first resolvable of `formationPlaces`; person → first resolvable of
  `birthPlaces`, `residences`, `workPlaces` in that order; else null. Years: ensemble inception/dissolved, person
  workStart/workEnd. Style: first genre `labelUk` (fallback `labelEn`); tags: genre `labelEn` lower-cased.
  Social links: P856 → WEBSITE as is; P2003 → `https://www.instagram.com/{id}/`; P2013 → `https://www.facebook.com/{id}`;
  P2397 → `https://www.youtube.com/channel/{id}`; P1902 → `https://open.spotify.com/artist/{id}`;
  P3040 → `https://soundcloud.com/{id}`; plus WIKI → the article URL (Task 1 rule). Ensemble = `instanceOf` is not
  exactly `{Q5}`.
- `NameNormalizer.normalize(String): String` — lower-case, drop `« » " „ “ ”`, map `’ ʼ ‘ \`` to `'`, collapse spaces, trim.

- [ ] **Step 1: Write failing tests:**
  - `UkraineRegionsTest`: 27 entries; every `Region` value is reached; Q1899 → KYIV, Q7525 → CRIMEA.
  - `PlaceResolverTest` (fixture-backed fetch from Task 2 plus synthetic entities): Гриньки → POLTAVA with its
    coordinates; a place in Russia → absent; a two-node `partOf` loop → absent and terminates; each QID fetched once.
  - `WikiArtistAssemblerTest`: Кобзон → empty; Лисенко → POLTAVA place; person whose birth place is abroad but
    residence in Lviv → LVIV; no resolvable place → place null; ensemble uses formation place, not birth places;
    tags `["rock"]` for genre labelEn "Rock"; Instagram URL format; WIKI link present.
  - `NameNormalizerTest`: `«Океан Ельзи»` equals `океан  ельзи`; `Кам’яний` equals `Кам'яний`.
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS.
- [ ] **Step 5:** commit `feat(wiki): regions, places and artist assembler`.

### Task 4: Collector and dry run

**Files:** create `com.nashi.wiki.WikiCollector`, `com.nashi.wiki.WikiQueries`, `com.nashi.wiki.WikiDryRun`;
modify `build.gradle` (task `wikiDryRun`); test `WikiCollectorTest`.

**Interfaces:**
- `WikiQueries`: six people queries (3 roots × {citizen of Q212, born in a place with P17 = Q212}, each with
  `FILTER NOT EXISTS { ?p wdt:P27 wd:Q159 }`) and one ensemble query; each selects `?item ?title` where `?title` is
  the ukwiki `schema:name`.
- `WikiCollector(WikiHttp http)`:
  - `List<WikiCandidate> listCandidates()` — runs the seven queries, de-duplicates by QID, keeps insertion order.
  - `List<WikiArtistData> fetchDetails(List<WikiCandidate> candidates)` — entities in batches of 50; places through
    `PlaceResolver`; genres in batches of 50; intros in batches of 20 (truncate 1,500); Commons credits and 400 px
    thumbs in batches of 50; assembled via `WikiArtistAssembler`, then `withMedia(...)`.
- `WikiDryRun.main(String[] args)` — no Spring; runs both steps with `PoliteWikiHttp`; writes
  `build/wiki-dry-run.json` (array of `WikiArtistData`) and prints counts: candidates, assembled, with place, per
  region, with image. Gradle: `tasks.register('wikiDryRun', JavaExec) { classpath = sourceSets.main.runtimeClasspath;
  mainClass = 'com.nashi.wiki.WikiDryRun' }`.

- [ ] **Step 1: Write failing test** `WikiCollectorTest` with a fake `WikiHttp` that routes by URL to fixtures:
  duplicate QIDs across queries appear once; `fetchDetails` for Лисенко returns name, POLTAVA, a biography ≤ 1,501
  chars, image URL and credit; Кобзон absent.
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS.
- [ ] **Step 5:** run `./gradlew wikiDryRun` live; expect ≥ 3,000 assembled, ≥ 60 % with a place, every region
  present. Record the printed counts in the ledger. Copy the JSON to the scratchpad for Task 8.
- [ ] **Step 6:** commit `feat(wiki): candidate collector and dry run`.

### Task 5: Import service, triggers and report

**Files:** create in `com.nashi.wiki`: `ArtistStore.java`, `MongoArtistStore.java`, `KnownArtists.java`,
`ImportReport.java`, `ArtistImportService.java`, `AdminNotifier.java`, `WikiImportRequested.java`,
`WikiImportScheduler.java`; modify `bot/TelegramBot.java`, `NashiApplication.java`; tests
`ArtistImportServiceTest`, `TelegramWikiCommandTest` (if `TelegramBot` cannot be built without a token, test the
extracted `isWikiImportCommand(chatId, text, adminChatId)` static method instead).

**Interfaces:**
- `record KnownArtists(Set<String> wikidataIds, Set<String> wikiTitles, Set<String> manualNames)` — `manualNames`
  normalized, from artists with no `wikidataId` and `status != REJECTED`.
- `interface ArtistStore { KnownArtists loadKnown(); boolean slugExists(String slug); Artist save(Artist a); }`;
  `MongoArtistStore` reads only `wikidataId, wikiTitle, name, status` fields.
- `record ImportReport(int candidates, int alreadyKnown, int added, int withoutPlace, int errors, String failure)`
  with `String toTelegramText()` in Ukrainian, e.g. `Імпорт з Вікіпедії: знайдено 3912, уже були 120, додано 3780
  (без місця на карті 950), помилок 12.` or `Імпорт з Вікіпедії впав: <failure>. Збережено до збою: N.`
- `interface AdminNotifier { void send(String text); }` — implemented by a `TelegramAdminNotifier` bean calling a new
  `TelegramBot.sendAdminText(String)`.
- `ArtistImportService(WikiCollector, ArtistStore, AdminNotifier)`:
  - `ImportReport runNow()` — synchronous, used by tests;
  - `boolean startInBackground()` — single-thread executor + `AtomicBoolean`; false if already running (then
    notifier sends `Імпорт уже йде.`); on start sends `Імпорт з Вікіпедії запущено.`;
  - `@EventListener onRequested(WikiImportRequested e)` → `startInBackground()`.
  - Flow: `loadKnown` → drop candidates by QID or title → `fetchDetails` → drop by normalized name in `manualNames`
    → map → slug = existing `toSlug` rule (move it to `StringUtils.toSlug`, used by `ArtistBeforeConvertCallback`
    too), `-q<digits>` suffix when `slugExists` or used earlier in the run → `save`; a save exception counts as an
    error and the run continues; an exception in listing/fetching ends the run with `failure`. Report always sent;
    lock always released.
- `TelegramBot`: text `/wiki_import` or `/wiki_import@<anything>` from `adminChatId` → publish
  `WikiImportRequested`; same text from another chat → ignored; any other text → `alive` as before.
- `WikiImportScheduler`: `@Scheduled(cron = "${nashi.wiki-import.cron:0 0 4 1 * *}", zone = "Europe/Kyiv")`, runs
  only when `${nashi.wiki-import.enabled:true}`; `@EnableScheduling` on `NashiApplication`.

- [ ] **Step 1: Write failing tests** `ArtistImportServiceTest` (in-memory `FakeArtistStore`, fake collector,
  recording notifier):
  - known QID and known title are not passed to `fetchDetails`;
  - a manual non-rejected artist «Океан Ельзи» blocks the wiki one; a REJECTED one does not;
  - two wiki people both named «Іван Петренко» → both saved, second slug ends with `-q<digits>`;
  - saved artist: `APPROVED`, `WIKIPEDIA`, `wikidataId`, location `GeoJsonPoint(lon, lat)`, region, city, imageKeys =
    `[imageUrl]`, imageCredit;
  - artist without place saved with `location == null` and counted in `withoutPlace`;
  - store throws on one save → `errors = 1`, others saved;
  - collector throws → report `failure` set, notifier got the failure text, `startInBackground()` works again after;
  - second `startInBackground()` while the first runs (latch in the fake collector) → false.
  - `isWikiImportCommand`: admin chat + `/wiki_import@nashi_bot` → true; other chat → false; `hello` → false.
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS (whole suite).
- [ ] **Step 5:** add a «Імпорт з Вікіпедії» section to backend `CLAUDE.md` (how to run, the no-Spring-tests rule,
  `/wiki_import`, dry run); commit `feat(wiki): import service, monthly schedule and /wiki_import`.

### Task 6: Frontend — types and attribution on the artist page

**Repo:** frontend. **Files:** copy backend `swagger.json`; regenerate `src/lib/api/generated/types.ts`; create
`src/features/artists/utils/wikiAttribution.ts`, `src/features/artists/components/WikiAttribution.tsx`,
`tests/wiki-attribution.test.ts`; modify `src/pages/ArtistProfilePage.tsx`.

**Interfaces:** `getWikiAttribution(artist: ArtistViewDTO): { articleUrl: string; articleTitle: string;
image?: { text: string; url: string } } | null` — null unless `source === 'WIKIPEDIA'` and `wikiUrl`; `articleTitle`
from the URL; image text `Фото: {author}, {license}, Wikimedia Commons` (missing parts dropped).

- [ ] **Step 1:** test: non-wiki artist → null; wiki artist → title «Океан Ельзи» from the encoded URL; credit
  without author → `Фото: CC BY-SA 4.0, Wikimedia Commons`.
- [ ] **Step 2:** FAIL. **Step 3:** implement; render under the biography «Джерело: стаття „{title}“ з Вікіпедії,
  CC BY-SA 4.0» (links to the article and `https://creativecommons.org/licenses/by-sa/4.0/deed.uk`) and the photo
  credit under the gallery, in `text-sm text-muted-foreground` with `aero-link` links.
- [ ] **Step 4:** `npm test && npm run type-check && npm run lint` (no new lint problems vs main).
- [ ] **Step 5:** commit `feat(wiki): Wikipedia attribution on the artist page`.

### Task 7: Frontend — clustered map

**Files:** create `src/features/map/utils/clusterPins.ts`, `src/features/map/components/ClusterMarker.tsx`,
`tests/cluster-pins.test.ts`; modify `MapContainer.tsx`; add `.aero-cluster` to `src/styles/aero.css`.

**Interfaces:**
- `createPinIndex(pins: MapPinDTO[]): PinIndex` — groups exact coordinates first (`groupPinsByLocation`), then loads
  one supercluster point per group with property `count = group.pins.length`; options `radius: 60, maxZoom: 16`,
  `map/reduce` summing `count`.
- `getMapItems(index: PinIndex, bbox: [number, number, number, number], zoom: number): MapItem[]` where
  `MapItem = { kind: 'cluster'; id: number; latitude: number; longitude: number; count: number }
  | { kind: 'group'; group: PinGroup }`.
- `getExpansionZoom(index: PinIndex, clusterId: number): number`.
- `ClusterMarker({ count, onClick })` — round aqua gel bubble, size steps by count (<10, <100, <1000, more), label
  `count` (≥1000 → `1,2 тис.`), `aria-label="{count} артистів — наблизити"`, a `<button>`.
- `MapContainer`: index memoised on `pins`; items from the current map bounds and integer zoom, recomputed on
  `onMoveEnd` and on load; cluster click → `flyTo` expansion zoom; group items render the existing `StackMarker`.

- [ ] **Step 1:** tests: 50 pins within 3 km of Kyiv at zoom 6 → one cluster, count 50; same at zoom 17 → 50 groups;
  300 pins at one exact coordinate → at zoom 17 one group with 300 pins, at zoom 6 counted as 300 in a cluster or
  the group; a pin outside the bbox is not returned; expansion zoom of the Kyiv cluster > 6.
- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** `npm test && npm run type-check && npm run lint`.
- [ ] **Step 5:** commit `feat(map): cluster thousands of pins`.

### Task 8: Verification and review

- [ ] Mock API (scratchpad `mock-api/server.mjs`) serves `/api/v1/map/pins` from the Task 4 dry-run JSON (≈ all
  artists with a place) and an artist profile with Wikipedia fields; screenshots at 390 and 1440 px: Ukraine view,
  Kyiv zoomed, stack list open, artist page with attribution; count rendered markers on the initial view (expect
  < 200).
- [ ] Backend `./gradlew test`, frontend `npm test && npm run type-check && npm run lint && npm run build`.
- [ ] Fresh whole-branch review of both branches (superpowers:requesting-code-review, most capable model) with this
  plan's Review Focus; fix Critical/Important with a failing test first; commit.
