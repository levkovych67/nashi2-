# Nashi Release Prep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Nashi releasable:
- artist kinds (musicians, bands, visual artists, writers) imported from Wikidata, each with a page that fits the kind;
- birth place on the map;
- full map filters;
- a local stack that cannot touch production;
- a production config and deploy that are safe.

**Architecture:**
- **Backend** (Spring Boot 3.4, MongoDB):
  - Gains new `Artist` fields and API fields.
  - The `com.nashi.wiki` importer learns occupations → kinds, dates, works and many more links.
  - Secrets move out of the repo into a server-side file loaded by `spring.config.import`.
  - A `local` profile plus a startup guard makes local runs safe.
- **Frontend** (React/Vite):
  - Loads all pins once and filters them in the browser, with the filters kept in the URL.
  - The artist page composes kind-specific sections.
- A Docker Compose stack (Mongo + backend + nginx frontend) is the local test build.

**Tech Stack:**
- Backend: Java 17, Spring Boot 3.4.1, MongoDB, Gson, JUnit 5 + Mockito.
- Frontend: React 19, TypeScript 5.9, react-map-gl/maplibre, supercluster, TanStack Query 5, Tailwind 3, node:test on Node 24.
- Infra: Docker Compose, nginx, GitHub Actions.

**Spec:** `nashi-backend/docs/superpowers/specs/2026-11-07-release-prep-design.md` (Ukrainian). The older spec `2026-11-04-wikipedia-import-design.md` still holds where this one is silent.

**Repos:** `B` = `~/development/nashi/nashi-backend` (private), `F` = `~/development/nashi/nashi-frontend` (PUBLIC). Both are on branch `feat/release-prep`.

## Global Constraints

- **Never print secrets.** Never print, copy, log or commit the values from `application*.properties`, `/etc/nashi/*`, `aws/*.csv` or `.env*`. Refer to keys by name only. When removing secret lines, delete them by key with `sed`, without displaying them.
- **Starting the backend locally** is allowed only with `SPRING_PROFILES_ACTIVE=local`, and only after Task 1 has landed. It must never run with the production profile or production secrets.
- **Tests are plain JUnit 5 / Mockito only.** Never use `@SpringBootTest`, `@DataMongoTest` or other Spring context or slice tests. `MockEnvironment` (spring-test) is fine.
- **Test fixtures** are real Wikimedia/MusicBrainz responses, trimmed, saved under `B/src/test/resources/wiki/`.
- **Wikimedia requests:**
  - User-Agent exactly `NashiWikiImport/1.0 (https://github.com/levkovych67/nashi-frontend)`. Never put an email anywhere.
  - Sequential, ≥ 200 ms apart, via `PoliteWikiHttp`.
  - MusicBrainz ≤ 1 request per second.
- **The frontend repo is public.** No backend secrets, hostnames or keys in it.
- **No `git push` and no merge into `main`.** A push to main deploys production.
- **Frontend copy:**
  - All user-facing copy is in Ukrainian, exactly as given in the task when the plan gives it.
  - Use the Aero design system (`src/styles/aero.css`, `src/components/ui/*`). Tap targets are ≥ 44 px.
  - Frontend helpers tested by node:test are plain `.ts` with no JSX and no TS `enum`/`namespace`, because Node's type stripping must run them.
- **Commands:** backend tests `cd B && ./gradlew test`; frontend tests `cd F && npm test`, plus `npm run type-check` and `npm run build`.
- **Commits** are small, one per task step that says commit. Every commit message ends with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01EmEYaztnSvp3c4vptTMfgG
  ```

## Review Focus

1. **Missing data never breaks a page.** An artist with no photo, no dates, no works, no links or an unknown link platform still renders its page and its pin. Covered by F1/F2 helper tests and the V-task visual check.
2. **The local guard holds.** With profile `local` but `SPRING_DATA_MONGODB_URI` pointing at a remote host, `telegram.enabled=true` or a real-looking key, startup stops and names the offending key without its value. Covered by T1 `StartupConfigGuardTest`.
3. **Links belong to the right artist.** Links, works and images in a batch never leak to another artist in the same batch. Covered by T6 `WorkSelectorTest.keepsWorksWithTheirCreator` and T7 `assemblesLinksOnlyFromOwnItem`.
4. **Filters stay fast on ~30k pins.** Covered by F3 `filterPinsHandles30kPinsQuickly` (< 50 ms). JSON is gzipped (T3 asserts the properties).
5. **Name search is forgiving.** It ignores case, apostrophe variants (`'`, `ʼ`, `’`) and stress marks (U+0301). Covered by F3 `normalizeName` tests.

---

## Order and parallelism

T1 → T2 → T3 must land first: they give safety and the API contract. After T3, two tracks run in parallel:
- **Backend track:** T4 → T5 → T6 → T7 → T8 → T9 → T10.
- **Frontend track:** F1 → F2 → F3 → F4 → F5.

V (verification) runs after both tracks.

---

### Task T1 (B): Secrets out of the repo, `local` profile, startup guard, Telegram switch

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-local.properties`
- Create: `src/main/java/com/nashi/config/StartupConfigGuard.java`
- Create: `src/main/resources/META-INF/spring.factories`
- Modify: `src/main/java/com/nashi/config/TelegramProperties.java`, `src/main/java/com/nashi/bot/TelegramBot.java`, `src/main/java/com/nashi/service/impl/TelegramService.java` (as needed for the switch)
- Modify: `.gitignore`. Untrack `aws/nashi-backend-user_accessKeys.csv` (`git rm --cached`; the file stays on disk).
- Test: `src/test/java/com/nashi/config/StartupConfigGuardTest.java`, `src/test/java/com/nashi/bot/TelegramSwitchTest.java`

**Interfaces:**
- Produces:
  - `StartupConfigGuard implements EnvironmentPostProcessor, Ordered` (order `Ordered.LOWEST_PRECEDENCE`, so it runs after config data, including the imported secrets file, is loaded).
  - `static List<String> problems(ConfigurableEnvironment env)`.
  - Properties `telegram.enabled` (default true), `telegrambots.enabled`.

- [ ] **Step 1: Write the failing tests** `StartupConfigGuardTest`. Build environments with `MockEnvironment` and call `StartupConfigGuard.problems(env)`.
  - `localProfileAcceptsLocalSettings`: profile `local`, `spring.data.mongodb.uri=mongodb://localhost:27017/nashi`, `telegram.enabled=false`, `telegrambots.enabled=false`, `spring.ai.openai.api-key=local-disabled`, `aws.s3.access-key-id=local-disabled` → empty list.
  - `localProfileRejectsRemoteMongo`: as above but `mongodb+srv://u:Secr3tPw@cluster0.example.net/nashi` → one problem that contains `spring.data.mongodb.uri` and does NOT contain `Secr3tPw` or `cluster0`.
  - `localProfileAcceptsDockerMongoHost`: `mongodb://mongo:27017/nashi` → empty.
  - `localProfileRejectsTelegram`: `telegram.enabled=true` → a problem naming `telegram.enabled`. Missing `telegrambots.enabled` (the starter default is on) → a problem naming `telegrambots.enabled`.
  - `localProfileRejectsRealLookingKeys`: OpenAI key `sk-abc123` → a problem naming `spring.ai.openai.api-key` without `sk-abc123`. S3 key `AKIAXXXXXXXX` → a problem naming `aws.s3.access-key-id`.
  - `productionReportsMissingKeysByName`: no profile and no secrets → problems list exactly the names `spring.data.mongodb.uri`, `spring.ai.openai.api-key`, `aws.s3.access-key-id`, `aws.s3.secret-access-key`, `telegram.bot-token`, `telegram.admin-chat-id`, `telegram.channel-id`.
  - `productionAcceptsCompleteConfig`: all seven set → empty.
  - `postProcessThrowsWithAllProblems`: `new StartupConfigGuard().postProcessEnvironment(env, null)` throws `IllegalStateException` whose message lists the problems and mentions `NASHI_SECRETS_FILE`.
  - `repoPropertiesHoldNoSecrets`: load the classpath `application.properties` with `Properties`. None of the seven keys above has a non-blank value. `spring.config.import` contains `optional:file:${NASHI_SECRETS_FILE:/etc/nashi/nashi-backend.properties}`.
  - `localPropertiesPassTheGuard`: load the classpath `application.properties` plus `application-local.properties` into a `MockEnvironment` with profile `local` → `problems` is empty.

- [ ] **Step 2: Run and see them fail.** Run `./gradlew test --tests 'com.nashi.config.*'`. Expected: compilation failure (`StartupConfigGuard` missing).

- [ ] **Step 3: Implement.**
  - **`application.properties`:** delete the seven secret lines by key with `sed -i '' '/^<key>=/d'`, without printing them. Also delete the unused `spring.jpa.*` and `cors.allowed-origins` lines. Add:
    - `spring.config.import=optional:file:${NASHI_SECRETS_FILE:/etc/nashi/nashi-backend.properties}`
    - `telegram.enabled=true`
    - `nashi.cors.allowed-origins=*`
    - `server.compression.enabled=true`
    - `server.compression.mime-types=application/json,text/html,text/plain,application/javascript,text/css`
    - `server.compression.min-response-size=2048`
    Keep `aws.s3.region`, `aws.s3.bucket`, the multipart limits, springdoc and `server.port`.
  - **`application-local.properties`:**
    - `spring.data.mongodb.uri=mongodb://localhost:27017/nashi`
    - `telegram.enabled=false`, `telegrambots.enabled=false`
    - `telegram.bot-token=local-disabled`, `telegram.admin-chat-id=0`, `telegram.channel-id=0`
    - `spring.ai.openai.api-key=local-disabled`
    - `aws.s3.access-key-id=local-disabled`, `aws.s3.secret-access-key=local-disabled`
    - `nashi.wiki-import.enabled=false`
  - **`StartupConfigGuard`:**
    - Local hosts allowed: `localhost`, `127.0.0.1`, `mongo`, `host.docker.internal`. Parse the host from the URI; `mongodb+srv` is never local.
    - Messages name the key and the rule, never the value.
    - Register it in `META-INF/spring.factories` under `org.springframework.boot.env.EnvironmentPostProcessor`.
  - **Telegram switch:**
    - Add `Boolean enabled` to `TelegramProperties`, where null means true.
    - Every outgoing Telegram call becomes a logged no-op when it is false: admin text, photos, media groups, audio, channel posts.
    - Verify that `telegrambots.enabled` is the property the starter honours. Look at the `TelegramBotStarterConfiguration` condition in the jar with `unzip -p` and `javap`, and ledger what you find.

- [ ] **Step 4: Write the failing test** `TelegramSwitchTest.disabledBotSendsNothing`.
  - With `telegram.enabled=false`, calling the admin text and channel send paths makes no call on an injected `TelegramClient` (Mockito `verifyNoInteractions`).
  - If you need a package-private constructor that accepts a `TelegramClient`, add one.
  - Run it, watch it fail, then implement.

- [ ] **Step 5: Untrack the AWS CSV.** Run `git rm --cached aws/nashi-backend-user_accessKeys.csv`, then add `aws/` and `config/` to `.gitignore`. Do not open the file.

- [ ] **Step 6: Run the whole suite.** Run `./gradlew test`. Expected: all pass, previous count + new ones.

- [ ] **Step 7: Docs.** Update `CLAUDE.md`:
  - "Configuration & Secrets" now describes the secrets file, `NASHI_SECRETS_FILE`, the local profile and the guard;
  - replace the dev-profile instructions with `SPRING_PROFILES_ACTIVE=local`;
  - drop the claims about Flapdoodle tests.

- [ ] **Step 8: Commit.** Message: `feat(config): secrets out of the repo, safe local profile and startup guard`.

### Task T2 (B + F): Local stack — Dockerfiles, Compose, scripts, LOCAL.md

**Files:**
- Create in B: `Dockerfile`, `.dockerignore`, `scripts/local-up.sh`, `scripts/local-down.sh`, `LOCAL.md`
- Modify in B: `docker-compose.yml`, `README.md` (local section points to LOCAL.md)
- Create in F: `Dockerfile`, `.dockerignore`, `nginx.conf`
- Modify in F: `README.md` (local section points to the backend LOCAL.md)

**Interfaces:**
- Consumes: the T1 `local` profile.
- Produces:
  - `http://localhost:8080` (backend, profile local) and `http://localhost:4173` (frontend through nginx).
  - Mongo on `127.0.0.1:27017` only.
  - `scripts/local-up.sh [--no-build]` and `scripts/local-down.sh [--reset]`.

- [ ] **Step 1: Backend Dockerfile.**
  - Multi-stage: `eclipse-temurin:17-jdk` runs `./gradlew bootJar -x test --no-daemon`, then `eclipse-temurin:17-jre` runs `java -jar app.jar`.
  - `ENV SPRING_PROFILES_ACTIVE=local` is the image default, so the image can never start in production mode by accident.
  - `.dockerignore` excludes `build/`, `.gradle/`, `aws/`, `config/`, `*.csv`, `.git/`, `docs/` and `src/test/`. Secrets must never enter the image.

- [ ] **Step 2: Compose.** In `docker-compose.yml`:
  - `mongo`: `mongo:7`, `127.0.0.1:27017:27017`, volume `mongo_data`, healthcheck `mongosh --quiet --eval 'db.runCommand({ping:1}).ok'`.
  - `backend`: build `.`, env `SPRING_PROFILES_ACTIVE=local` and `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/nashi`, `127.0.0.1:8080:8080`, depends on a healthy mongo, healthcheck curl or wget on `/api/v1/health`.
  - `frontend`: build `../nashi-frontend` with args `VITE_API_BASE_URL=http://localhost:8080`, `VITE_CDN_URL=http://localhost:8080` and `VITE_MAPTILER_API_KEY=${VITE_MAPTILER_API_KEY:-}`; port `127.0.0.1:4173:80`.

- [ ] **Step 3: Frontend Dockerfile + nginx.**
  - `node:24-alpine` runs `npm ci`, then `npm run build` with the three `ARG`s passed as env; then `nginx:1.27-alpine` with `nginx.conf`.
  - `nginx.conf`:
    - `try_files $uri $uri/ /index.html` (SPA fallback);
    - gzip for js/css/json/svg;
    - `Cache-Control: public, max-age=31536000, immutable` for `/assets/`;
    - `no-cache` for `index.html`.
  - `.dockerignore` excludes `node_modules`, `dist` and `.env*`.

- [ ] **Step 4: Scripts.**
  - **`local-up.sh`:**
    1. Checks that `docker` is present and the daemon is running.
    2. If `VITE_MAPTILER_API_KEY` is unset, reads it from `../nashi-frontend/.env.local` with `grep`/`cut`, without echoing it. Prints whether it was found.
    3. Runs `docker compose up -d --build` (`--no-build` skips the build).
    4. Polls `http://localhost:8080/api/v1/health` for up to 180 s.
    5. Prints «Сайт: http://localhost:4173» and «API: http://localhost:8080/swagger-ui».
  - **`local-down.sh`:** `docker compose down`; with `--reset` it adds `-v`.
  - Both scripts use `set -euo pipefail` and are executable.

- [ ] **Step 5: `LOCAL.md` in Ukrainian.** Sections:
  - «Що потрібно» (Docker Desktop, about 4 GB of free RAM);
  - «Запуск однією командою»;
  - «Що всередині» (three services; nothing goes to the production base, the bot, S3 or OpenAI);
  - «Звідки дані» (after T7 the import fills the base on first start; how to change `NASHI_WIKIIMPORT_LIMITPERCATEGORY`, the env-var form of `nashi.wiki-import.limit-per-category` under Spring relaxed binding);
  - «Скинути базу»;
  - «Режим розробки» (`docker compose up -d mongo`, `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`, `npm run dev` in the frontend with `.env.local` → `VITE_API_BASE_URL=http://localhost:8080`);
  - «Що не працює локально» (photo/audio uploads in the form, Telegram moderation, AI tags);
  - «Якщо щось не так».

- [ ] **Step 6: Verify the stack for real.**
  1. Run `./scripts/local-up.sh`. Expected: it exits 0 and health is 200.
  2. `curl -s -o /dev/null -w '%{http_code}' http://localhost:4173/artists/anything` → `200` (SPA fallback).
  3. `docker compose logs backend | grep -iE 'long ?polling|telegram'` shows no polling registration.
  4. `docker compose logs backend | grep -i 'mongodb+srv'` → nothing.
  Ledger the outputs. Leave the stack running for later tasks, or stop it with `local-down.sh`.

- [ ] **Step 7: Commit** in both repos. Backend: `feat(local): one-command local stack with Docker Compose`. Frontend: `feat(local): Dockerfile and nginx config for the local stack`.

### Task T3 (B): Data model and API contract

**Files:**
- Create: `enums/ArtistCategory.java` (`MUSICIAN, BAND, VISUAL_ARTIST, WRITER`), `enums/LocationBasis.java` (`BIRTH, FORMATION, RESIDENCE, WORK`), `domain/ArtistWork.java`, `util/ArtistFacts.java`
- Modify: `domain/Artist.java`, `domain/SocialLink.java`, `dto/artist/ArtistViewDTO.java`, `dto/artist/ArtistDTOMinified.java`, `dto/MapPinDTO.java`, `service/impl/artist/ArtistServiceImpl.java`, `service/impl/MapServiceImpl.java`, `util/WikiLinks.java`, `swagger.json`
- Test: `util/ArtistFactsTest.java`, `service/impl/MapServiceImplTest.java`, `SwaggerContractTest.java`; extend `ArtistServiceImplTest`

**Interfaces:**
- Produces, used by T7 and by the frontend:
  - **`Artist` new fields:**
    - `List<ArtistCategory> categories`
    - `String birthDate`, `String deathDate`, `String birthPlace`
    - `LocationBasis locationBasis`
    - `List<String> genres`
    - `List<ArtistWork> works`
  - **`ArtistWork`:** `@Data @Builder @NoArgsConstructor @AllArgsConstructor` with `wikidataId, title, Integer year, imageUrl, fullImageUrl, ImageCredit imageCredit, articleUrl`. It is reused as is in `ArtistViewDTO`.
  - **`SocialLink.Platform` gains:** `X, DEEZER, BANDCAMP, DISCOGS, MUSICBRAINZ, LASTFM, COMMONS, WIKISOURCE, GOODREADS, OPEN_LIBRARY, ARTSY, WIKIDATA`. Existing values stay as they are.
  - **`ArtistViewDTO` gains:** `categories, birthDate, deathDate, birthPlace, locationBasis, genres, works, wikidataUrl`.
  - **`ArtistDTOMinified` gains:** `categories`.
  - **`MapPinDTO` gains:** `List<ArtistCategory> categories, Region region, String city, Integer year, List<String> genres`. The class is annotated `@JsonInclude(JsonInclude.Include.NON_EMPTY)`.
  - **`ArtistFacts`:**
    - `static List<ArtistCategory> categoriesOf(Artist a)` — stored categories, else `[BAND]` when `members` is non-empty, else `[MUSICIAN]`.
    - `static List<String> genresOf(Artist a)` — stored genres, else `[style]` when style is non-blank, else `[]`.
    - `static Integer yearOf(Artist a)` — the birth year from `birthDate` when the artist is not a BAND, else `foundationYear`.
  - **`WikiLinks.wikidataUrl(String qid)`** → `https://www.wikidata.org/wiki/<qid>`, or null.

- [ ] **Step 1: Write the failing tests.**
  - **`ArtistFactsTest`:**
    - `formBandWithMembersIsBand`
    - `formSoloIsMusician`
    - `storedCategoriesWin`
    - `genresFallBackToStyle`
    - `noStyleNoGenres`
    - `yearOfPersonIsBirthYear` (`"1814-03-09"` → 1814; `"1975"` → 1975)
    - `yearOfBandIsFoundation`
    - `yearOfPersonWithoutBirthFallsBackToFoundation`
  - **`MapServiceImplTest.artistPinCarriesFilterFields`:** make `artistToPinDto` package-private and use a Mockito `S3Service`. The pin has categories, region, city, year and genres.
  - **`ArtistServiceImplTest.viewDtoCarriesNewFields`:** categories, dates, birthPlace, locationBasis, genres, works and `wikidataUrl` for `wikidataId=Q134958`.
  - **`SwaggerContractTest`:**
    - Parse `swagger.json` with Gson.
    - Assert that the `ArtistViewDTO` properties include the 8 new names, that `MapPinDTO` includes the 5 new names, and that the `ArtistWork` schema exists.
    - Assert that the `SocialLink` platform enum lists all new values.
  - **`compressionIsOn`:** the repo `application.properties` has `server.compression.enabled=true` and `application/json` in the mime types. If T1 has not added them, add them here.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew test`.

- [ ] **Step 3: Implement the domain, DTOs and mappings.**
  - Use `ArtistFacts` in `toViewDto`, `toMinifiedDto` and `artistToPinDto`.
  - Hand-edit `swagger.json` to match: new schemas `ArtistWork`, `ArtistCategory` (enum) and `LocationBasis` (enum), and new properties.

- [ ] **Step 4: Run the full suite.** Run `./gradlew test`; all pass. Run `./gradlew build -x test`; it is OK.

- [ ] **Step 5: Commit.** Message: `feat(api): artist kinds, dates, birth place, genres and works in the model and API`.

### Task T4 (B): Wikidata parser — occupations, dates, movements, works, more links

**Files:**
- Create: `wiki/WikiDate.java`
- Modify: `wiki/WikiEntity.java`, `wiki/WikidataEntityParser.java`, `wiki/WikiCollector.java` (sitefilter `ukwiki|ukwikisource`), `src/test/java/com/nashi/wiki/TestEntities.java`
- Test: `WikidataEntityParserTest`, `WikiDateTest`; new fixture `src/test/resources/wiki/entities-kinds.json`

**Interfaces:**
- Produces:
  - **`record WikiDate(String iso, int year)`** with `static WikiDate of(String wikidataTime, int precision)`:
    - `"+1814-03-09T00:00:00Z"` at 11 → `("1814-03-09", 1814)`;
    - at 10 → `("1814-03", 1814)`;
    - at 9 → `("1814", 1814)`;
    - below 9 → null;
    - a negative or unparseable time → null.
  - **`WikiEntity` gains:**
    - `List<String> occupations` (P106, preferred first, in statement order)
    - `WikiDate birthDate` (P569), `WikiDate deathDate` (P570)
    - `List<String> movements` (P135)
    - `List<String> notableWorks` (P800)
    - `String commonsCategory` (P373)
    - `String wikisourceTitle` (the ukwikisource sitelink title)
    - `socialIds` now also holds P2002, P7085, P3789, P2850, P2722, P3283, P1953, P434, P3192, P2963, P648 and P2042.
  - **`TestEntities`** gets a builder-style helper, so tests do not break on every new component.

- [ ] **Step 1: Fetch a real fixture.**
  - Use `wbgetentities` with `props=labels|claims|sitelinks`, `languages=uk|en` and `sitefilter=ukwiki|ukwikisource`, with the polite UA.
  - Items:
    - Taras Shevchenko Q134958 (writer and painter, has wikisource);
    - Kateryna Bilokur Q1960349 (painter);
    - Lesya Ukrainka Q178012 (writer);
    - Okean Elzy Q1546347 (band);
    - Jamala Q233924 (singer).
  - Verify each QID's label before saving; if a QID is wrong, find the right one with `wbsearchentities` and ledger it.
  - Trim claims to the properties we read plus P31/P27, and save the result as `entities-kinds.json`.
- [ ] **Step 2: Write the failing tests.**
  - `WikiDateTest`: the cases above.
  - `parsesOccupationsInOrder`: Shevchenko has an occupation list that contains a writer-type and a painter-type QID; assert the order equals the statement order in the fixture.
  - `parsesBirthAndDeathDates`: Shevchenko is born `1814-03-09` and died `1861-03-10`, or whatever the fixture says; read it and assert those exact values.
  - `parsesMovementsCommonsCategoryNotableWorks`
  - `parsesUkWikisourceSitelink`
  - `parsesNewLinkIdentifiers`: Jamala and Okean Elzy have a Spotify/Discogs/MusicBrainz id where the fixture has them.
- [ ] **Step 3: Run and see the failures.** Then implement. Then run `./gradlew test`, all green.
- [ ] **Step 4: Commit.** Message: `feat(wiki): read occupations, dates, movements, works and more link ids`.

### Task T5 (B): Occupation trees → kinds, chunked candidate queries for visual artists and writers

Research (2026-11-07, raw data in `B/build/research/`) found two problems:
- A query of the `wdt:P106/wdt:P279* wd:ROOT` shape times out for visual artists and writers.
- The visual tree (802 classes) includes film.

The working pattern has two steps:
1. Fetch the occupation tree (`?occ wdt:P279* wd:ROOT`, minus the excluded subtrees).
2. Query people with `VALUES ?occ { … }` in chunks.

Chunk sizes that passed:
- visual: citizen ≤ 400 (at 53–59 s, too close to the limit), born 100–200;
- writers: citizen 75, born 37–38.
Musician queries (the existing 7) run fine in 5–49 s, so they stay as they are.

**Files:**
- Create: `wiki/OccupationCategories.java`, `wiki/CandidateGroup.java` (`MUSIC, ENSEMBLE, VISUAL, WRITER`), `wiki/CandidateLister.java`
- Modify: `wiki/WikiQueries.java`, `wiki/WikiCandidate.java` (add `CandidateGroup group`), `wiki/SparqlParser.java`, `wiki/WikiCollector.java`, `wiki/ImportReport.java` (a count of candidate chunks that failed)
- Test: `OccupationCategoriesTest`, `WikiQueriesTest`, `SparqlParserTest`, `CandidateListerTest`, `WikiCollectorTest`; fixture `sparql-occupations.json` (real, trimmed)

**Interfaces:**
- **Roots and exclusions** (constants in `OccupationCategories`):
  - MUSICIAN: roots Q639669 (musician), Q177220 (singer), Q36834 (composer).
  - VISUAL_ARTIST: root Q3391743 (visual artist). Excluded subtrees:
    - filmmaker Q1414443, which covers film director, cinematographer, camera operator, film editor and production designer;
    - stand-up comedian;
    - make-up artist;
    - cartographer.
    Take the exact QIDs from `build/research/raw/visual_tree.json` (or `wbsearchentities`), confirm each label with the live API, and ledger them.
  - WRITER: root Q36180 (writer). Excluded subtrees: columnist and correspondent. Confirm the QIDs the same way. Lyricist Q822146 stays: «поет-пісняр» is a poet.
- **`WikiQueries`:**
  - `occupationTree(String root, List<String> excludedRoots)` → `SELECT DISTINCT ?occ WHERE { ?occ wdt:P279* wd:ROOT. MINUS { ?occ wdt:P279* wd:EX } … }`.
  - `peopleWithOccupations(List<String> occupations, String ukraineRule)` → the `people` shape, but with `VALUES ?occ { wd:… } ?item wdt:P106 ?occ; wdt:P31 wd:Q5.` + rule + `NOT_RUSSIAN` + `UK_ARTICLE`.
  - `musicQueries()` → the existing 6 people queries + the ensemble query, unchanged.
  - Rules: `CITIZEN = "?item wdt:P27 wd:Q212. "`, `BORN = "?item wdt:P19/wdt:P17 wd:Q212. "`.
- **`SparqlParser.parseIds(String json, String var)`** → `List<String>` of QIDs.
- **`CandidateLister`:**
  - constructor `(Function<String, String> sparqlGet)`;
  - `List<WikiCandidate> list(CandidateGroup group, List<String> occupations, String rule, int chunkSize)`;
  - on any failure of a chunk (exception or unparseable JSON), it splits that chunk in two and retries each half, down to a single occupation;
  - a single occupation that still fails is skipped, logged and counted in `failedChunks()`;
  - start chunk sizes: VISUAL citizen 150, VISUAL born 60, WRITER citizen 40, WRITER born 20.
- **`OccupationCategories`:**
  - `OccupationCategories(Map<String, ArtistCategory> byOccupation)`;
  - `List<ArtistCategory> categoriesOf(List<String> occupations)` keeps the order of first appearance, dedupes, and ignores unknown occupations;
  - when one occupation is in two trees, the first in the order MUSICIAN, VISUAL_ARTIST, WRITER wins for that occupation.
- **`WikiCollector`:**
  - **`listCandidates()`:**
    1. Fetches the three trees once per instance. Musician trees are needed for the category map only.
    2. Builds `OccupationCategories`.
    3. Runs `musicQueries()`: MUSIC for the people queries, ENSEMBLE for the ensemble query.
    4. Runs `CandidateLister` for VISUAL and WRITER × {CITIZEN, BORN}.
    5. Dedupes by QID, keeping the first group seen.
  - `occupationCategories()` is a package-private getter that T7 uses.
- **`ImportReport`** adds the failed-chunk count to the Telegram text when it is above 0: «… не вдалося отримати частину кандидатів: N запитів».

- [ ] **Step 1: Save a real trimmed `sparql-occupations.json`** (a writer-tree query, about 30 rows).
- [ ] **Step 2: Write the failing tests.**
  - `OccupationCategoriesTest`:
    - `poetPainterIsWriterThenVisual` (order [poet, painter] → `[WRITER, VISUAL_ARTIST]`)
    - `unknownOccupationIgnored`
    - `duplicatesCollapsed`
    - `emptyGivesEmpty`
    - `occupationInTwoTreesGoesToFirstKind`
  - `WikiQueriesTest`:
    - each people query has the `NOT_RUSSIAN` and `UK_ARTICLE` clauses and its rule;
    - `occupationTree` has a `MINUS` per excluded root;
    - `peopleWithOccupations` lists every occupation in `VALUES`;
    - `musicQueries` equals the old 7 queries.
  - `CandidateListerTest` (with a fake `sparqlGet`):
    - `splitsFailingChunkInHalves`: a chunk of 8 fails and its halves succeed; all candidates returned; 3 calls.
    - `skipsSingleFailingOccupationAndCountsIt`
    - `tagsCandidatesWithGroup`
  - `SparqlParserTest.parsesIds`
  - `WikiCollectorTest.candidatesKeepTheirFirstGroup`
- [ ] **Step 3: Implement.** Run `./gradlew test`, all green.
- [ ] **Step 4: Live check.**
  - Run a throwaway main or JShell against the live endpoint through `PoliteWikiHttp`. Do NOT commit it.
  - Fetch the three trees and run `listCandidates()`.
  - Ledger: tree sizes, candidate counts per group, the number of queries, the total time, any failed chunks.
  - Expected (research): about 4.5k MUSIC+ENSEMBLE, about 5.0k VISUAL after the film exclusion, about 6.2k WRITER, fewer after cross-group dedup.
- [ ] **Step 5: Commit.** Message: `feat(wiki): kinds from occupation trees, chunked candidates for visual artists and writers`.

### Task T6 (B): Works for visual artists (Wikidata + Commons categories) and writers

Research shows that only 287 of about 5.9k visual artists have works with `P170` + `P18` on Wikidata. 2,876 works in total, with uk labels on 14.5%, en on 78.1% and P571 on 84%. By contrast, 21.5% of visual artists have a Commons category (P373).

Writers:
- P800 (notable work) is present for 151 writers;
- P50 (author) for 810 writers with 11,401 works, but only 8% of those works have a uk.wikipedia article;
- 432 writers have a uk.wikisource author page.

**Files:**
- Create: `wiki/WorkRow.java`, `wiki/WorkSelector.java`, `wiki/WorkImages.java`, `wiki/CommonsWorks.java`
- Modify: `wiki/WikiQueries.java`, `wiki/SparqlParser.java`, `wiki/MediaWikiParser.java`, `wiki/WikiCollector.java`
- Test: `WorkSelectorTest`, `WorkImagesTest`, `CommonsWorksTest`, `SparqlParserTest`, `MediaWikiParserTest`; fixtures `sparql-works-visual.json`, `sparql-works-written.json`, `imageinfo-works.json`, `commons-subcats.json`, `commons-files.json` (all real, trimmed)

**Interfaces:**
- **`record WorkRow(String creator, String work, String title, Integer year, String imageFile, String ukTitle, String wikisourceTitle, boolean notable)`**
- **`WikiQueries.visualWorks(List<String> creators)`:** `VALUES ?creator`, `?work wdt:P170 ?creator; wdt:P18 ?image`, an optional uk label falling back to an en label, optional `YEAR(P571)`, and an optional uk.wikipedia title.
- **`WikiQueries.writtenWorks(List<String> authors)`:**
  ```
  { ?author wdt:P800 ?work BIND(true AS ?notable) }
  UNION { ?work wdt:P50 ?author. { <uk.wikipedia article> } UNION { <uk.wikisource sitelink> } }
  ```
  plus labels, `YEAR(P577)`, the optional uk.wikipedia title and the optional uk.wikisource title.
- **`SparqlParser.parseWorks(String json)`** → `List<WorkRow>`. A label equal to the QID counts as no title.
- **`WorkSelector.select(List<WorkRow> rows, int max)`** → `Map<String creator, List<WorkRow>>`. Order within a creator:
  1. notable;
  2. has ukTitle;
  3. has wikisourceTitle;
  4. has title;
  5. year ascending, nulls last;
  6. QID numeric.
  Dedup per creator; keep at most `max` (12).
- **`WorkImages.previewUrl(String fullUrl)`** replaces `/1280px-` with `/500px-` when present, otherwise returns the input. Wikimedia serves only standard thumbnail widths (verified 2026-11-07: 330, 500, 960 and 1280 work; 400 returns HTTP 400). The imageinfo API itself rounds `iiurlwidth=400` up to a `/500px-` URL, so portraits are fine.
- **Commons fallback for visual artists (`CommonsWorks`).** It applies to a VISUAL_ARTIST with a P373 category and fewer than 4 Wikidata works.
  1. List the category's subcategories with `list=categorymembers&cmtype=subcat`, one request per artist.
  2. Pick the subcategories whose title, without the `Category:` prefix, starts with one of `Paintings by `, `Works by `, `Drawings by `, `Sculptures by `, `Prints by `, `Artworks by `, `Graphic works by `. The person's name in the title is not checked: the subcategory is already inside the artist's own category.
  3. List up to 12 files from those subcategories with `cmtype=file`, in that order, so the total stays ≤ 12.
  4. A work from Commons has no QID. Its title is the file name without the extension, with `_` turned into spaces, and with a trailing parenthesised museum or ID suffix removed when the remainder is longer than 3 characters.
  - `CommonsWorks.workTitle(String fileName)` is a pure function with tests.
  - `CommonsWorks.isWorksCategory(String title)` is also pure, with tests.
- **`WikiCollector`:**
  - collects works per artist: visual from Wikidata, then the Commons fallback, for `VISUAL_ARTIST`; written for `WRITER`; in batches of 200 creators (split in halves on failure, as `CandidateLister` does);
  - for every work image, Commons imageinfo with `iiurlwidth=1280`: the thumb becomes `fullImageUrl` and the preview is derived from it; the credit is built as for portraits;
  - `articleUrl` = the uk.wikipedia article when there is one, else the uk.wikisource text (`https://uk.wikisource.org/wiki/<encoded title>`);
  - returns `Map<String qid, List<ArtistWork>>` for T7.

- [ ] **Step 1: Save the real fixtures.** Work through the live API with the polite UA:
  - a visual-works query for Bilokur and 2 more painters;
  - a written-works query for Shevchenko and Lesya Ukrainka;
  - imageinfo for 3 work files;
  - one `cmtype=subcat` and one `cmtype=file` response for a painter whose Commons category has «Paintings by …» (find one; ledger which).
- [ ] **Step 2: Write the failing tests.**
  - `WorkSelectorTest`:
    - `notableFirstThenArticleThenWikisourceThenYear`
    - `capsAtTwelve`
    - `keepsWorksWithTheirCreator` (interleaved rows of A and B; no work moves)
    - `duplicateWorkRowsCollapsed`
  - `WorkImagesTest`: `previewOfThumb`, `previewOfOriginalIsSame`.
  - `CommonsWorksTest`:
    - `worksCategoryPrefixes`
    - `portraitCategoryIsNotWorks` («Portraits of X», «X in art», «Photographs of X» → false)
    - `titleFromFileName` (`Kateryna_Bilokur_-_Bouquet_of_flowers_(1959).jpg` → «Kateryna Bilokur - Bouquet of flowers (1959)», keeping a year in parentheses; `Foo_(Q12345).jpg` → «Foo»)
    - `capsAtTwelveAcrossSubcategories`
  - `SparqlParserTest`: `parsesVisualWorks`, `parsesWrittenWorksWithNotableAndWikisource`, `qidLabelIsNoTitle`.
  - `MediaWikiParserTest`: `parsesCategoryMembers`.
- [ ] **Step 3: Implement.** Run `./gradlew test`, all green.
- [ ] **Step 4: Commit.** Message: `feat(wiki): works for visual artists (Wikidata and Commons) and writers`.

### Task T7 (B): Assemble everything, map to `Artist`, local seeding, dry-run stats

**Files:**
- Create: `wiki/WikiLinkTable.java`, `wiki/LocalSeedRunner.java`
- Modify: `wiki/WikiArtistData.java`, `wiki/WikiArtistAssembler.java`, `wiki/PlaceResolver.java` (expose `String label(String qid)` for any fetched place), `wiki/WikiCollector.java`, `wiki/ArtistImportService.java`, `wiki/ArtistStore.java` + `wiki/MongoArtistStore.java` (`long countWikipediaArtists()`), `wiki/WikiDryRun.java`, `application-local.properties` (`nashi.wiki-import.on-startup=true`, `nashi.wiki-import.limit-per-category=150`)
- Test: `WikiArtistAssemblerTest`, `ArtistImportServiceTest`, `WikiLinkTableTest`, `LocalSeedRunnerTest`, `WikiCollectorTest`

**Interfaces:**
- Consumes: T3 (domain), T4 (`WikiEntity` fields), T5 (`OccupationCategories`, `CandidateGroup`), T6 (works map).
- Produces:
  - **`WikiArtistData` gains:**
    - `List<ArtistCategory> categories`
    - `String birthDate`, `String deathDate`, `String birthPlace`
    - `LocationBasis locationBasis`
    - `List<String> genres`
    - `List<ArtistWork> works`, attached through a `withWorks(List<ArtistWork>)` copy method.
  - **`WikiArtistAssembler.assemble(WikiEntity e, CandidateGroup group, Map<String, WikiPlace> places, Map<String, WikiEntity> labels, OccupationCategories kinds, Function<String, String> placeLabel)`:**
    - categories: ensemble → `[BAND]`, else `kinds.categoriesOf(e.occupations())`. If that is empty (an occupation outside every tree), fall back to the category of `group`: MUSIC→MUSICIAN, ENSEMBLE→BAND, VISUAL→VISUAL_ARTIST, WRITER→WRITER.
    - birthPlace: `placeLabel.apply(first P19)`.
    - locationBasis: the list the chosen place came from (BIRTH/RESIDENCE/WORK, FORMATION for ensembles).
    - genres: uk labels (else en) of P136 then P135, deduped, max 5.
  - **Works:** attach `WikiCollector.works(categoriesByQid, commonsCategoryByQid)` (T6) through `withWorks`. For VISUAL_ARTIST, drop works without an `imageUrl`; writers' works need no image.
  - **`WikiLinkTable.links(WikiEntity e)`** → `List<SocialLink>`. It implements the spec's link table plus three platforms that T3 added: ESU P4613, CHTYVO P13437 and WIKIART P6002. T4 did not parse those three properties, so add them to `WikidataEntityParser`'s `socialIds` here, with a test. Take the URL templates for every property from Wikidata P1630 (the formatter URL) and ledger any template that differs from the spec. Further rules:
    - it implements the spec's link table, in that table's order;
    - Commons/Wikisource titles are MediaWiki-encoded (spaces → `_`, then URL-encoded);
    - it always adds WIKIDATA, and WIKI when there is a uk title.
  - **`ArtistImportService`:**
    - `toArtist` maps every new field;
    - a new constructor parameter `int limitPerCategory` (`@Value("${nashi.wiki-import.limit-per-category:0}")` on the Spring constructor) keeps at most N new candidates per `CandidateGroup` when N > 0.
  - **`LocalSeedRunner`:** `@Profile("local") @Component implements ApplicationRunner`. When `nashi.wiki-import.on-startup` is true and `store.countWikipediaArtists() == 0`, it calls `importService.startInBackground()`.
  - **`WikiDryRun`** prints:
    - counts per category;
    - with location, by `locationBasis`;
    - with birthDate, with works (per category), with a photo;
    - link coverage per platform.

- **Russia-linked exclusion (owner decision, 2026-11-08):** an artist is not imported if they have Russian citizenship (P27 = Q159, as before) OR any state award or title of Russia.
  - **Parser:** read P166 (awards) into `WikiEntity.awards` (`List<String>`).
  - **Collector:**
    - in `fetchDetails`, fetch the award entities (`props=claims`), cached for the run like genres;
    - an award is Russian when its P17 (country) is Q159 (Russia) or Q2184 (RSFSR). Verify the RSFSR QID live and ledger it.
  - **Not Russian:** Soviet awards (P17 = USSR Q15180) and imperial awards (Russian Empire Q34266) do not count.
  - **Assembler:** `assemble(...)` gets a `Set<String> russianAwards` parameter and returns empty when any of the entity's awards is in it.
  - **Count:**
    - count these exclusions in `WikiCollector`;
    - expose the count through `ArtistCandidateSource` (a default method, like the failed-chunk count);
    - add it to `ImportReport`: «… відсіяно через зв'язок з РФ: N».
  - **Tests:**
    - `parsesAwards`
    - `russianStateAwardExcludes`
    - `rsfsrAwardExcludes`
    - `sovietAwardDoesNotExclude`
    - `imperialAwardDoesNotExclude`
    - `russianCitizenStillExcluded`
    - `reportMentionsRussiaLinkedCount`
    Use a real trimmed award fixture: e.g. «Народний артист Росії» and «Народний артист СРСР»; find their QIDs live.
  - The dry run prints the excluded count.

- [ ] **Step 1: Write the failing tests** (use the T4/T6 fixtures through `WikidataEntityParser`).
  - `shevchenkoIsWriterAndVisualArtist`: the order follows his occupations.
  - `bandIsBand`
  - `birthDateAndDeathDateCopied`
  - `birthPlaceOutsideUkraineKeptAsLabel`: the place resolves to nothing, but the label is kept; the location falls back to a residence with `RESIDENCE`.
  - `locationBasisBirthWhenBornInUkraine`
  - `genresFromGenreAndMovementMaxFive`
  - `assemblesLinksOnlyFromOwnItem`: two entities with different Spotify ids yield two disjoint link lists.
  - `WikiLinkTableTest`:
    - `bandcampTemplate`
    - `commonsCategoryEncoded` (`"Taras Shevchenko"` → `Category:Taras_Shevchenko`)
    - `wikisourceLink`
    - `wikidataAlwaysPresent`
    - `orderFollowsTable`
  - `ArtistImportServiceTest`:
    - `toArtistMapsNewFields`
    - `limitPerCategoryKeepsNPerGroup` (fake source: 5 MUSIC + 5 WRITER, limit 2 → 4 saved)
    - `zeroLimitMeansAll`
  - `LocalSeedRunnerTest`: `seedsWhenNoWikipediaArtists`, `skipsWhenAlreadySeeded`, `skipsWhenDisabled`.
- [ ] **Step 2: Run, see the failures, implement.** Run `./gradlew test`, all green.
- [ ] **Step 3: Live sample dry run.**
  - Run `./gradlew wikiDryRun -Plimit=40`. If the current `-Plimit` is a global limit, add a per-group meaning and ledger it.
  - Expected: every category is present, works are present for VISUAL_ARTIST and WRITER, and there are no exceptions.
  - Ledger the printed stats.
- [ ] **Step 4: Seed the local stack.**
  1. Run `./scripts/local-up.sh` (with rebuild).
  2. Watch `docker compose logs -f backend` until the report line «Імпорт з Вікіпедії: …» appears.
  3. `curl -s localhost:8080/api/v1/map/pins | python3 -c 'import json,sys; d=json.load(sys.stdin); print(len(d))'`
  4. `curl -s -H 'Accept-Encoding: gzip' -o /dev/null -w '%{size_download}' localhost:8080/api/v1/map/pins`
  Ledger both numbers.
- [ ] **Step 5: Full dry run in the background.** Run `./gradlew wikiDryRun > build/full-dry-run.log 2>&1 &` (it takes hours). The controller reads its stats later in task V.
- [ ] **Step 6: Commit.** Message: `feat(wiki): import kinds, dates, birth place, genres, works and links; seed the local stack`.

### Task T8 (B): MusicBrainz link enrichment

Kept: research shows that 1,229 musicians have P434, and 1,019 of them lack YouTube or Spotify. In a sample of 40 of those, 10 gained a YouTube or Spotify link from MusicBrainz. About 1k requests at 1/s is roughly 17 minutes per full run.

**Files:**
- Create: `wiki/MusicBrainzClient.java` (`get(String mbid)` with its own pacing ≥ 1100 ms between requests, the polite UA, `Accept: application/json`, and retries on 503/429 honouring Retry-After), `wiki/MusicBrainzParser.java`
- Modify: `wiki/WikiCollector.java`, `wiki/WikiImportConfig.java` (wiring)
- Test: `MusicBrainzParserTest`, `MusicBrainzClientTest`, `WikiCollectorTest`; fixture `musicbrainz-url-rels.json` (real: one artist with Spotify, Apple Music, a YouTube channel and Bandcamp, trimmed)

**Interfaces:**
- **`MusicBrainzParser.links(String json)`** → `List<SocialLink>` from `relations[].url.resource`. Known hosts:
  - YOUTUBE:
    - `youtube.com/channel/UC…` is kept as `https://www.youtube.com/channel/UC…`;
    - `music.youtube.com/channel/UC…` is normalised to the same form;
    - `youtube.com/@handle`, `youtube.com/<name>` and `youtube.com/user/<name>` are kept as they are, and are link-only for the player.
  - SPOTIFY: `open.spotify.com/artist/…`.
  - APPLE_MUSIC: `music.apple.com/…/artist/…` and `itunes.apple.com/…artist/…`.
  - DEEZER: `deezer.com/…artist/…`.
  - SOUNDCLOUD: `soundcloud.com/…`.
  - BANDCAMP: `*.bandcamp.com`.
  - Everything else is ignored, including `vk.*`.
  - Query strings are stripped.
- **`WikiCollector`** enriches only MUSICIAN/BAND items that have a MUSICBRAINZ link and lack YOUTUBE or SPOTIFY. It adds only the platforms that are missing, and a failure for one artist is logged and skipped.

- [ ] **Step 1: Write the failing tests.**
  - `parsesKnownHostsOnly`
  - `normalisesYoutubeMusicChannel`
  - `stripsQuery`
  - `addsOnlyMissingPlatforms`
  - `skipsWhenBothPresent`
  - `pacingIsAtLeastOneSecond` (fake sleeper/clock as in `PoliteWikiHttpTest`)
- [ ] **Step 2: Implement, run `./gradlew test`, commit.** Message: `feat(wiki): fill missing music links from MusicBrainz`.

### Task T9 (B): API hardening

**Files:**
- Modify: `controller/ArtistController.java`, `service/impl/artist/ArtistServiceImpl.java`, `bot/TelegramBot.java`, `service/impl/MapServiceImpl.java`, `config/MongoIndexConfig.java`, `config/CorsConfig.java`, `controller/MetadataController.java`, `controller/HealthController.java`
- Create: `config/CachingConfig.java` (`@EnableCaching`)
- Delete: `api/TestClass.java` (first check that nothing references it)
- Test:
  - `ArtistServiceImplTest`: `pendingArtistBySlugIsNotFound`, `pendingArtistByIdIsNotFound`, `listAndSearchUseApprovedOnly`
  - `TelegramChatGuardTest`: `callbackFromOtherChatIgnored`, `callbackFromAdminChatHandled`
  - `MapServiceImplTest.styleIsMatchedLiterally`: `"a.*"` becomes `Pattern.quote` in the criteria
  - `HealthControllerTest`: `mongoDownGives503WithoutExceptionText`
  - `MetadataControllerTest.limitsAreCappedAt50`
  - `CorsConfigTest.originsReadFromProperty`

**Interfaces:**
- Public artist endpoints return only APPROVED; anything else is `ResourceNotFoundException` (404). The Telegram approval paths still load PENDING artists by id through the repository, so check `ArtistApprovalServiceImpl` and keep it working.
- `TelegramBot` handles callbacks and texts only when the chat id equals `telegram.admin-chat-id`. Everything else is logged and ignored, and nobody else gets an "alive" reply.
- Indexes are created in `MongoIndexConfig`, each in its own try/catch with a warning:
  - `slug_unique` (unique);
  - `status_1_region_1`;
  - `categories_1`.
- CORS: `nashi.cors.allowed-origins` is a comma list, `allowCredentials(false)`.
- **Admin commands (owner decision, 2026-11-08):** `/hide <slug або Q-id>` and `/show <slug або Q-id>`, accepted only in the admin chat.
  - **`/hide`:** sets the artist's status to HIDDEN. The document stays, so its `wikidataId` keeps it from being imported again.
    - Verify that the importer's known-artists load does NOT filter by status, and test it at the criteria level.
    - Replies «Приховано: <name> (<slug>). Повторно не імпортується.»
  - **`/show`:** sets a HIDDEN artist back to APPROVED. Replies «Знову показано: <name>».
  - **Unknown argument:** replies «Не знайдено: <arg>».
  - The public endpoints already return APPROVED only, so a hidden artist disappears from the map, search and page.
  - **Tests:**
    - static parsing: `parsesHideAndShowWithSlugOrQid`, `ignoresCommandsFromOtherChats`;
    - service: `hideSetsHidden`, `showRestoresApproved`, `showIgnoresNonHidden`, `unknownArgumentReportsNotFound`;
    - store: `knownArtistsIncludeHidden`.
- Event pins (`MapServiceImpl.eventToPinDto`) also carry `region` and `city` (when the event has them), so the map's region filter works for events. Test: `MapServiceImplTest.eventPinCarriesRegion`.

- [ ] **Step 1: Write the failing tests.** Run them and see them fail. Implement. Run `./gradlew test`, all green.
- [ ] **Step 2: Commit.** Message: `fix(api): approved-only artists, admin-only bot, literal style filter, indexes, honest health`.

### Task T10 (B + F): CI/CD and the release runbook

**Files:**
- Modify: `B/.github/workflows/deploy.yml`, `F/.github/workflows/deploy.yml`
- Create: `B/RELEASE.md`

- [ ] **Step 1: Backend workflow.**
  - Job `test`: `actions/checkout@v4`, `actions/setup-java@v4` (temurin 17), `./gradlew test --no-daemon`.
  - Job `deploy`: `needs: test`, uses `appleboy/ssh-action@<latest v1 tag>` (verify the tag with `gh api repos/appleboy/ssh-action/releases/latest`), keeps the same secrets, and passes `port: ${{ secrets.VPS_PORT || 22 }}`. Script:
    ```
    set -e
    test -f /etc/nashi/nashi-backend.properties || { echo "Missing /etc/nashi/nashi-backend.properties — see RELEASE.md"; exit 1; }
    cd /var/www/nashi/nashi-backend
    git pull origin main
    ./gradlew clean build -x test
    systemctl restart nashi-backend
    for i in $(seq 1 30); do curl -fsS http://localhost:8080/api/v1/health >/dev/null && exit 0; sleep 2; done
    echo "Backend did not become healthy"; exit 1
    ```
- [ ] **Step 2: Frontend workflow.**
  - Job `check`: checkout@v4, setup-node@v4 (node 24, cache npm), `npm ci`, `npm run type-check`, `npm test`, `npm run build`.
  - Job `deploy`: `needs: check`, the same pinned ssh-action. The script is the same as today but uses `npm ci`.
- [ ] **Step 3: Validate the YAML.** Run `python3 -c 'import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]' <files>`. If `actionlint` is available, run it too.
- [ ] **Step 4: `RELEASE.md` in Ukrainian.** Steps:
  1. **«SSH-ключ для деплою»:** generate a new key pair locally, put the public half in `~/.ssh/authorized_keys` of `VPS_USER` on the server, put the private half into the `VPS_SSH_KEY` secret of both repos, and check `VPS_HOST`. The cause of the failure was `unable to authenticate`.
  2. **«Файл секретів на сервері»,** BEFORE the first deploy of this branch:
     ```
     sudo mkdir -p /etc/nashi
     sudo cp /var/www/nashi/nashi-backend/src/main/resources/application.properties /etc/nashi/nashi-backend.properties
     ```
     Then `chown` the file to the service user (`systemctl show -p User --value nashi-backend`, empty means root) and `chmod 600`.
  3. **«Замінити ключі»:**
     - OpenAI: a new key;
     - AWS IAM: a new access key, deactivate the old one;
     - Mongo: a new password;
     - Telegram: BotFather `/revoke`.
     Edit `/etc/nashi/nashi-backend.properties`, then `systemctl restart nashi-backend`.
  4. **«MapTiler»:** restrict the key to the site's domain in the MapTiler dashboard, or create a new restricted key and update the frontend `.env` on the server.
  5. **«Порядок релізу»:** merge the backend into main → watch the Actions run → health OK → merge the frontend.
  6. **«Перший імпорт»:** send `/wiki_import` in the admin chat. If the report says that some candidate queries failed (Wikidata was slow), send `/wiki_import` again later: the import adds only artists that are still missing. Give the duration from task V's full dry run. One report message arrives at the end.
  7. **«Перевірка після релізу»:** a checklist — the map, filters, one page of each kind, `/?artist=`.
  8. **«Відкат»:** `git revert` of the merge commit and push, or on the server `git checkout <previous sha>` + build + restart.
- [ ] **Step 5: Commit** in both repos. Message: `ci: tests before deploy, health check after, pinned actions` (+ `docs: release runbook`).

---

### Task F1 (F): Types and helpers — platforms, media, life dates

**Files:**
- Modify: `swagger.json` (copy from B), `src/lib/api/generated/types.ts` (`npm run generate:types`)
- Create: `src/features/artists/utils/platforms.ts`, `src/features/artists/utils/media.ts`, `src/features/artists/utils/lifeDates.ts`
- Test: `tests/platforms.test.ts`, `tests/media.test.ts`, `tests/life-dates.test.ts`

**Interfaces:**
- **`platforms.ts`:**
  - `type LinkGroup = 'listen' | 'about' | 'social' | 'other'`
  - `PLATFORM_INFO: Record<string, { label: string; group: LinkGroup }>`. Labels:
    - YOUTUBE «YouTube», SPOTIFY «Spotify», APPLE_MUSIC «Apple Music», DEEZER «Deezer», SOUNDCLOUD «SoundCloud», BANDCAMP «Bandcamp» → listen;
    - DISCOGS «Discogs», MUSICBRAINZ «MusicBrainz», LASTFM «Last.fm», COMMONS «Wikimedia Commons», WIKISOURCE «Вікіджерела», GOODREADS «Goodreads», OPEN_LIBRARY «Open Library», ARTSY «Artsy» → about;
    - INSTAGRAM «Instagram», FACEBOOK «Facebook», TIKTOK «TikTok», TELEGRAM «Telegram», X «X» → social;
    - WEBSITE «Офіційний сайт», WIKI «Вікіпедія», WIKIDATA «Вікідані», OTHER «Посилання» → other.
  - `groupLinks(links: {platform?: string; url?: string}[]): Record<LinkGroup, {platform: string; label: string; url: string}[]>`:
    - skips links without a url or with a non-http(s) url;
    - an unknown platform goes to 'other' with its URL hostname as the label;
    - keeps the input order within a group and dedupes by url.
- **`media.ts`:**
  - `youtubeEmbed(url: string): string | null`:
    - `/channel/UCxxxx` → `https://www.youtube-nocookie.com/embed/videoseries?list=UUxxxx`;
    - `watch?v=ID` and `youtu.be/ID` → `https://www.youtube-nocookie.com/embed/ID`;
    - `@handle` → null.
  - `spotifyEmbed(url: string): string | null` handles `open.spotify.com/(intl-xx/)?(artist|album|track|playlist)/ID` → `https://open.spotify.com/embed/<type>/<ID>`.
  - `searchLinks(name: string): { youtube: string; spotify: string }` → `https://www.youtube.com/results?search_query=<enc>` and `https://open.spotify.com/search/<enc>`.
- **`lifeDates.ts`:**
  - `formatPartialDate(iso?: string | null): string | null`: `"1814-03-09"` → «9 березня 1814», `"1814-03"` → «березень 1814», `"1814"` → «1814».
  - `lifeSpan(a: {categories?: string[]; birthDate?: string|null; deathDate?: string|null; foundationYear?: number|null; disbandYear?: number|null}): string | null`:
    - person: «1814–1861» / «нар. 1975» / «пом. 1861»;
    - BAND: «1994–2010» / «з 1994».
  - `CATEGORY_LABEL`: MUSICIAN «Музикант», BAND «Гурт», VISUAL_ARTIST «Художник», WRITER «Письменник».
  - `locationBasisNote(basis, city)`:
    - BIRTH → null;
    - RESIDENCE → «На карті: <city> — місце життя»;
    - WORK → «На карті: <city> — місце роботи»;
    - FORMATION → «На карті: <city> — місце заснування».

- [ ] **Step 1: Write the failing tests** for every case above, including:
  - `groupLinks`: an unknown platform, a javascript: url dropped, a duplicate url dropped;
  - `youtubeEmbed`: a handle gives null;
  - `spotifyEmbed`: the intl path;
  - `formatPartialDate`: all 12 months in the genitive, spot-checking січня, лютого, травня and грудня.
- [ ] **Step 2: Run `npm test` (fail), implement, run `npm test` (pass) and `npm run type-check`.**
- [ ] **Step 3: Commit.** Message: `feat(artists): link platforms, embeds and life dates helpers`.

### Task F2 (F): Artist page by kind

**Files:**
- Create in `src/features/artists/components/`: `ArtistHero.tsx`, `ArtistFacts.tsx`, `ListenSection.tsx`, `EmbedCard.tsx`, `WorksGallery.tsx`, `WritingsSection.tsx`, `ArtistLinks.tsx`
- Modify: `src/pages/ArtistProfilePage.tsx`, `src/lib/api/hooks/useArtists.ts` (no retry on 404), `src/components/SEO.tsx` (if needed)

**Interfaces:**
- Consumes: F1 helpers and the generated `ArtistViewDTO`.
- **Section order** (spec «Сторінка митця»): Hero → Facts → Biography + `WikiAttribution` → Listen (MUSICIAN/BAND) → Works (VISUAL_ARTIST) → Writings (WRITER) → the existing press release, demo tracks and members → Links.
- **Copy:**
  - Headings: «Слухати», «Роботи», «Твори», «Посилання». Link sub-groups: «Слухати», «Про творчість», «Соцмережі», «Інше».
  - **`EmbedCard`** before the click: platform name, «Увімкнути плеєр», and the note «Плеєр завантажиться з YouTube/Spotify і може встановити їхні cookies». After the click: an iframe with `loading="lazy"`, `allow="encrypted-media; picture-in-picture"` and a title. Before the click there is no iframe in the DOM.
  - Search fallback buttons: «Пошук на YouTube», «Пошук на Spotify».
  - Works:
    - the lightbox uses `components/ui/dialog`;
    - credit line «Автор: <author> · <license> · Wikimedia Commons» (a link to `filePageUrl`);
    - a missing title shows «Без назви»;
    - «Більше робіт на Wikimedia Commons» links to the COMMONS link.
  - Writings: «Читати на Вікіджерелах» comes first when there is a WIKISOURCE link.
  - Hero: «Показати на карті» is a `<Link to={'/?artist=' + slug}>`, rendered only when lat/lng exist.
  - States: 404 → «Митця не знайдено» + «До списку митців»; other errors → «Не вдалося завантажити сторінку» + «Спробувати ще» (`refetch`).
  - Without a photo, the content does not overlap the header: remove the negative margin when there is no hero image.
- **JSON-LD:** `MusicGroup` when categories include BAND, otherwise `Person` with `jobTitle` from `CATEGORY_LABEL`. The description is the first 160 characters of the biography.

- [ ] **Step 1: Build the sections.** Every section returns null when it has no data.
- [ ] **Step 2: Visual check against a mock API.**
  - Use the scratchpad mock (`/private/tmp/claude-501/-Users-bohdanlevkovych-development/895087ac-ecd5-4db0-aade-5e95eb76bb7e/scratchpad/mock-api/server.mjs`). Extend it with four realistic artists:
    - a musician with a YouTube channel and Spotify;
    - a band without YouTube/Spotify;
    - a painter with 3 works, one without a title;
    - a writer with works and Wikisource.
    Also add a 404 slug and a 500 slug.
  - Run Vite on :5173 and take playwright-core screenshots (system Chrome) at 1440 and 390 for each artist.
  - Check:
    - no iframe before the click and one after;
    - the lightbox opens and closes with Esc;
    - no console errors.
- [ ] **Step 3: Gates.** `npm test`, `npm run type-check` and `npm run build` pass; `npm run lint` shows no new problems compared with the base (17).
- [ ] **Step 4: Commit.** Message: `feat(artists): artist page by kind — listen, works, writings, facts, links`.

### Task F3 (F): Pin filter logic and URL params

**Files:**
- Create: `src/features/map/utils/pinFilters.ts`, `src/features/map/utils/filterParams.ts`
- Test: `tests/pin-filters.test.ts`, `tests/filter-params.test.ts`

**Interfaces:**
- **Types:**
  - `type PinKind = 'MUSICIAN' | 'BAND' | 'VISUAL_ARTIST' | 'WRITER' | 'EVENT'`
  - `type PeriodKey = 'before-1900' | '1900-1945' | '1946-1990' | 'from-1991'`
  - `interface PinFilters { q: string; kinds: PinKind[]; regions: string[]; periods: PeriodKey[]; genres: string[] }`
  - `EMPTY_FILTERS`
- **Functions:**
  - `pinKinds(pin)`: EVENT → `['EVENT']`; otherwise `pin.categories`, falling back to `['MUSICIAN']`.
  - `periodOf(year?: number | null): PeriodKey | null`, using the bounds ≤1899, 1900–1945, 1946–1990 and ≥1991.
  - `normalizeName(s)`:
    - lower-case;
    - strips U+0301;
    - maps `'`, `ʼ`, `’` and `` ` `` to nothing;
    - collapses spaces.
  - `filterPins(pins, f)`:
    - AND across filters, OR within one;
    - `q` is a substring of `normalizeName(name)`;
    - period and genre do not apply to EVENT pins.
  - `facetCounts(pins, f)` → `{ kinds: Record<PinKind, number>; regions: Record<string, number>; periods: Record<PeriodKey, number>; genres: Record<string, number> }`. Each facet is counted with all the *other* filters applied.
  - `topGenres(counts, selected, n = 12)`: by count descending, always including the selected ones.
- **`filterParams.ts`:**
  - `parseFilters(sp: URLSearchParams): PinFilters`, reading `q`, `type`, `region`, `period` and `genre` (comma lists). Unknown values are dropped.
  - `writeFilters(sp: URLSearchParams, f): URLSearchParams` keeps unrelated params such as `artist`, and removes empty ones.
  - `activeFilterCount(f)`.

- [ ] **Step 1: Write the failing tests.**
  - and-across/or-within;
  - events ignore period/genre;
  - kind fallback;
  - each `periodOf` bound (1899, 1900, 1945, 1946, 1990, 1991, null);
  - `normalizeName` («Мар'яна» = «Марʼяна» = «МАР’ЯНА»; «Ле́ся» = «леся»);
  - facets exclude their own filter;
  - `topGenres` keeps the selected genres;
  - parse/write round trip;
  - unknown param values dropped;
  - `artist` param kept;
  - `filterPinsHandles30kPinsQuickly`: 30 000 synthetic pins, one call under 50 ms, measured with `performance.now()`.
- [ ] **Step 2: Run `npm test` (fail), implement, run `npm test` (pass) and `npm run type-check`.**
- [ ] **Step 3: Commit.** Message: `feat(map): client-side pin filters and URL params`.

### Task F4 (F): Filter UI, results list, map focus

**Files:**
- Create in `src/features/map/components/`: `FilterControls.tsx`, `MapFilterPanel.tsx` (≥ 1024 px), `MapFilterSheet.tsx` (< 1024 px), `ResultsList.tsx`
- Modify: `src/pages/MapPage.tsx`, `src/features/map/components/MapContainer.tsx`, `src/features/map/components/PinPreviewDrawer.tsx`, `src/lib/api/hooks/useMapPins.ts`
- Delete: `src/features/map/components/MapFilters.tsx` and the unused filter state in `src/stores/useMapStore.ts` (delete the store if nothing else uses it)

**Interfaces:**
- **Data:**
  - `useMapPins()` takes no params and calls `/api/v1/map/pins` once (`staleTime` 10 min).
  - `MapPage` reads the filters with `useSearchParams` + `parseFilters`, writes them with `writeFilters(..., { replace: true })`, and passes `filterPins(...)` to `MapContainer`.
  - The map never unmounts on a filter change.
- **`MapContainer`** gets a new optional prop `focus?: { longitude: number; latitude: number; key: string }`. On a key change it calls `flyTo({ zoom: 12, duration: 800 })`.
- **Focus:**
  - `?artist=<slug>` → find the pin by slug → focus on it and open the drawer.
  - Not found → toast «Цього митця немає на карті». Remove `artist` from the URL when the drawer closes.
- **Controls:**
  - search input with placeholder «Пошук за ім'ям»;
  - chip groups «Тип» (Музиканти, Гурти, Художники, Письменники, Події), «Період» (До 1900, 1900–1945, 1946–1990, Від 1991) and «Жанр»;
  - «Область» as a dropdown with checkboxes and labels from `/api/v1/lookup/regions`;
  - each chip shows its count from `facetCounts`; a zero-count chip is disabled unless selected;
  - «Скинути» (only when something is active);
  - result line «Знайдено: N».
- **Results:**
  - sorted by `name.localeCompare(b, 'uk')`;
  - the first 50, then «Показати ще»;
  - each row shows name, kind label, city and year;
  - a click focuses the map and opens the drawer.
- **Layout:**
  - desktop: a left `aero-glass` panel, 360 px wide, full height minus the header, with its own scroll;
  - mobile: a bottom bar with the search field and «Фільтри (n)»; it opens a bottom Sheet (85vh) with the controls and the button «Показати N»;
  - a «Список» button opens the results in a Sheet.
- **Drawer:**
  - shows the kind labels, city and year;
  - «Переглянути профіль» is a `<Link>` styled as a button;
  - events keep their current look.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Visual check.**
  - Use the scratchpad mock with the 4.5k-artist dry run plus the new fields (extend the mock to give each pin categories, region, city, year and genres; derive them from the dry-run JSON where possible).
  - Take playwright screenshots at 1440 and 390 for:
    - the initial state;
    - type = Художники;
    - region = Львівська;
    - search «жадан»;
    - the results list open;
    - a click on a result (the drawer opens);
    - reset;
    - `/?artist=<slug>` focus;
    - an unknown slug (toast).
  - The URL updates with the filters; there are no console errors; the map canvas is not recreated (check that the maplibre canvas element is the same before and after a filter change).
- [ ] **Step 3: Gates.** As in F2.
- [ ] **Step 4: Commit.** Message: `feat(map): filter panel, results list and focus on an artist`.

### Task F5 (F): Public-repo hygiene and small fixes

**Files:** `.env.example`, `.gitignore`, `.claude/settings.local.json` (untrack), `src/lib/api/client.ts`, `src/components/layout/MobileNav.tsx`, `CLAUDE.md`, `tests/client-params.test.ts`

- [ ] **Step 1: Write the failing test** `tests/client-params.test.ts`. Export the param serialisation as a pure function `buildQuery(params): string` from a new `src/lib/api/query.ts`. `{types: ['ARTIST','EVENT'], region: 'LVIV', empty: ''}` → `types=ARTIST&types=EVENT&region=LVIV`. Make `client.ts` use it.
- [ ] **Step 2: Hygiene.**
  - `.env.example`: `VITE_API_BASE_URL=http://localhost:8080`, `VITE_CDN_URL=http://localhost:8080`, `VITE_MAPTILER_API_KEY=your-maptiler-key`.
  - Run `git rm --cached .claude/settings.local.json` and add `.claude/settings.local.json` to `.gitignore`.
  - MobileNav gets the «Радіо» and «Про проєкт» links, matching the header nav.
- [ ] **Step 3: Update `CLAUDE.md`:**
  - clustering (supercluster), client-side filters and URL params;
  - env vars including `VITE_CDN_URL`;
  - the test list;
  - the local stack pointer.
- [ ] **Step 4: Gates and commit.** Message: `chore: public-repo hygiene, array query params, mobile nav`.

---

### Task V: End-to-end verification and handoff (controller)

- [ ] Rebuild the local stack with `./scripts/local-up.sh`. The seed import is finished.
- [ ] Take playwright screenshots against `http://localhost:4173`, desktop 1440 and mobile 390:
  - the map with all pins;
  - each filter;
  - the results list;
  - focus from an artist page;
  - one real page of each kind (MUSICIAN with YouTube/Spotify, BAND, VISUAL_ARTIST with works, WRITER with works);
  - an artist without a photo;
  - 404.
- [ ] Measure the pins payload (gzip bytes) and the main-thread time of the first map render.
- [ ] Read the stats from the full dry run (`build/full-dry-run.log`): counts per kind, coverage and duration. Fill the numbers into `RELEASE.md` and into the customer doc.
- [ ] Final whole-branch review (a fresh reviewer on the most capable model), then the fix pass, then finishing-a-development-branch for both repos.
