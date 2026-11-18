# «Наші V2» — план імплементації продукту (технічна команда)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Побудувати «Наші V2» — mobile-first інтерактивну карту України з музикантами (агрегація область → населений пункт → пін, випадковий аудіофрагмент, сторінка музиканта, необмежені лайки, фільтр за областю) і форму пропозиції музиканта з модерацією в Telegram-групі, так, щоб затверджений дизайн підключався без переробки backend-логіки.

**Architecture:** Монорепозиторій `nashi_v2/`. Backend — Spring Boot (Java 21) з пакетами за фічами під `com.nashi`, MongoDB `nashi_v2`, S3-сховище, Telegram long polling; модуль імпорту Вікіпедії і частина інфраструктури переносяться з V1. Web — SPA на React 19 з жорстким поділом `src/core/` (headless-логіка: карта, аудіо, лайки, форма, фільтри, конфіг дизайну — покрита тестами) і `src/ui/` (скін, що замінюється після handoff студії). Контракт API — `contracts/openapi.yaml` (contract-first), фікстури — спільні з дизайн-студією.

**Tech Stack:** Java 21, Spring Boot 4.x (fallback 3.5.x), Spring Data MongoDB, MongoDB 8, AWS SDK v2 (S3/MinIO), telegrambots-longpolling + telegrambots-client (остання версія, сумісна з обраним Boot), Caffeine, Bucket4j, Thumbnailator + TwelveMonkeys ImageIO + metadata-extractor, jaudiotagger, springdoc-openapi, JUnit 5, Mockito, Testcontainers; React 19, TypeScript 5.9, Vite 7, Tailwind CSS 4, React Router 7, TanStack Query 5, react-map-gl 8 + MapLibre GL 5, supercluster 8, Zustand 5, React Hook Form 7 + Zod 4, openapi-typescript, MSW 2, Vitest 3, Testing Library, Playwright; Docker Compose, nginx, GitHub Actions, GHCR.

**Spec:** `nashi_v2/docs/tasks/02-tech-team-task.md` (ТЗ технічної команди). Нормативні документи, обов'язкові до прочитання перед будь-якою задачею: `nashi_v2/docs/01-decisions.md` (рішення D-xx), `nashi_v2/docs/02-contracts.md` (API, модель, формула піна, токени, handoff). Аудит V1 і що з нього беремо: `nashi_v2/docs/00-v1-audit.md`. Код V1 — `nashi-backend/`, `nashi-frontend/` (поруч із `nashi_v2/`).

---

## Як читати цей план

- 33 задачі в шести етапах (T1–T33). Номер задачі = рекомендований порядок у межах ролі; залежності вказані в блоці **Interfaces → Consumes**.
- Шляхи — від кореня `nashi_v2/`. Java-пакети — під `backend/src/main/java/com/nashi/`, тести — під `backend/src/test/java/com/nashi/` у тому самому пакеті (позначено `test:`).
- Команди: backend — з `nashi_v2/backend/` (`./gradlew …`), web — з `nashi_v2/` (`npm … -w web`).
- «Перенести з V1» означає: скопіювати файл із `nashi-backend/…`/`nashi-frontend/…`, змінити пакет/імпорти і **перенести його тести разом із фікстурами**; поведінкові зміни перелічені в кроці.
- Тексти для людей (UI, Telegram, помилки) — українською; ключі, ідентифікатори, назви тестів — англійською.
- Кожна задача закінчується комітом у стилі Conventional Commits (`feat(map): …`, `test(audio): …`, `chore(ci): …`).

## Global Constraints

- Java 21; Spring Boot — остання GA 4.x на момент старту (fallback 3.5.x — рішення записується в `docs/adr/0001-spring-boot-version.md`); Node.js 24 LTS; npm workspaces (`web`, `studio`).
- API — лише `/api/v2/**`, точно за `docs/02-contracts.md` розділ 2 і `contracts/openapi.yaml`; помилки — RFC 9457 з `code` і `errors[]` (D-64).
- Регіонів рівно 27, коди й назви — `02-contracts.md` розділ 2 «Перелічення»; Київ (Q1899) → `KYIV_CITY`, Севастополь (Q7525) → `SEVASTOPOL_CITY` (D-11).
- Пін — формула `02-contracts.md` розділ 4; обчислюється один раз і зберігається; `EXACT` → радіус 0 (D-13).
- Пороги агрегації: `regionMaxZoom = 7`, `placeMaxZoom = 10`; межі зуму карти 5..17 (D-12, 2.11).
- Фрагмент: 15 с, fade-in 300 мс, fade-out 600 мс; hover-intent 250 мс; mobile-тригер за замовчуванням `TAP_PREVIEW_AND_PLAY` (D-21…D-26).
- Лайки: `count` 1..50, flush через 600 мс або на 50 тапах, `keepalive` при закритті; 20 запитів / 10 с / IP (D-30…D-32).
- Заявки: фото 1..5, `image/jpeg|png|webp`, ≤ 10 МБ кожне, ≤ 55 МБ запит; клієнт стискає до 2048 px; сервер — JPEG 1600 px + мініатюра 400 px без EXIF; 5 заявок / год / IP; пастка `faxNumber` (D-40, D-41, D-46).
- Модерація лише в групі `nashi.telegram.moderation.chat-id`; кнопки «✅ Так» / «❌ Ні»; callback `sub:<id>:y|n`; перший клік виграє (D-42).
- Секретів у репозиторії немає; профілі `local` і `test` фізично не можуть дістатися живих сервісів (`StartupConfigGuard`), Telegram long polling лише в `prod` (D-65, D-66).
- Тести backend — JUnit 5 + Mockito; інтеграційні — тільки Testcontainers (MongoDB 8, MinIO) у профілі `test`; жодних тестів проти живих API (Вікідані, Deezer, iTunes, Telegram) — лише записані відповіді в `src/test/resources/`.
- Вікімедіа: User-Agent `NashiBot/2.0 (+https://<домен>/about)` без особистої пошти, запити послідовно, пауза ≥ 1 с, повтор за `Retry-After` на 429/503 (D-52).
- Web: у `src/ui/**` жодних захардкожених кольорів/шрифтів — лише CSS-змінні `--n-*` (D-68); жодних захардкожених текстів — лише ключі `copy.uk.json` (`02-contracts.md` розділ 8).
- Mobile-first: мінімальна ширина 360 px, сенсорні цілі ≥ 44 px; desktop від 1024 px (D-04).
- Пояснення геомітки видно постійно на екрані карти, а також у картці-прев'ю і на сторінці музиканта (D-14).

## Review Focus

1. **Два адміністратори натискають «Так» одночасно або натискають стару кнопку після `/hide`** → створюється рівно один музикант, другий отримує «Заявку вже оброблено (статус: …)», нічого не змінюється. Тест: T16 `ModerationServiceIT.concurrentApprovalsCreateOneArtist`.
2. **Користувач багато разів тапає лайк і одразу закриває вкладку / сервер відповідає 429** → жоден тап не губиться: залишок іде з `keepalive`, після 429 повторюється з накопиченими тапами. Тест: T27 `batcher.test.ts › retries rate-limited taps without losing any` і `› flushAll sends remaining with keepalive`.
3. **Наведення на пін на desktop до першого кліку на сторінці; iPhone, де `volume` лише для читання** → немає винятку в консолі, стан `blocked`, показується `map.soundBlockedHint`; на iOS старт/стоп без fade. Тест: T25 `engine.test.ts › play rejected with NotAllowedError goes to blocked` і `› skips fades when volume is not writable`.
4. **Фото з iPhone: повернуте EXIF-орієнтацією і з GPS у метаданих; файл PDF з розширенням .jpg; 6 фото** → фото збережене правильно повернутим і без EXIF, PDF відхилений 415 з `field = images[n]`, 6-те фото — 400. Тест: T9 `ImageProcessorTest.appliesOrientationAndStripsExif`, `.rejectsRenamedPdf`; T14 `SubmissionControllerIT.rejectsSixthImage`.
5. **Музикант, чий пункт без площі, музикант з місцем поза Україною, 600 музикантів з Києва** → піни детерміновані (однакові між запусками), лежать у межах радіуса, поза Україною — музикант не потрапляє в `/map/pins`, але сторінка відкривається. Тест: T8 `PinPlacerTest` (вектори `contracts/test-vectors/pins.json`), T11 `MapServiceIT.artistWithoutPinIsNotOnMapButHasPage`.

---

## File Structure

```
nashi_v2/
  package.json                     npm workspaces: web, studio; скрипти верхнього рівня
  .github/workflows/               backend.yml, web.yml, deploy.yml                       (T31)
  contracts/
    openapi.yaml                   OpenAPI 3.1 — джерело правди                          (T1)
    test-vectors/                  pins.json, links.json, names.json — спільні вектори для Java і TS (T1, T7, T8)
    scripts/pin-vectors.mjs        еталонна JS-реалізація формули піна → pins.json        (T8)
    fixtures/, geo/, scripts/build-*.mjs                                                 (план студії, задача 2)
  docs/adr/                        0001-spring-boot-version.md, 0002-v1-migration-scope.md
  backend/
    build.gradle, settings.gradle, gradle/
    src/main/java/com/nashi/
      NashiApplication.java
      api/            Problem Details, винятки                                           (T2)
      config/         StartupConfigGuard, CORS, індекси, executors, NashiProperties       (T2, T7)
      health/         /api/v2/health                                                     (T2)
      geo/            Region (27), RegionCatalog, PinPlacer                               (T5, T8)
      place/          довідник places, пошук, сид                                        (T6)
      artist/         модель artists, slug, нормалізація, посилання, видимість            (T7, T10, T17)
      musician/       /musicians, /musicians/{slug}                                      (T10)
      map/            /map/pins|regions|places, ETag, кеш                                 (T11)
      filter/         /filters                                                           (T11)
      ratelimit/      Bucket4j, ClientIp                                                 (T12)
      like/           /musicians/{id}/likes                                              (T12)
      media/          FileStorage (S3/MinIO), ImageProcessor                             (T9)
      submission/     /submissions, валідація, антиспам, дублікати                       (T14)
      telegram/       шлюз Bot API, маршрутизатор оновлень, політика модераторів          (T15)
      moderation/     картка заявки, «Так»/«Ні», створення музиканта, збагачення          (T16)
      admin/          /hide /show /stats /wiki_import /audio_resolve /ping               (T17)
      jobs/           jobs_runs, очищення фото відхилених                                (T18)
      wiki/           порт модуля V1 (лише музиканти) + SummaryCleaner                   (T19)
      audio/          провайдери, резолвер, /fragment                                    (T20, T21)
      migration/      одноразовий перенос з V1                                           (T22)
    src/main/resources/
      application.properties, application-local.properties, application-test.properties
      geo/regions-meta.json (копія contracts/geo), geo/places-ua.json.gz (сид)
      META-INF/spring.factories (StartupConfigGuard)
  web/
    package.json, vite.config.ts, vitest.config.ts, playwright.config.ts, index.html
    scripts/design-sync.mjs                                                              (T29)
    src/
      app/            App, router, providers
      api/            client.ts, types.gen.ts (згенеровано), queries.ts, mocks/ (MSW)       (T3)
      core/           headless-логіка, 100 % під тестами Vitest
        i18n/ design/ map/ filters/ audio/ likes/ submission/
      design/         tokens.css, decisions.json, map-style.json, copy.uk.json, assets/ (з handoff) (T3, T29)
      ui/             скін: map/, musician/, suggest/, layout/, primitives/                (T24, T26, T28, T29)
      pages/          MapPage, MusicianPage, SuggestPage, AboutPage, NotFoundPage
    e2e/              *.spec.ts — проєкти mobile (iPhone 13) і desktop (1440×900)
  infra/
    docker-compose.yml             local: mongo 8, minio, backend, web                    (T4)
    docker-compose.prod.yml        prod: mongo 8, backend, web (образи з GHCR)            (T31)
    backend.Dockerfile, web.Dockerfile, nginx/web.conf, nginx/edge.conf
    scripts/local-up.sh, local-down.sh, backup-mongo.sh                                  (T4, T32)
    RELEASE.md                     чек-лист релізу і перемикання з V1                     (T33)
```

---

## Етапи, оцінка, календар

Ролі: **BE** — backend-розробник (Java), **FE** — frontend-розробник (React), **OPS** — devops, **QA** — тестування/приймання. Оцінки — людино-дні чистої роботи (з тестами й рев'ю), без буфера.

| Етап | Задачі | BE | FE | OPS | QA |
|---|---|---|---|---|---|
| 0. Фундамент | T1 контракт і монорепо, T2 каркас backend, T3 каркас web, T4 локальна інфраструктура | 4 | 3 | 1,5 | — |
| 1. Домен і публічне API | T5 регіони, T6 довідник місць, T7 модель artists, T8 PinPlacer, T9 медіа, T10 /musicians, T11 /map + /filters + /config, T12 лайки, T13 контракт-тест | 17 | — | — | 1 |
| 2. Заявки і модерація | T14 /submissions, T15 Telegram-інфраструктура, T16 модерація, T17 команди адміна, T18 фонові задачі | 11 | — | — | 1 |
| 3. Вікіпедія, аудіо, міграція | T19 порт імпорту, T20 аудіо-провайдери, T21 /fragment, T22 міграція V1 | 13 | — | — | 1 |
| 4. Web | T23 ядро карти, T24 карта (каркасний скін), T25 аудіо-двигун, T26 прев'ю і сторінка, T27 лайки, T28 форма, T29 підключення дизайну, T30 a11y і продуктивність | — | 27 | — | 3 |
| 5. Запуск | T31 CI/CD і prod-інфраструктура, T32 бекапи і моніторинг, T33 staging, приймання, перемикання | 2 | 1 | 4,5 | 3 |
| **Разом** | | **47** | **31** | **6** | **9** |

Склад: 1 BE + 1 FE на повний час, OPS і QA — частково (≈ 0,3 ставки). Вартість = людино-дні × ставка ролі (ставки дає замовник/підрядник; у плані не фіксуються).

Календар (≈ 11 тижнів від старту; т. = тиждень):

| Тиждень | BE | FE | Контрольна точка |
|---|---|---|---|
| т.1 | T1, T2 | T3 | **КТ1** (кінець т.1): `contracts/openapi.yaml` 1.0 погоджено з дизайн-командою (список у розділі «Відповіді на питання», п. 7); web і студія рендерять одні фікстури |
| т.2 | T5, T6, T7 | T23, T25 | — |
| т.3 | T8, T9, T10 | T27, T24 (на MSW) | — |
| т.4 | T11, T12, T13 | T24, T28 (core) | **КТ2**: staging з read API, картою і лайками; web перемкнуто з MSW на справжнє API |
| т.5 | T14, T15 | T28 (UI), T26 | — |
| т.6 | T16, T17, T18 | T26, T30 | Очікуваний handoff студії (план студії, кінець т.5–6) |
| т.7 | T19 | T29 (`design:sync`, конфіг рішень) | **КТ3**: наскрізний сценарій без фінального дизайну: заявка → Telegram → «Так» → музикант на карті; імпорт Вікіпедії на staging |
| т.8 | T20, T21 | T29 (скін) | — |
| т.9 | T22, T4/T31 підтримка | T29 (скін), T30 | **КТ4**: затверджений дизайн імплементовано; фрагменти грають на карті; міграція V1 — dry run |
| т.10 | T31, T32 (з OPS) | правки за QA | **КТ5**: приймання на staging за матрицею критеріїв |
| т.11 | T33 | T33 | **КТ6**: запуск V2 на production-домені |

Якщо handoff студії запізнюється, КТ4 зсувається на те саме число тижнів; КТ1–КТ3 від студії не залежать.

---
## Етап 0. Фундамент

### Task 1: Монорепозиторій і контракт API (`contracts/openapi.yaml`)

**Files:**
- Create: `package.json`, `.gitignore`, `.editorconfig`, `.nvmrc` (`24`), `README.md` (як запустити; посилання на `docs/`)
- Create: `contracts/openapi.yaml`, `contracts/README.md`, `contracts/package.json` (workspace `contracts`, лише dev-залежність `@redocly/cli`)
- Create: `contracts/test-vectors/links.json`, `contracts/test-vectors/names.json`
- Create: `contracts/tests/openapi.test.mjs`
- Create: `docs/adr/0001-spring-boot-version.md` (шаблон; заповнюється в T2)

**Interfaces:**
- Consumes: `docs/02-contracts.md` розділи 1–2.
- Produces: `contracts/openapi.yaml` зі схемами (точні імена — їх використовують T3 `openapi-typescript`, T10–T21 як імена Java-record і T13 контракт-тест): `MapPin`, `MapPinsResponse`, `RegionStat`, `PlaceStat`, `FilterDefinition`, `FilterOption`, `FilterType`, `MusicianListItem`, `MusicianPage`, `MusicianDetails`, `TextSource`, `Image`, `ImageCredit`, `Origin`, `LifeDates`, `Materials`, `ClipSummary`, `Work`, `Video`, `Link`, `Fragment`, `LikeRequest`, `LikeResponse`, `PlaceSuggestion`, `SubmissionRequest`, `SubmissionAccepted`, `PublicConfig`, `Health`, `Problem`, `FieldError`; перелічення `Region`, `MusicianKind`, `OriginBasis`, `PinPrecision`, `AudioSource`, `LinkPlatform`, `LinkGroup`, `PlaceType`, `WorkType`. `operationId`: `getMapPins`, `getMapRegions`, `getMapPlaces`, `getFilters`, `listMusicians`, `getMusician`, `getFragment`, `addLikes`, `searchPlaces`, `createSubmission`, `getPublicConfig`, `getHealth`.
- Produces: `contracts/test-vectors/links.json` — масив `{ "url": string, "platform": LinkPlatform }` (≥ 30 рядків, по 2+ на кожну платформу, включно з `music.youtube.com` → `YOUTUBE_MUSIC`, `youtu.be` → `YOUTUBE`, `uk.m.wikipedia.org` → `WIKIPEDIA`, `t.me` → `TELEGRAM`, `x.com` і `twitter.com` → `X`, невідомий домен → `WEBSITE`, `mailto:` → `OTHER`); `names.json` — масив `{ "input", "normalized", "slug" }` (заповнюється в T7).

- [ ] **Step 1: Ініціалізувати репозиторій**

```bash
cd /Users/bohdanlevkovych/development/nashi/nashi_v2 && git init -b main
```
`package.json`: `"private": true`, `"workspaces": ["contracts", "web", "studio"]`, скрипти `"test": "npm test --workspaces --if-present"`, `"lint:api": "npm run lint -w contracts"`. `.gitignore`: `node_modules/`, `dist/`, `build/`, `.gradle/`, `*.env`, `.env.*` (крім `.env.example`), `/infra/secrets/`.

- [ ] **Step 2: Написати тест контракту**

`contracts/tests/openapi.test.mjs` (node:test + `yaml` пакет): читає `openapi.yaml` і перевіряє:
```js
test('Region enum has exactly 27 values incl. KYIV_CITY and SEVASTOPOL_CITY', () => {
  const r = spec.components.schemas.Region.enum;
  assert.equal(r.length, 27);
  assert.ok(r.includes('KYIV_CITY') && r.includes('SEVASTOPOL_CITY'));
});
test('every operation has an operationId from the agreed list', () => { /* 12 ids above, no more, no less */ });
test('every 4xx/5xx response uses application/problem+json with Problem schema', () => {});
test('MapPin has exactly the fields of 02-contracts 2.1', () => {
  assert.deepEqual(Object.keys(spec.components.schemas.MapPin.properties).sort(),
    ['hasAudio','id','kind','lat','lon','name','placeId','region','slug','thumbUrl'].sort());
});
test('LikeRequest.count is integer 1..50', () => {});
test('SubmissionRequest required = name, kind, originPlaceId, summary, links, consent', () => {});
```

- [ ] **Step 3: Запустити — тест падає (файла ще немає)**

Run: `node --test contracts/tests/` → Expected: FAIL `ENOENT … openapi.yaml`.

- [ ] **Step 4: Написати `contracts/openapi.yaml`** за `02-contracts.md` розділ 2 дослівно: `openapi: 3.1.0`, `servers: [{url: /api/v2}]`, приклади (`examples`) для кожної відповіді взяті з `02-contracts.md`. `SubmissionRequest` описується як `multipart/form-data` з частинами `data` (`contentMediaType: application/json`, схема `SubmissionRequest`) і `images` (`type: array, items: {type: string, contentMediaType: image/*}`, `minItems: 1`, `maxItems: 5`). Заповнити `links.json`.

- [ ] **Step 5: Запустити тест і лінтер**

Run: `node --test contracts/tests/ && npx @redocly/cli lint contracts/openapi.yaml` → Expected: усі тести PASS, `Woohoo! Your API description is valid.`

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(contracts): add OpenAPI 3.1 contract for API v2 and shared test vectors"
```

### Task 2: Каркас backend (Gradle, профілі, StartupConfigGuard, Problem Details, health)

**Files:**
- Create: `backend/settings.gradle`, `backend/build.gradle`, `backend/gradle/wrapper/*`, `backend/gradlew`
- Create: `com/nashi/NashiApplication.java`
- Create: `com/nashi/api/{ApiException,FieldViolation,ValidationFailedException,NotFoundException,RateLimitedException,PayloadTooLargeException,UnsupportedMediaException,ProblemDetailsAdvice}.java`
- Create: `com/nashi/config/{StartupConfigGuard,CorsConfig,AsyncConfig,ClockConfig,NashiProperties}.java`
- Create: `com/nashi/health/HealthController.java`
- Create: `src/main/resources/{application.properties,application-local.properties,application-test.properties,META-INF/spring.factories}`
- Test: `config/StartupConfigGuardTest.java`, `api/ProblemDetailsAdviceTest.java`, `health/HealthControllerTest.java`, `support/MongoIT.java` (база для інтеграційних тестів)
- Modify: `docs/adr/0001-spring-boot-version.md`

**Interfaces:**
- Consumes: V1 `nashi-backend/src/main/java/com/nashi/config/StartupConfigGuard.java` (перенести з V1, змінивши ключі) і його тест.
- Produces:
  - `public abstract class ApiException extends RuntimeException { public abstract HttpStatus status(); public abstract String code(); public List<FieldViolation> errors() }`
  - `public record FieldViolation(String field, String message)`
  - `new ValidationFailedException(List<FieldViolation>)` → 400 `VALIDATION_FAILED`; `new NotFoundException(String what)` → 404 `NOT_FOUND`; `new RateLimitedException(long retryAfterSeconds)` → 429 `RATE_LIMITED` + `Retry-After`; `PayloadTooLargeException` → 413; `UnsupportedMediaException(String field)` → 415.
  - `ProblemDetailsAdvice` (`@RestControllerAdvice`): `ApiException`, `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `MaxUploadSizeExceededException` (→ 413), `HttpMessageNotReadableException` (→ 400), інше → 500 `INTERNAL` без тексту винятку.
  - `@ConfigurationProperties("nashi") public record NashiProperties(Cors cors, Audio audio, Map map, Likes likes, Submission submission, Security security)` зі значеннями за замовчуванням з `02-contracts.md` 2.11 (`audio.fragmentSeconds=15`, `map.regionMaxZoom=7`, `map.placeMaxZoom=10`, `map.minZoom=5`, `map.maxZoom=17`, `likes.batchMax=50`, `likes.flushMs=600`, `submission.maxImages=5`, `submission.maxImageMb=10`, `submission.summaryMin=30`, `submission.summaryMax=1000`, `security.ipHashSecret`).
  - `AsyncConfig`: бін-и `Executor telegramExecutor` (1 потік, черга 500) і `Executor jobsExecutor` (1 потік, черга 10).
  - `ClockConfig`: `@Bean Clock clock()` = `Clock.systemUTC()`; константа `ClockConfig.KYIV = ZoneId.of("Europe/Kyiv")`.
  - `abstract class MongoIT` (test): `@Testcontainers`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, статичний `MongoDBContainer("mongo:8")` + `MinIOContainer`, `@DynamicPropertySource` пише URI. Усі `*IT` наслідують його.

- [ ] **Step 1: Gradle і ADR.** Згенерувати проєкт (start.spring.io: Gradle Groovy, Java 21, Web, Data MongoDB, Validation, Actuator). Додати: `org.springdoc:springdoc-openapi-starter-webmvc-ui` (лінія, сумісна з обраним Boot), `org.projectlombok:lombok`, `com.github.ben-manes.caffeine:caffeine`, `com.bucket4j:bucket4j_jdk17-core`, `software.amazon.awssdk:s3` (BOM), `org.telegram:telegrambots-longpolling`, `org.telegram:telegrambots-client`, `net.coobird:thumbnailator`, `com.twelvemonkeys.imageio:imageio-webp`, `com.drewnoakes:metadata-extractor`, `net.jthink:jaudiotagger`, `com.google.code.gson:gson` (потрібен модулю wiki з V1); тестові — `org.testcontainers:mongodb`, `org.testcontainers:minio`, `org.testcontainers:junit-jupiter`. У `0001-spring-boot-version.md` записати обрану версію Boot і перевірені версії springdoc і telegrambots (якщо хоч одна не підтримує Boot 4 — Boot 3.5.x і причина).

- [ ] **Step 2: Перенести з V1 `StartupConfigGuard` і написати тести на нові правила**

`StartupConfigGuardTest` (plain JUnit + `MockEnvironment`):
```java
@Test void prodRequiresAllSecrets() // без профілю: відсутні ключі → problems() містить кожен з:
  // "spring.mongodb.uri" (у Boot 3.5 — "spring.data.mongodb.uri"), "nashi.s3.bucket", "nashi.s3.access-key-id",
  // "nashi.s3.secret-access-key", "nashi.telegram.bot-token", "nashi.telegram.moderation.chat-id",
  // "nashi.security.ip-hash-secret"
@Test void localRejectsRemoteMongo()          // local + mongodb+srv://… → problem
@Test void localRejectsTelegramPolling()      // local + nashi.telegram.polling-enabled=true → problem
@Test void localRejectsRealS3Endpoint()       // local + nashi.s3.endpoint порожній (=AWS) → problem
@Test void testProfileHasSameRulesAsLocal()   // test + mongodb://localhost:27017 + polling false + s3 endpoint http://localhost:9000 → no problems
@Test void messagesNeverContainValues()       // секрет "abc123" не з'являється в жодному повідомленні
```
Правило для `local`/`test`: Mongo — лише `mongodb://` на `localhost|127.0.0.1|mongo|host.docker.internal` або порт, виданий Testcontainers (`localhost:<будь-який>`); `nashi.telegram.polling-enabled=false`, `nashi.telegram.sending-enabled=false`; `nashi.s3.endpoint` — `http://localhost…|http://127.0.0.1…|http://minio…`.

- [ ] **Step 3: Запустити — падає.** Run: `./gradlew test --tests 'com.nashi.config.StartupConfigGuardTest'` → FAIL (нових ключів ще немає).
- [ ] **Step 4: Реалізувати** `static List<String> problems(ConfigurableEnvironment env)` з новими ключами; зареєструвати в `META-INF/spring.factories` (`org.springframework.boot.env.EnvironmentPostProcessor=com.nashi.config.StartupConfigGuard`). `application.properties`: `spring.config.import=optional:file:${NASHI_SECRETS_FILE:/etc/nashi-v2/backend.properties}`, `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=55MB`, `server.forward-headers-strategy=framework`, `server.compression.enabled=true`, `springdoc.api-docs.path=/v3/api-docs`. `application-local.properties` і `application-test.properties` — безпечні значення з кроку 2.
- [ ] **Step 5: Тести Problem Details і health**

`ProblemDetailsAdviceTest` (MockMvc `standaloneSetup` з тестовим контролером, що кидає кожен виняток):
```java
@Test void validationFailedHasCodeAndErrors()   // status 400, content-type application/problem+json,
                                                 // $.code == "VALIDATION_FAILED", $.errors[0].field == "summary"
@Test void rateLimitedSetsRetryAfter()          // 429, header Retry-After == "7", $.code == "RATE_LIMITED"
@Test void unknownExceptionHidesMessage()       // 500, $.code == "INTERNAL", body не містить "secret-detail"
@Test void maxUploadSizeIs413()                 // MaxUploadSizeExceededException → 413 PAYLOAD_TOO_LARGE
```
`HealthControllerTest`: Mongo `ping` OK → `200 {"status":"UP"}`; виняток → `503 {"status":"DOWN"}` без тексту винятку.

- [ ] **Step 6: Реалізувати** `ProblemDetailsAdvice`, `HealthController` (`GET /api/v2/health`, `MongoTemplate.executeCommand("{ping:1}")`), `CorsConfig` (як у V1: `nashi.cors.allowed-origins`, без credentials, методи GET/POST).
- [ ] **Step 7: Запустити всі тести.** Run: `./gradlew test` → PASS.
- [ ] **Step 8: Commit** `feat(backend): bootstrap Spring Boot app with config guard, problem details and health`

### Task 3: Каркас web (Vite, роутер, API-клієнт, MSW, токени, тексти)

**Files:**
- Create: `web/package.json`, `web/vite.config.ts`, `web/vitest.config.ts`, `web/playwright.config.ts`, `web/index.html`, `web/tsconfig.json`, `web/.env.example`
- Create: `web/src/main.tsx`, `src/app/{App.tsx,router.tsx,providers.tsx}`, `src/pages/{MapPage,MusicianPage,SuggestPage,AboutPage,NotFoundPage}.tsx` (порожні заглушки з заголовком)
- Create: `src/api/client.ts`, `src/api/types.gen.ts` (згенеровано), `src/api/queries.ts`, `src/api/mocks/{handlers.ts,browser.ts,node.ts}`
- Create: `src/core/i18n/{copy.ts,defaults.uk.ts}`, `src/core/design/{config.ts,defaults.ts,types.ts}`
- Create: `src/design/{tokens.css,decisions.json,map-style.json,copy.uk.json}` (каркасна тема: нейтральні сірі, системний шрифт; `decisions.json` = `{"schemaVersion":1,"decisions":{}}`; `map-style.json` — стиль OpenMapTiles для OpenFreeMap з плейсхолдерами `{{TILES_URL}}`, `{{GLYPHS_URL}}`)
- Test: `src/api/client.test.ts`, `src/core/i18n/copy.test.ts`, `src/core/design/config.test.ts`, `e2e/smoke.spec.ts`

**Interfaces:**
- Consumes: `contracts/openapi.yaml` (T1), `contracts/fixtures/**` (план студії, задача 2; якщо ще немає — тимчасово 3 записи вручну в `src/api/mocks/seed.ts`, видалити, коли фікстури з'являться).
- Produces:
  - `export class ApiProblem extends Error { status: number; code: string; errors: { field: string; message: string }[]; retryAfterSec?: number }`
  - `export async function apiFetch<T>(path: string, init?: RequestInit & { query?: Record<string, string | string[] | number | undefined> }): Promise<T>` — масиви в query повторюють ключ (`region=A&region=B`, як V1 `client-params`); не-2xx з `application/problem+json` → `ApiProblem`; мережева помилка → `ApiProblem` зі `status: 0`, `code: 'NETWORK'`; `204` → `undefined`.
  - `queries.ts`: `useMapPins()`, `useMapRegions()`, `useMapPlaces()`, `useFilters()`, `usePublicConfig()`, `useMusicians(params)`, `useMusician(slug)`, `usePlaceSearch(q, region?)`; ключі `['map','pins']`, `['map','regions']`, `['map','places']`, `['filters']`, `['config']`, `['musicians','list',params]`, `['musicians','detail',slug]`, `['places','search',q,region]`; `staleTime` 5 хв, для `map/*` — 10 хв.
  - `export type CopyKey = keyof typeof DEFAULT_COPY`; `export function createCopy(overrides: Record<string,string>, defaults = DEFAULT_COPY): (key: CopyKey, vars?: Record<string, string | number>) => string`; хук `useCopy()`.
  - `export type DecisionId = 'identity' | 'map.basemap' | 'map.initial-view' | 'map.aggregation' | 'map.pin' | 'map.cluster' | 'map.origin-notice' | 'audio.mobile-trigger' | 'audio.desktop-hover' | 'audio.player' | 'musician.preview' | 'musician.page' | 'like.button' | 'filters.region' | 'submission.form' | 'nav.structure' | 'onboarding'`
  - `export interface DesignConfig { variant(id: DecisionId): string; param(id: DecisionId, name: string, fallback: number): number }`; `export function loadDesignConfig(file: DecisionsFile | null): DesignConfig`; хук `useDesign()`.
  - Env: `VITE_API_BASE_URL` (за замовчуванням `/api/v2`), `VITE_MAP_TILES_URL` (за замовчуванням `https://tiles.openfreemap.org/planet`), `VITE_MAP_GLYPHS_URL`, `VITE_API_MOCK` (`1` → MSW у браузері), `VITE_MAP_STYLE` (`blank` → стиль лише з фоном для E2E без мережі).

- [ ] **Step 1: Створити проєкт** (`npm create vite@latest web -- --template react-ts`), залежності з «Tech Stack», Tailwind 4 через `@tailwindcss/vite`; у `src/index.css`: `@import "tailwindcss"; @import "./design/tokens.css";` і `@theme inline` з відображенням `--color-bg: var(--n-color-bg)` тощо для всіх кольорових токенів `02-contracts.md` розділ 5 (щоб класи `bg-bg`, `text-text-muted` читали токени). Скрипт `"gen:api": "openapi-typescript ../contracts/openapi.yaml -o src/api/types.gen.ts"`.
- [ ] **Step 2: Тести**
```ts
// copy.test.ts
it('plural uk: like count', () => {
  const t = createCopy({ 'musician.likesCount': '{count, plural, one {# лайк} few {# лайки} many {# лайків} other {# лайка}}' });
  expect([1, 3, 5, 11, 21, 22, 25].map(n => t('musician.likesCount', { count: n })))
    .toEqual(['1 лайк', '3 лайки', '5 лайків', '11 лайків', '21 лайк', '22 лайки', '25 лайків']);
});
it('falls back to default and then to the key', () => { /* відсутній override → DEFAULT_COPY; невідомий → сам ключ */ });
it('substitutes {name}', () => {});
// client.test.ts (MSW node)
it('repeats array params', async () => { /* region=['LVIV','KYIV_CITY'] → '?region=LVIV&region=KYIV_CITY' */ });
it('maps problem+json to ApiProblem with field errors', async () => {});
it('reads Retry-After into retryAfterSec', async () => {});
it('network failure becomes ApiProblem status 0 code NETWORK', async () => {});
// config.test.ts
it('uses defaults from 01-decisions when decisions are empty', () => {
  const c = loadDesignConfig(null);
  expect(c.variant('audio.mobile-trigger')).toBe('TAP_PREVIEW_AND_PLAY');
  expect(c.variant('map.aggregation')).toBe('REGION_PLACE_PIN');
});
it('ignores PROPOSED decisions and applies APPROVED ones with params', () => {});
it('unknown variant id for a technical decision falls back to default and warns once', () => {});
```
`DEFAULT_COPY` (у `defaults.uk.ts`) містить **усі** обов'язкові ключі `02-contracts.md` розділ 8; тест `it('has every required key from contracts section 8')` перевіряє список.
Дефолти `DesignConfig` (`defaults.ts`): `map.initial-view=UKRAINE`, `map.aggregation=REGION_PLACE_PIN`, `map.pin=AVATAR_RING`, `map.cluster=BUBBLE_COUNT`, `map.origin-notice=PERSISTENT_CHIP`, `audio.mobile-trigger=TAP_PREVIEW_AND_PLAY`, `audio.desktop-hover=RING_PROGRESS`, `audio.player=MINI_BAR`, `musician.preview=BOTTOM_SHEET`, `musician.page=HERO`, `like.button=HEART_BURST`, `filters.region=SHEET_LIST`, `submission.form=SINGLE_PAGE`, `nav.structure=MAP_ONLY_SHEETS`, `onboarding=NONE`.
- [ ] **Step 3: Запустити — падає.** Run: `npm run test -w web` → FAIL (модулів немає).
- [ ] **Step 4: Реалізувати** `client.ts`, `copy.ts` (мінімальний парсер ICU: `{var}`, `{count, plural, one {…} few {…} many {…} other {…}}`, `#`; категорії з `new Intl.PluralRules('uk')`), `config.ts`, `queries.ts`, MSW `handlers.ts` (віддає `contracts/fixtures/*` за маршрутами 2.1–2.11; `POST likes` — сумує в пам'яті; `POST submissions` — 202 після 1,5 с), роутер: `/` → `MapPage`, `/m/:slug` → `MusicianPage`, `/suggest` → `SuggestPage`, `/about` → `AboutPage`, `*` → `NotFoundPage`; усі сторінки — `React.lazy`.
- [ ] **Step 5: Playwright.** `playwright.config.ts`: проєкти `mobile` (`devices['iPhone 13']`) і `desktop` (`viewport 1440×900`, Chromium); `webServer`: `npm run dev -w web` з `VITE_API_MOCK=1 VITE_MAP_STYLE=blank`. `e2e/smoke.spec.ts`: відкриває `/`, бачить `role=main`; відкриває `/m/<slug з фікстур>`, бачить `heading` з ім'ям.
- [ ] **Step 6: Запустити.** Run: `npm run test -w web && npx playwright install --with-deps chromium webkit && npm run e2e -w web` → PASS на обох проєктах.
- [ ] **Step 7: Commit** `feat(web): bootstrap SPA with typed API client, MSW, copy and design config`

### Task 4: Локальна інфраструктура (Docker Compose)

**Files:**
- Create: `infra/docker-compose.yml`, `infra/backend.Dockerfile`, `infra/web.Dockerfile`, `infra/nginx/web.conf`, `infra/scripts/local-up.sh`, `infra/scripts/local-down.sh`, `infra/LOCAL.md`

**Interfaces:**
- Consumes: T2 (backend jar), T3 (web build).
- Produces: локальний стек: `mongo:8` (порт `127.0.0.1:27018`, щоб не конфліктувати з V1 на 27017), `minio` (`127.0.0.1:9000`, консоль `9001`, бакет `nashi-v2` створює одноразовий сервіс `minio-init` з `mc mb --ignore-existing` і `mc anonymous set download nashi-v2/artists`), `backend` (`SPRING_PROFILES_ACTIVE=local`, `127.0.0.1:8081`), `web` (nginx, `127.0.0.1:4174`, проксі `/api/` → backend).

- [ ] **Step 1: Dockerfile-и.** `backend.Dockerfile`: збірка `eclipse-temurin:21-jdk` (`./gradlew bootJar -x test`), рантайм `eclipse-temurin:21-jre` + `curl`, `ENV SPRING_PROFILES_ACTIVE=local` (як V1: образ за замовчуванням не може стартувати як prod), `HEALTHCHECK curl -fsS localhost:8080/api/v2/health`. `web.Dockerfile`: `node:24` → `npm ci && npm run build -w web` з build-arg `VITE_*`; рантайм `nginx:1.27-alpine` з `nginx/web.conf` (SPA fallback `try_files $uri /index.html`; `/assets/` — `Cache-Control: public, max-age=31536000, immutable`; `/api/` → `proxy_pass http://backend:8080`; `client_max_body_size 60m`).
- [ ] **Step 2: Перевірити.** Run: `infra/scripts/local-up.sh && curl -fsS http://127.0.0.1:8081/api/v2/health && curl -fsS http://127.0.0.1:4174/ | grep -q '<div id="root">'` → Expected: `{"status":"UP"}`, grep exit 0.
- [ ] **Step 3: Commit** `chore(infra): add local docker compose stack with mongo 8 and minio`

---

## Етап 1. Домен і публічне API

### Task 5: Регіони (27) і `RegionCatalog`

**Files:**
- Create: `com/nashi/geo/{Region.java,RegionMeta.java,RegionCatalog.java}`
- Create: `src/main/resources/geo/regions-meta.json` (копія `contracts/geo/regions-meta.json`; Gradle-задача `copyGeo` у `processResources` копіює її автоматично)
- Test: `geo/RegionTest.java`, `geo/RegionCatalogTest.java`

**Interfaces:**
- Consumes: `contracts/geo/regions-meta.json` (план студії, задача 2).
- Produces:
  - `public enum Region { CRIMEA, VINNYTSIA, …, KYIV_CITY, SEVASTOPOL_CITY; public String uaName() }` — 27 значень у порядку `02-contracts.md`; `uaName()` як V1 `Region.uaName` + «Київ», «Севастополь».
  - `public record RegionMeta(Region code, String name, double[] center, double[] bbox)` (`center` = `[lon, lat]`, `bbox` = `[w, s, e, n]`)
  - `@Component public class RegionCatalog { public List<RegionMeta> all(); public RegionMeta of(Region r) }`

- [ ] **Step 1: Тести**
```java
@Test void hasExactly27Regions()                  // Region.values().length == 27
@Test void namesMatchContract()                   // Region.KYIV_CITY.uaName() == "Київ", LVIV → "Львівська область", CRIMEA → "Автономна Республіка Крим"
@Test void catalogCoversEveryRegion()             // all().size()==27, кожен Region має meta
@Test void centerLiesInsideBbox()                 // для кожного: w ≤ center[0] ≤ e, s ≤ center[1] ≤ n
@Test void kyivCityBboxInsideKyivOblastBbox()     // KYIV_CITY bbox ⊂ KYIV bbox
```
- [ ] **Step 2: Запустити — падає.** `./gradlew test --tests 'com.nashi.geo.*'` → FAIL.
- [ ] **Step 3: Реалізувати** (`RegionCatalog` читає JSON Jackson-ом у `@PostConstruct`, падає при старті, якщо регіонів ≠ 27).
- [ ] **Step 4: Запустити — PASS.**
- [ ] **Step 5: Commit** `feat(geo): add 27 regions with catalog of centers and bounds`

### Task 6: Довідник населених пунктів (`places`), сид і пошук

**Files:**
- Create: `com/nashi/place/{Place,PlaceType,PlaceRepository,PlaceSuggestion,PlaceSearchService,PlaceController,PlaceSeeder,PlaceStore}.java`
- Create: `com/nashi/place/seed/{PlaceSeedGenerator,PlaceTypes,PlaceSeedRow}.java`; Gradle-задача `placesSeed` (`JavaExec`, як V1 `wikiDryRun`)
- Create: `src/main/resources/geo/places-ua.json.gz` (результат генератора, комітиться), `src/main/resources/geo/places-ua.version` (дата генерації)
- Test: `place/seed/PlaceSeedGeneratorTest.java` (фікстура `src/test/resources/places/sparql-lviv.json`), `place/PlaceSearchServiceIT.java`, `place/PlaceControllerIT.java`, `place/PlaceSeederIT.java`

**Interfaces:**
- Consumes: T5 `Region`; V1 `com.nashi.wiki.PoliteWikiHttp`, `WikiHttp`, `SparqlParser`, `UkraineRegions` (переносяться в T19, але генератору потрібні раніше — перенести ці 4 класи тут разом із тестами; `UkraineRegions` одразу з 27 регіонами: `Q1899 → KYIV_CITY`, `Q7525 → SEVASTOPOL_CITY`, `Q170036 → KYIV`, `Q756294 → CRIMEA`); T7 `Names.normalize` (якщо T7 ще не зроблено — створити `artist/Names.java` тут, T7 його доповнить).
- Produces:
  - `@Document("places") public record Place(@Id String id, String name, String nameNormalized, PlaceType type, Region region, GeoJsonPoint point, Double areaKm2, Long population)`
  - `public enum PlaceType { CITY, TOWN, VILLAGE, OTHER }`
  - `public record PlaceSuggestion(String placeId, String name, PlaceType type, Region region, String regionName, double lat, double lon)`
  - `PlaceSearchService.search(String q, @Nullable Region region, int limit): List<PlaceSuggestion>`
  - `PlaceStore.find(String placeId): Optional<Place>`; `PlaceStore.ensure(String qid, String name, Region region, double lat, double lon): Place` (upsert з `type = OTHER`, якщо пункту немає в сиді); `PlaceStore.nearest(double lat, double lon, double maxMeters): Optional<Place>`
  - `GET /api/v2/places/search?q=&region=&limit=` → `List<PlaceSuggestion>`; `q` < 2 символів → 400 `VALIDATION_FAILED` (`field: "q"`).

- [ ] **Step 1: Визначити типи населених пунктів.** Один SPARQL-запит до `query.wikidata.org` (вручну, через `PoliteWikiHttp` з `PlaceSeedGenerator --types`): розподіл `P31` для сутностей з `P17 = Q212` і `P625`, що є підкласами `Q486972` (human settlement). Скласти таблицю `PlaceTypes` (QID → `PlaceType`: міста → `CITY`, селища/смт → `TOWN`, села/селища сільського типу/хутори → `VILLAGE`, решта → `OTHER`) з коментарем-міткою кожного QID; таблицю закріпити тестом `PlaceTypesTest.knownQidsMapToExpectedTypes` (≥ 5 QID з відомими мітками, напр. `Q515 → CITY`, `Q532 → VILLAGE`).
- [ ] **Step 2: Тест генератора на записаній відповіді**
```java
@Test void parsesSparqlRowsIntoSeedRows()  // фікстура з ≥ 3 рядками Львівщини: Q36036 Львів CITY LVIV area 182.01 pop 717273; Q465104 Броди TOWN LVIV
@Test void prefersHigherRankTypeWhenSeveralP31()  // CITY > TOWN > VILLAGE > OTHER
@Test void dropsRowsWithoutUkLabelOrCoordinates()
@Test void dedupesByQidAcrossOblastQueries()
```
- [ ] **Step 3: Запустити — падає; реалізувати** `PlaceSeedGenerator.main(String[] args)`: для кожного QID області з `UkraineRegions` (крім міст) і окремо для Києва та Севастополя — запит
```sparql
SELECT ?place ?label ?coord ?type ?area ?pop WHERE {
  ?place wdt:P131+ wd:%OBLAST% ; wdt:P625 ?coord ; wdt:P31 ?type .
  VALUES ?type { %TYPES% }
  ?place rdfs:label ?label FILTER(LANG(?label) = "uk")
  OPTIONAL { ?place wdt:P2046 ?area } OPTIONAL { ?place wdt:P1082 ?pop }
}
```
(для Києва/Севастополя — сам QID міста як пункт `CITY` плюс його населені пункти); при таймауті області — ділити за `P131` районів (як V1 `ChunkSplitter`); результат — `places-ua.json.gz` (рядок JSON на пункт, поля `PlaceSeedRow`), сортований за QID. Запустити `./gradlew placesSeed` і закомітити файл.
- [ ] **Step 4: Перевірити сид** тестом `PlaceSeedFileTest` (читає ресурс): `≥ 25_000` рядків; усі 27 регіонів мають ≥ 1 пункт; присутні `Q36036` (Львів, `LVIV`), `Q465104` (Броди, `LVIV`), `Q1899` (Київ, `KYIV_CITY`), `Q7525` (Севастополь, `SEVASTOPOL_CITY`).
- [ ] **Step 5: Інтеграційні тести пошуку і сідера**
```java
// PlaceSearchServiceIT (MongoIT, 6 пунктів у фікстурі)
@Test void prefixOfAnyWordMatches()        // "под" знаходить "Кам'янець-Подільський"; "кам'ян" і "камян" — теж
@Test void ignoresCaseApostrophesAndStress() // "БРОДИ", "бро́ди" → Броди
@Test void sortsByPopulationDesc()          // "ки" → Київ перед Києво-…
@Test void filtersByRegion()
@Test void limitsResults()                  // limit 2 → 2
// PlaceSeederIT
@Test void seedsEmptyCollectionOnce()       // перший старт: count ≥ 25000; повторний старт з тією ж версією — без запису
@Test void reseedsWhenVersionChanges()
// PlaceControllerIT
@Test void shortQueryIs400()                // q="к" → 400 VALIDATION_FAILED, errors[0].field == "q"
```
- [ ] **Step 6: Реалізувати** `PlaceSearchService` (регулярний вираз `(^|[ \-])` + `Pattern.quote(Names.normalize(q))` по `nameNormalized`, сорт `population` desc, `nulls last`), `PlaceSeeder` (`ApplicationRunner`; версія у документі `places_meta {_id:"seed", version}`; bulk upsert пачками по 1000), `PlaceStore`, `PlaceController`; індекси `nameNormalized_1_population_-1`, `region_1`, `point_2dsphere` додати в `MongoIndexes` (T7).
- [ ] **Step 7: Запустити** `./gradlew test --tests 'com.nashi.place.*'` → PASS.
- [ ] **Step 8: Commit** `feat(place): add Ukrainian settlements gazetteer seeded from Wikidata with prefix search`

### Task 7: Модель `artists`, нормалізація імен, slug, індекси, класифікація посилань

**Files:**
- Create: `com/nashi/artist/{Artist,ArtistStatus,ArtistSource,Discipline,MusicianKind,Origin,OriginBasis,PinPrecision,ImageAsset,ImageCredit,LifeDates,Link,LinkPlatform,LinkGroup,LinkClassifier,Work,WorkType,Video,AudioClip,AudioSource,TextSource,ArtistRepository,Names,SlugGenerator,SlugAllocator,ArtistChangedEvent}.java`
- Create: `com/nashi/config/MongoIndexes.java`
- Test: `artist/NamesTest.java`, `artist/SlugGeneratorTest.java`, `artist/SlugAllocatorTest.java`, `artist/LinkClassifierTest.java`, `config/MongoIndexesIT.java`
- Modify: `contracts/test-vectors/names.json`

**Interfaces:**
- Consumes: T5 `Region`, T6 `PlaceType`.
- Produces:
  - `@Document("artists") @Data @Builder public class Artist` з полями `02-contracts.md` розділ 3 (типи: `origin: Origin`, `pin: GeoJsonPoint`, `images: List<ImageAsset>`, `links: List<Link>`, `audio: List<AudioClip>`, `likesCount: long`, `createdAt/updatedAt/publishedAt: Instant` з `@CreatedDate`/`@LastModifiedDate`) плюс внутрішнє `legacyV1Id: String` (див. T22).
  - `public record Origin(String placeId, String placeName, Region region, OriginBasis basis, PinPrecision precision, GeoJsonPoint center)`
  - `public record ImageAsset(String key, String url, String thumbKey, String thumbUrl, String alt, ImageCredit credit)`
  - `public record AudioClip(String clipId, AudioSource source, String externalId, String key, String url, String title, int durationSec, String attribution, String externalUrl)`
  - `public record Link(LinkPlatform platform, String url)`; `public enum LinkPlatform { …; public LinkGroup group() }` (групи — `02-contracts.md` «Перелічення»)
  - `LinkClassifier.classify(String url): LinkPlatform` (статичний)
  - `Names.normalize(String): String` — нижній регістр (`Locale.ROOT`), без `«»"„“”`, без апострофів `'’ʼ‘\``, без комбінованого наголосу `U+0301`, один пробіл, `trim`. **Відмінність від V1 `NameNormalizer`**: V1 залишав апостроф як `'`; V2 видаляє (контракт 2.5, 2.9).
  - `SlugGenerator.slugify(String name): String` — транслітерація за постановою КМУ № 55 (2010): `є→ye/ie`, `ї→yi/i`, `й→y/i`, `ю→yu/iu`, `я→ya/ia` (перший варіант на початку слова), `зг→zgh`, `х→kh`, `ц→ts`, `ч→ch`, `ш→sh`, `щ→shch`, `ь` і апостроф — пропуск; нижній регістр; не `[a-z0-9]` → `-`; кілька `-` → один; обрізати `-`; до 80 символів по межі слова.
  - `SlugAllocator.allocate(String name, Predicate<String> taken): String` → `base`, `base-2`, `base-3`, …
  - `ArtistRepository extends MongoRepository<Artist, String>`: `Optional<Artist> findBySlugAndStatus(String, ArtistStatus)`, `Optional<Artist> findByWikidataId(String)`, `Optional<Artist> findByLegacyV1Id(String)`, `boolean existsBySlug(String)`, `List<Artist> findByStatusAndNameNormalized(ArtistStatus, String)`.
  - `public record ArtistChangedEvent(String artistId)` — публікується після кожного створення, зміни статусу, зміни `origin`/`pin`/імені/фото (не після лайків).
  - `MongoIndexes` (`ApplicationRunner`): індекси `02-contracts.md` розділ 3 для `artists`, `places`, `submissions` (`status_1_createdAt_-1`), `jobs_runs` (`job_1_startedAt_-1`). Унікальні (`slug_unique`, `wikidataId_unique_sparse`, `legacyV1Id_unique_sparse`) — помилка зупиняє старт; решта — попередження в лог (як V1).

- [ ] **Step 1: Вектори і тести**

`names.json` (≥ 12 рядків), наприклад:
```json
[{"input":"«Океан Ельзи»","normalized":"океан ельзи","slug":"okean-elzy"},
 {"input":"Соломія Крушельницька","normalized":"соломія крушельницька","slug":"solomiia-krushelnytska"},
 {"input":"Кам’янець","normalized":"камянець","slug":"kamianets"},
 {"input":"Ї́жак  Згорани","normalized":"їжак згорани","slug":"yizhak-zghorany"},
 {"input":"ДахаБраха","normalized":"дахабраха","slug":"dakhabrakha"},
 {"input":"The Hardkiss","normalized":"the hardkiss","slug":"the-hardkiss"},
 {"input":"Юрій","normalized":"юрій","slug":"yurii"},
 {"input":"Щедрик","normalized":"щедрик","slug":"shchedryk"}]
```
```java
@ParameterizedTest @MethodSource("namesJson") void normalizeMatchesVector(String in, String expected)
@ParameterizedTest @MethodSource("namesJson") void slugMatchesVector(String in, String expected)
@Test void allocateAddsNumericSuffix()      // taken {"okean-elzy","okean-elzy-2"} → "okean-elzy-3"
@Test void slugOfOnlySymbolsFallsBackToArtist() // "!!!" → "artist"
@ParameterizedTest @MethodSource("linksJson") void classifyMatchesVector(String url, LinkPlatform expected) // contracts/test-vectors/links.json
@Test void groupsFollowContract()           // SPOTIFY→MUSIC, YOUTUBE→VIDEO, WIKIPEDIA→REFERENCE, INSTAGRAM→SOCIAL
// MongoIndexesIT
@Test void createsAllIndexes()              // listIndexes artists містить slug_unique, wikidataId_unique_sparse, pin_2dsphere, status_1_likesCount_-1 …
@Test void duplicateSlugIsRejected()        // два save з однаковим slug → DuplicateKeyException
```
- [ ] **Step 2: Запустити — падає.** `./gradlew test --tests 'com.nashi.artist.*' --tests 'com.nashi.config.MongoIndexesIT'` → FAIL.
- [ ] **Step 3: Реалізувати** класи з блоку Produces.
- [ ] **Step 4: Запустити — PASS.**
- [ ] **Step 5: Commit** `feat(artist): add artist document model, name normalization, KMU-2010 slugs and indexes`

### Task 8: `PinPlacer` — детермінований пін за формулою

**Files:**
- Create: `com/nashi/geo/{PinPlacer.java,PlaceRef.java}`
- Create: `contracts/scripts/pin-vectors.mjs`, `contracts/test-vectors/pins.json`
- Test: `geo/PinPlacerTest.java`

**Interfaces:**
- Consumes: T6 `PlaceType`, T7 `PinPrecision`.
- Produces:
  - `public record PlaceRef(String placeId, double lat, double lon, PlaceType type, Double areaKm2)`
  - `public static double PinPlacer.radiusMeters(PlaceType type, Double areaKm2)`
  - `public static GeoJsonPoint PinPlacer.pinFor(String artistId, PlaceRef place, PinPrecision precision)` (`GeoJsonPoint(x = lon, y = lat)`)

Алгоритм (`02-contracts.md` розділ 4, тут дослівно, бо тести його не визначають повністю):
```java
static GeoJsonPoint pinFor(String artistId, PlaceRef p, PinPrecision precision) {
    if (precision == PinPrecision.EXACT) return new GeoJsonPoint(p.lon(), p.lat());
    double R = radiusMeters(p.type(), p.areaKm2());
    byte[] h = sha256((artistId + ":" + p.placeId()).getBytes(UTF_8));
    double u1 = Integer.toUnsignedLong(ByteBuffer.wrap(h, 0, 4).getInt()) / 4294967296.0;
    double u2 = Integer.toUnsignedLong(ByteBuffer.wrap(h, 4, 4).getInt()) / 4294967296.0;
    double r = R * Math.sqrt(u1), theta = 2 * Math.PI * u2;
    double lat = p.lat() + (r * Math.cos(theta)) / 111320.0;
    double lon = p.lon() + (r * Math.sin(theta)) / (111320.0 * Math.cos(Math.toRadians(p.lat())));
    return new GeoJsonPoint(lon, lat);
}
// radiusMeters: areaKm2 != null → clamp(0.6 * sqrt(areaKm2 / PI) * 1000, 150, 8000);
//               інакше CITY 3000, TOWN 1200, VILLAGE 400, OTHER 400
```

- [ ] **Step 1: Еталонна JS-реалізація і вектори.** `contracts/scripts/pin-vectors.mjs` реалізує ту саму формулу (Node `crypto.createHash('sha256')`, `readUInt32BE`) і пише `pins.json` — 8 векторів `{artistId, place:{placeId,lat,lon,type,areaKm2}, precision, expected:{lat,lon}}`: Київ (`Q1899`, 50.45, 30.5236, CITY, 839) × 3 різні `artistId`; Броди (TOWN, без площі); село (VILLAGE, 2.1); `OTHER` без площі; `EXACT`; мала площа (0.01 → радіус 150). Run: `node contracts/scripts/pin-vectors.mjs` → файл створено.
- [ ] **Step 2: Тести**
```java
@ParameterizedTest @MethodSource("pinsJson") void matchesJsReferenceVectors(...)  // |Δlat|,|Δlon| < 1e-9
@Test void exactReturnsPlaceCoordinates()
@Test void deterministicAcrossCalls()
@Test void staysWithinRadius()            // 10 000 випадкових artistId для Києва: haversine(pin, центр) ≤ 8000 + 1
@Test void radiusRules()                  // (CITY,null)=3000; (TOWN,null)=1200; (VILLAGE,null)=400; (CITY,839)=8000; (VILLAGE,0.01)=150; (CITY,50)=2393.65±0.01
@Test void spreadIsUniformByArea()        // 10 000 пінів: частка в колі радіусом R/2 = 0.25 ± 0.02
@Test void differentArtistsSamePlaceDiffer()
```
- [ ] **Step 3: Запустити — падає; реалізувати; запустити — PASS.** Run: `./gradlew test --tests 'com.nashi.geo.PinPlacerTest'`.
- [ ] **Step 4: Commit** `feat(geo): place pins deterministically inside settlement radius with JS parity vectors`

### Task 9: Медіа — сховище S3/MinIO і обробка фото

**Files:**
- Create: `com/nashi/media/{FileStorage,S3FileStorage,S3Properties,ImageProcessor,ImageFormat,ProcessedImage}.java`
- Test: `media/ImageProcessorTest.java` (фікстури `src/test/resources/images/`: `rotated-gps.jpg` — EXIF Orientation=6 і GPS, 3024×4032; `photo.png`; `photo.webp`; `fake.jpg` — PDF з розширенням .jpg; `tiny.jpg` 300×200), `media/S3FileStorageIT.java`

**Interfaces:**
- Consumes: T2 `UnsupportedMediaException`, `MongoIT` (MinIO-контейнер).
- Produces:
  - `public interface FileStorage { void put(String key, byte[] bytes, String contentType); byte[] get(String key); void copy(String fromKey, String toKey); void delete(String key); String publicUrl(String key); }`
  - `@ConfigurationProperties("nashi.s3") public record S3Properties(String endpoint, String region, String bucket, String accessKeyId, String secretAccessKey, String publicBaseUrl)` — `endpoint` порожній = AWS; `publicBaseUrl` — CDN/публічна адреса бакета (`publicUrl(key) = publicBaseUrl + "/" + key`).
  - `public enum ImageFormat { JPEG, PNG, WEBP; static Optional<ImageFormat> sniff(byte[] head) }` — за magic bytes: `FF D8 FF`; `89 50 4E 47 0D 0A 1A 0A`; `RIFF????WEBP`.
  - `public record ProcessedImage(byte[] full, byte[] thumb, int width, int height)`
  - `ImageProcessor.process(byte[] input, String field): ProcessedImage` — кидає `UnsupportedMediaException(field)` для невідомого формату або нерозбірного файла; застосовує EXIF Orientation (metadata-extractor) і перекодовує в JPEG якість 0.85: `full` — довша сторона ≤ 1600, `thumb` — квадрат 400×400 (обрізка по центру); менші за ліміт зображення не збільшуються. Перекодування з `BufferedImage` гарантує відсутність EXIF/XMP/GPS.

- [ ] **Step 1: Тести**
```java
@Test void sniffsFormatsByMagicBytes()
@Test void rejectsRenamedPdf()               // fake.jpg → UnsupportedMediaException, field "images[2]"
@Test void appliesOrientationAndStripsExif() // rotated-gps.jpg → width 1200, height 1600 (повернуто), Metadata без GPS-директорії і без ExifIFD0 Orientation
@Test void doesNotUpscaleSmallImages()       // tiny.jpg → 300×200
@Test void thumbIs400Square()
@Test void decodesPngAndWebp()
// S3FileStorageIT (MinIO)
@Test void putGetCopyDelete()
@Test void publicUrlUsesBaseUrl()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `./gradlew test --tests 'com.nashi.media.*'`.
- [ ] **Step 3: Commit** `feat(media): add S3 storage and image processing with orientation fix and EXIF stripping`

### Task 10: Публічне API музикантів — `/musicians`, `/musicians/{slug}`

**Files:**
- Create: `com/nashi/musician/{MusicianController,MusicianQueryService,MusicianListQuery,MusicianSort,MusicianListItem,MusicianPage,MusicianDetails,MusicianMapper}.java`
- Test: `musician/MusicianMapperTest.java`, `musician/MusicianControllerIT.java`

**Interfaces:**
- Consumes: T7 `Artist`, `ArtistRepository`, `Names`, `LinkPlatform.group()`; T9 `FileStorage.publicUrl`; T5 `Region.uaName()`.
- Produces:
  - `public record MusicianListQuery(List<Region> regions, String q, MusicianSort sort, int page, int size)`; `public enum MusicianSort { NAME, LIKES, RECENT }`
  - `MusicianQueryService.list(MusicianListQuery): MusicianPage`; `MusicianQueryService.details(String slug): MusicianDetails` (кидає `NotFoundException("musician")`)
  - DTO-record-и з іменами схем OpenAPI: `MusicianListItem`, `MusicianPage(List<MusicianListItem> items, int page, int size, long totalItems, int totalPages)`, `MusicianDetails` з вкладеними `TextSource`, `Image`, `ImageCredit`, `OriginDto` (у JSON — `origin`), `LifeDates`, `Materials(List<ClipSummary> clips, List<Work> works, List<Video> videos)`, `LinkDto(platform, group, url)`.
  - `MusicianMapper.toDetails(Artist): MusicianDetails`, `toListItem(Artist): MusicianListItem` — `thumbUrl` = перший `ImageAsset`: `thumbKey` → `publicUrl`, інакше `thumbUrl`; `origin.lat/lon` = `pin` (контракт 2.6); `hasAudio = !audio.isEmpty()`; `materials.clips` — без `url` (лише `clipId,title,source,durationSec,externalUrl`); посилання сортуються за групою `MUSIC, VIDEO, REFERENCE, SOCIAL`, у групі — як збережено.

- [ ] **Step 1: Тести**
```java
// MusicianMapperTest
@Test void thumbPrefersStoredKeyOverExternalUrl()
@Test void originUsesPinCoordinates()
@Test void linksSortedByGroupThenStored()
@Test void clipsHaveNoPlaybackUrl()
@Test void textSourceNullForSubmission()
// MusicianControllerIT (MongoIT; 5 музикантів: 3 PUBLISHED (LVIV×2, KYIV_CITY), 1 HIDDEN, 1 PUBLISHED без pin)
@Test void listsOnlyPublished()               // totalItems == 4
@Test void filtersByRepeatedRegion()          // ?region=LVIV&region=KYIV_CITY → 3
@Test void searchIgnoresCaseApostropheStress()// q="камянець" знаходить «Кам’янець-бенд»
@Test void sortsByLikesDesc()
@Test void sizeAbove100Is400()
@Test void detailsBySlug()                    // 200, $.origin.region == "LVIV", $.links[0].group == "MUSIC"
@Test void hiddenIs404Problem()               // 404, $.code == "NOT_FOUND"
@Test void musicianWithoutPinHasPage()        // 200, $.origin == null
```
- [ ] **Step 2: Запустити — падає; реалізувати** (`list` — `MongoTemplate` з `Criteria.where("status").is(PUBLISHED)`, `origin.region in`, `nameNormalized` regex `Pattern.quote(Names.normalize(q))`; сортування `NAME` → `nameNormalized` asc з колацією `uk`; `LIKES` → `likesCount` desc; `RECENT` → `publishedAt` desc); **PASS**.
- [ ] **Step 3: Commit** `feat(musician): add list and details endpoints for published musicians`

### Task 11: Карта, фільтри і публічний конфіг — `/map/*`, `/filters`, `/config/public`

**Files:**
- Create: `com/nashi/map/{MapController,MapService,MapSnapshot,MapPin,MapPinsResponse,RegionStat,PlaceStat}.java`
- Create: `com/nashi/filter/{FilterController,FilterService,FilterDefinition,FilterOption,FilterType}.java`
- Create: `com/nashi/config/{PublicConfigController,PublicConfig}.java`
- Test: `map/MapServiceIT.java`, `map/MapControllerIT.java`, `filter/FilterServiceTest.java`, `config/PublicConfigControllerIT.java`

**Interfaces:**
- Consumes: T5 `RegionCatalog`, T6 `PlaceStore`, T7 `Artist`, `ArtistChangedEvent`, T9 `FileStorage.publicUrl`, T2 `NashiProperties`.
- Produces:
  - `public record MapSnapshot(String version, List<MapPin> pins, List<RegionStat> regions, List<PlaceStat> places)`
  - `MapService.snapshot(): MapSnapshot` — кешується (Caffeine, 1 запис), перебудовується ліниво після `@EventListener onArtistChanged(ArtistChangedEvent)`; `version` = перші 8 hex SHA-256 від рядка `id:updatedAt` усіх опублікованих музикантів з піном, відсортованих за `id`.
  - `MapController`: `GET /api/v2/map/pins` → `MapPinsResponse(version, pins)`; `/map/regions` → 27 `RegionStat` (з `RegionCatalog`, `count` = кількість пінів з `origin.region`); `/map/places` → `PlaceStat` для кожного `origin.placeId` з `count ≥ 1` (координати і тип — з `PlaceStore`, назва — `origin.placeName`). Усі три — `ETag: "<version>"`, `Cache-Control: public, max-age=60`, `If-None-Match` → 304.
  - `FilterService.definitions(): List<FilterDefinition>` — у фазі 1 один елемент `{key:"region", label:"Область", type: MULTI_SELECT, options: 27 × {value, label: uaName, count}}`; опції з `count = 0` присутні (UI вирішує, як показати).
  - `PublicConfigController`: `GET /api/v2/config/public` → `PublicConfig` з `NashiProperties` (`turnstileSiteKey` = `null`, поки вимкнено).

- [ ] **Step 1: Тести**
```java
// MapServiceIT
@Test void pinsContainOnlyPublishedWithPin()
@Test void artistWithoutPinIsNotOnMapButHasPage()   // Review Focus 5
@Test void regionsAlways27WithCounts()              // 2 LVIV, 1 KYIV_CITY, решта 0
@Test void placesAggregateByPlaceId()               // два музиканти з Q36036 → один PlaceStat count 2, lat/lon = центр пункту, не пін
@Test void versionChangesAfterArtistChangedEvent()
@Test void versionStableAfterLikes()                // $inc likesCount не змінює version
// MapControllerIT
@Test void pinsReturnEtagAndCacheControl()
@Test void ifNoneMatchGives304()
@Test void pinJsonHasExactlyContractFields()        // ключі першого піна == 10 полів 2.1; thumbUrl відсутній, якщо фото немає
// FilterServiceTest
@Test void regionFilterHas27OptionsWithCounts()
// PublicConfigControllerIT
@Test void returnsDefaultsFromProperties()          // fragmentSeconds 15, map.regionMaxZoom 7, likes.batchMax 50
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** `MapPin` серіалізується з `@JsonInclude(NON_NULL)` (як V1 `MapPinDTO` — менший payload). `/map/pins` додається до `server.compression.mime-types`.
- [ ] **Step 3: Commit** `feat(map): add cached pins, region and place aggregates with ETag; filters and public config`

### Task 12: Лайки і обмеження частоти запитів

**Files:**
- Create: `com/nashi/ratelimit/{RateLimiter,RateLimitPolicy,ClientIp}.java`
- Create: `com/nashi/like/{LikeController,LikeService,LikeRequest,LikeResponse}.java`
- Test: `ratelimit/RateLimiterTest.java`, `like/LikeServiceIT.java`, `like/LikeControllerIT.java`

**Interfaces:**
- Consumes: T7 `Artist`, `ArtistStatus`; T2 `RateLimitedException`, `NotFoundException`, `ValidationFailedException`.
- Produces:
  - `public enum RateLimitPolicy { LIKES(20, Duration.ofSeconds(10)), SUBMISSIONS(5, Duration.ofHours(1)) }`
  - `RateLimiter.check(RateLimitPolicy policy, String key)` — кидає `RateLimitedException(secondsToRefill)`; бакети Bucket4j у Caffeine (`expireAfterAccess` = вікно політики, максимум 100 000 ключів); `Clock`-інʼєкція для тестів.
  - `ClientIp.of(HttpServletRequest): String` — `request.getRemoteAddr()` (після `ForwardedHeaderFilter`, увімкненого `server.forward-headers-strategy=framework`; backend слухає лише внутрішню мережу Docker, тож `X-Forwarded-For` ставить тільки наш nginx).
  - `public record LikeRequest(@NotNull @Min(1) @Max(50) Integer count)`; `public record LikeResponse(long likesCount)`
  - `LikeService.add(String artistId, int count): long` — `mongoTemplate.findAndModify(query(where("_id").is(id).and("status").is(PUBLISHED)), new Update().inc("likesCount", count), options().returnNew(true), Artist.class)`; `null` → `NotFoundException("musician")`. Не оновлює `updatedAt`, не публікує `ArtistChangedEvent`.
  - `POST /api/v2/musicians/{id}/likes` → `200 LikeResponse`.

- [ ] **Step 1: Тести**
```java
// RateLimiterTest (фіксований Clock)
@Test void allows20LikesRequestsPer10Seconds()     // 20 × check ok, 21-й → RateLimitedException retryAfter ∈ [1,10]
@Test void refillsAfterWindow()
@Test void keysAreIndependent()
// LikeServiceIT
@Test void incrementsAtomicallyUnderConcurrency()  // 100 потоків × add(id, 1) → likesCount == 100
@Test void addsBatchCount()                        // add(id, 37) → +37
@Test void hiddenArtistIs404()
// LikeControllerIT
@Test void zeroOrFiftyOneIs400()                   // count 0 і 51 → 400 VALIDATION_FAILED, field "count"
@Test void twentyFirstRequestIs429WithRetryAfter()
@Test void responseHasNewTotal()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(like): add unlimited atomic likes with per-IP request rate limit`

### Task 13: Контракт-тест реалізації проти `contracts/openapi.yaml`

**Files:**
- Create: `backend/src/test/java/com/nashi/ContractConformanceIT.java`
- Modify: `backend/build.gradle` (`tasks.test.inputs.file('../contracts/openapi.yaml')`, як V1 робив для `swagger.json`)

**Interfaces:**
- Consumes: T10–T12 контролери; T1 контракт. Задачі T14 і T21 додають свої шляхи — тест перевіряє лише вже реалізовані (список `IMPLEMENTED_OPERATIONS` у тесті доповнюється в T14 і T21).
- Produces: тест, що падає при розходженні коду і контракту.

- [ ] **Step 1: Тест.** Піднімає застосунок (`MongoIT`), читає `/v3/api-docs` (springdoc) і `contracts/openapi.yaml` (`io.swagger.parser.v3:swagger-parser` як test-залежність), порівнює для кожної операції з `IMPLEMENTED_OPERATIONS`: шлях і метод; імена і `required` параметрів; коди відповідей; для схем `MapPin`, `RegionStat`, `PlaceStat`, `FilterDefinition`, `MusicianListItem`, `MusicianPage`, `MusicianDetails`, `LikeRequest`, `LikeResponse`, `PlaceSuggestion`, `PublicConfig` — множину імен властивостей і значення перелічень.
```java
@Test void implementedOperationsMatchContract()
@Test void enumsMatchContract()          // Region (27), MusicianKind, LinkPlatform, …
```
- [ ] **Step 2: Запустити** `./gradlew test --tests 'com.nashi.ContractConformanceIT'` → якщо FAIL — виправити **код** (контракт міняється лише через `02-contracts.md` + `openapi.yaml` разом). → PASS.
- [ ] **Step 3: Commit** `test(api): verify implemented endpoints against contracts/openapi.yaml`

---
## Етап 2. Заявки і модерація

### Task 14: Прийом заявок — `POST /api/v2/submissions`

**Files:**
- Create: `com/nashi/submission/{Submission,SubmissionStatus,SubmissionRepository,SubmissionRequest,SubmissionAccepted,SubmissionController,SubmissionValidator,SubmissionService,DuplicateDetector,IpHasher,SubmissionCreatedEvent,ModerationEntry,ModerationAction,Moderator,TelegramRefs}.java`
- Test: `submission/SubmissionValidatorTest.java`, `submission/IpHasherTest.java`, `submission/DuplicateDetectorIT.java`, `submission/SubmissionControllerIT.java`
- Modify: `ContractConformanceIT.IMPLEMENTED_OPERATIONS` (+ `createSubmission`)

**Interfaces:**
- Consumes: T6 `PlaceStore.find`, T7 `Names`, `LinkClassifier`, `ArtistRepository`, T9 `ImageProcessor`, `FileStorage`, T12 `RateLimiter`, `RateLimitPolicy.SUBMISSIONS`, `ClientIp`.
- Produces:
  - `public record SubmissionRequest(String name, MusicianKind kind, String originPlaceId, String birthPlaceId, String summary, List<String> genres, List<String> links, String youtubeChannelUrl, String wikipediaUrl, String contact, Boolean consent, String faxNumber)` — Jakarta-анотації для простих правил `02-contracts.md` 2.10.
  - `SubmissionValidator.validate(SubmissionRequest r, List<MultipartFile> images): List<FieldViolation>` — крос-польові правила: `originPlaceId`/`birthPlaceId` існують у `places`; `birthPlaceId` лише для `PERSON`; ≥ 1 посилання групи `MUSIC`/`VIDEO` серед `links` + `youtubeChannelUrl`; домени `youtubeChannelUrl` і `wikipediaUrl`; лише `http(s)`; 1..5 фото (поле `images`), кожне ≤ 10 МБ (поле `images[i]`); повідомлення — українською.
  - `@Document("submissions") public class Submission` — поля `02-contracts.md` розділ 3 `submissions`.
  - `public enum SubmissionStatus { PENDING, APPROVED, REJECTED, DISCARDED }`; `public enum ModerationAction { SENT, APPROVED, REJECTED, DUPLICATE_WARNING, FAILED }`; `public record Moderator(long telegramUserId, String username, String displayName)`; `public record ModerationEntry(ModerationAction action, Moderator by, Instant at, String note)`; `public record TelegramRefs(long chatId, Integer threadId, List<Integer> albumMessageIds, Integer controlMessageId)`.
  - `IpHasher.hash(String ip): String` — HMAC-SHA-256 (`nashi.security.ip-hash-secret`), hex.
  - `DuplicateDetector.findDuplicate(String name): Optional<Artist>` — опублікований з тим самим `nameNormalized`.
  - `SubmissionService.submit(SubmissionRequest r, List<MultipartFile> images, String clientIp): SubmissionAccepted` — порядок: rate limit → пастка (`faxNumber` не порожній → зберегти `DISCARDED` без фото, повернути `202` з тим самим виглядом) → валідація → обробка фото (`ImageProcessor`) → `FileStorage.put("submissions/{id}/{n}.jpg")` і `…/{n}-thumb.jpg` → збереження `PENDING` з `duplicateOfArtistId` → `ApplicationEventPublisher.publishEvent(new SubmissionCreatedEvent(id))`.
  - `POST /api/v2/submissions` (`consumes = multipart/form-data`, частини `data` і `images`) → `202 SubmissionAccepted(id, "PENDING")`.

- [ ] **Step 1: Тести**
```java
// SubmissionValidatorTest (PlaceStore — мок: існують Q36036, Q465104)
@Test void validRequestHasNoViolations()
@Test void summaryShorterThan30()                     // field "summary", message "Мінімум 30 символів"
@Test void needsMusicOrVideoLink()                    // links = [instagram] і без youtubeChannelUrl → field "links"
@Test void youtubeChannelCountsAsVideoLink()
@Test void birthPlaceOnlyForPerson()                  // BAND + birthPlaceId → field "birthPlaceId"
@Test void unknownPlaceId()                           // "Q0" → field "originPlaceId"
@Test void wikipediaMustBeUkOrEn()                    // ru.wikipedia.org → field "wikipediaUrl"
@Test void rejectsJavascriptUrl()                     // "javascript:alert(1)" → field "links[0]"
@Test void rejectsSixthImage()                        // 6 файлів → field "images"
@Test void consentRequired()
// IpHasherTest
@Test void sameIpSameHashDifferentSecretDifferentHash()
@Test void hashNeverContainsIp()
// SubmissionControllerIT (MongoIT + MinIO)
@Test void acceptsValidSubmissionAndStoresProcessedImages() // 202; у MinIO є submissions/{id}/0.jpg і 0-thumb.jpg; документ PENDING; подія опублікована
@Test void rejectsSixthImage()                        // Review Focus 4: 400, errors[0].field == "images"
@Test void renamedPdfIs415()                          // field "images[0]"
@Test void honeypotIsSilentlyDiscarded()              // faxNumber="x" → 202, status DISCARDED, подія НЕ опублікована, фото не завантажені
@Test void sixthSubmissionPerHourIs429()
@Test void duplicateNameIsFlagged()                   // опублікований «Океан Ельзи» + заявка «"океан  ельзи"» → duplicateOfArtistId заповнено
@Test void storesIpHashNotIp()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `./gradlew test --tests 'com.nashi.submission.*' --tests 'com.nashi.ContractConformanceIT'`.
- [ ] **Step 3: Commit** `feat(submission): accept musician suggestions with validation, antispam and image processing`

### Task 15: Telegram-інфраструктура — шлюз Bot API, маршрутизатор, політика модераторів

**Files:**
- Create: `com/nashi/telegram/{TelegramProperties,ApproverPolicy,TelegramGateway,BotApiTelegramGateway,TelegramRetry,TelegramUpdateRouter,TelegramBotRegistrar,ChatAdmins,CallbackData,Verdict,Html}.java`
- Test: `telegram/CallbackDataTest.java`, `telegram/TelegramUpdateRouterTest.java`, `telegram/ChatAdminsTest.java`, `telegram/TelegramRetryTest.java`, `telegram/HtmlTest.java`, `telegram/BotApiTelegramGatewayTest.java`

**Interfaces:**
- Consumes: V1 `bot/AbstractBot.java` (логіку повторів на 429 з `retry_after` і експоненційний backoff перенести в `TelegramRetry`, узагальнивши на будь-який `BotApiMethod<T>`), V1 `bot/TelegramBot.isAdminChat` (ідея фільтра чату).
- Produces:
  - `@ConfigurationProperties("nashi.telegram") public record TelegramProperties(String botToken, boolean pollingEnabled, boolean sendingEnabled, Moderation moderation)`; `record Moderation(long chatId, Integer threadId, ApproverPolicy approverPolicy)`; `public enum ApproverPolicy { CHAT_ADMINS, ANY_MEMBER }`.
  - `public interface TelegramGateway { List<Integer> sendAlbum(List<String> photoUrls); int sendControl(String html, List<List<InlineButton>> keyboard, Integer replyTo); void editControl(int messageId, String html); void answerCallback(String callbackQueryId, String text); void sendText(String html); Set<Long> chatAdminIds(); }`; `public record InlineButton(String text, String callbackData)`. Усі методи шлють у `moderation.chatId` (+ `message_thread_id`, якщо задано); `parse_mode = HTML`; при `sendingEnabled = false` — лог і повернення порожніх ID (`sendControl` → `-1`).
  - `BotApiTelegramGateway(TelegramClient client, TelegramProperties props, TelegramRetry retry)`; `sendAlbum` з 1 фото → `SendPhoto`, 2..5 → `SendMediaGroup`; `editControl` → `EditMessageText` без `reply_markup` (кнопки зникають).
  - `TelegramRetry.execute(Supplier<T> call): T` — 429 → чекати `retry_after` с; 5xx/мережа → backoff 1, 2, 4, 8 с; ≤ 5 спроб; 4xx (крім 429) — одразу виняток.
  - `public enum Verdict { YES, NO }`; `public record CallbackData(String submissionId, Verdict verdict) { public String encode(); public static Optional<CallbackData> parse(String raw); }` — формат `sub:<24 hex>:y|n` (≤ 64 байт обмеження Telegram).
  - `ChatAdmins.contains(long userId): boolean` — кеш `getChatAdministrators` на 5 хв (Caffeine), при помилці API — останнє відоме значення, а якщо його немає — `false`.
  - `TelegramUpdateRouter implements LongPollingSingleThreadUpdateConsumer` — `consume(Update)`: оновлення не з `moderation.chatId` → лог `warn` і ігнор (без відповіді); `callback_query` → `ModerationCallbackHandler.handle(CallbackQuery)` (T16); текст, що починається з `/` → `AdminCommandHandler.handle(Message)` (T17); інше — ігнор (V1 відповідав «alive» на будь-що — прибрано, є `/ping`).
  - `TelegramBotRegistrar` (`SmartLifecycle`): якщо `pollingEnabled` — створює `TelegramBotsLongPollingApplication` і реєструє маршрутизатор з токеном; інакше лог «Telegram polling off».
  - `Html.escape(String): String` — `&`, `<`, `>`, `"`.

- [ ] **Step 1: Тести**
```java
@Test void callbackEncodeParseRoundTrip()          // ("6650f0c2a1b2c3d4e5f60718", YES) → "sub:6650f0c2a1b2c3d4e5f60718:y"
@Test void callbackRejectsForeignFormats()         // "ARTIST:APPROVE:1" (формат V1), "sub:xyz:y", "sub:<24hex>:maybe" → empty
@Test void callbackFitsTelegramLimit()             // encode().getBytes(UTF_8).length ≤ 64
@Test void routerIgnoresOtherChats()               // update з chatId ≠ moderation → жоден обробник не викликано
@Test void routerDispatchesCallbacksAndCommands()
@Test void chatAdminsCachedForFiveMinutes()        // 2 виклики за 4 хв → 1 запит до gateway; через 6 хв — 2
@Test void chatAdminsFallsBackToLastKnownOnError()
@Test void retryHonoursRetryAfter()                // 429 retry_after=3 → sleeper отримав 3000 мс, друга спроба успішна
@Test void retryStopsOn400()
@Test void htmlEscapes()                           // "<b>&" → "&lt;b&gt;&amp;"
@Test void gatewayIsNoOpWhenSendingDisabled()      // TelegramClient не викликано
@Test void gatewayAddsThreadIdWhenConfigured()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `./gradlew test --tests 'com.nashi.telegram.*'`.
- [ ] **Step 3: Commit** `feat(telegram): add bot gateway with retries, chat filter and moderator policy`

### Task 16: Модерація — картка заявки, «✅ Так» / «❌ Ні», створення музиканта

**Files:**
- Create: `com/nashi/moderation/{SubmissionCardFormatter,SubmissionNotifier,NotifyRetryJob,ModerationService,DecisionResult,AlreadyDecidedException,ArtistFromSubmission,ModerationCallbackHandler,EnrichmentRequested,AudioResolveRequested}.java`
- Test: `moderation/SubmissionCardFormatterTest.java`, `moderation/ArtistFromSubmissionTest.java`, `moderation/ModerationServiceIT.java`, `moderation/ModerationCallbackHandlerTest.java`, `moderation/SubmissionNotifierTest.java`

**Interfaces:**
- Consumes: T14 `Submission`, `SubmissionCreatedEvent`, `SubmissionRepository`; T15 `TelegramGateway`, `CallbackData`, `ChatAdmins`, `TelegramProperties`, `Html`; T6 `PlaceStore`; T7 `SlugAllocator`, `ArtistRepository`, `ArtistChangedEvent`, `LinkClassifier`; T8 `PinPlacer`; T9 `FileStorage`.
- Produces:
  - `SubmissionCardFormatter.format(Submission s, Place origin, @Nullable Place birthPlace, @Nullable Artist duplicate, String siteBaseUrl): String` — HTML ≤ 4096 символів (опис обрізається `MediaWikiParser.truncate`-подібно до 1500), шаблон:
    ```
    🆕 <b>Нова заявка</b> · {Гурт|Музикант}
    <b>{name}</b>
    📍 Походження: {origin.name}, {regionName}[ · народження: {birthPlace.name}]
    🎵 Жанри: {genres через кому | —}
    📝 {summary}
    🔗 {кожне посилання з платформою: «Spotify: url»}
    ▶️ YouTube-канал: {url} · 📖 Вікіпедія: {url}
    👤 Контакт: {contact | не вказано}
    [⚠️ Можливий дублікат: <a href="{siteBaseUrl}/m/{slug}">{name}</a>]
    ```
  - `SubmissionNotifier.onCreated(SubmissionCreatedEvent)` (`@Async("telegramExecutor") @EventListener`): альбом фото (URL з `FileStorage.publicUrl` для `submissions/{id}/{n}.jpg`; бакет має публічне читання префіксів `submissions/` і `artists/` — Telegram завантажує фото за URL) → контрольне повідомлення з кнопками `[[«✅ Так» → sub:id:y, «❌ Ні» → sub:id:n]]` у відповідь на перше фото альбому → зберегти `TelegramRefs`, лог `SENT` (+ `DUPLICATE_WARNING`, якщо є дублікат). Помилка → лог `FAILED` з причиною.
  - `NotifyRetryJob` (`@Scheduled(fixedDelay = 10 хв)`): `PENDING` без `telegram.controlMessageId`, менше 5 записів `FAILED` → повторити `onCreated`.
  - `ModerationService.decide(String submissionId, Verdict verdict, Moderator by): DecisionResult` — атомарно: `findAndModify({_id, status: PENDING}, {$set: {status, decidedAt}, $push: {moderationLog: entry}}, returnNew)`; `null` → `AlreadyDecidedException(currentStatus)`. `YES` → `ArtistFromSubmission.create(submission)` → `artistRepository.save` → `submission.artistId` → події `ArtistChangedEvent`, `EnrichmentRequested(artistId, wikipediaUrl)` (якщо `wikipediaUrl` є), `AudioResolveRequested(artistId)`.
  - `public record DecisionResult(Verdict verdict, Submission submission, @Nullable Artist artist)`
  - `ArtistFromSubmission.create(Submission s): Artist` — `status PUBLISHED`, `source SUBMISSION`, `disciplines [MUSIC]`, `slug` через `SlugAllocator`, `origin`: `birthPlaceId` є → `{birthPlace, basis BIRTH, precision SETTLEMENT}`, інакше `{originPlace, basis = PERSON ? ORIGIN : FORMATION, precision CITY}`; `pin = PinPlacer.pinFor(artist.id, placeRef, precision)` (id генерується до збереження: `new ObjectId().toHexString()`); фото копіюються `submissions/{sid}/{n}.jpg` → `artists/{aid}/{n}.jpg` (+ thumb), `ImageAsset.alt = name`; `links` → `Link(LinkClassifier.classify(url), url)`; `summary` = текст заявника, `textSource = null`; `publishedAt = now`.
  - `ModerationCallbackHandler.handle(CallbackQuery q)`: `CallbackData.parse` → empty → `answerCallback(q.id, "Невідома кнопка")`; політика `CHAT_ADMINS` і користувач не адмін → `answerCallback(q.id, "Модерувати можуть лише адміністратори групи")`, нічого не змінюється; інакше `decide` → `editControl(messageId, card + "\n\n✅ Схвалено — @{username}, {dd.MM HH:mm Київ} · <a href=\"{site}/m/{slug}\">сторінка</a>")` або `"❌ Відхилено — @{username}, {час}"` → `answerCallback(q.id, "Готово")`; `AlreadyDecidedException` → `editControl` з підсумком за поточним станом (кнопки зникають) і `answerCallback(q.id, "Заявку вже оброблено (статус: {Схвалено|Відхилено})")`. **`answerCallbackQuery` викликається завжди** (у V1 це була заглушка — кнопка «крутилася» безкінечно).

- [ ] **Step 1: Тести**
```java
// SubmissionCardFormatterTest
@Test void escapesUserHtml()                     // name "<script>" → "&lt;script&gt;"
@Test void includesDuplicateWarningLink()
@Test void fitsTelegramLimit()                   // summary 1000 символів + 10 посилань → ≤ 4096
// ArtistFromSubmissionTest
@Test void personWithBirthPlaceGetsSettlementPrecision()
@Test void bandUsesFormationAndCityPrecision()
@Test void pinComputedWithFinalArtistId()        // pin == PinPlacer.pinFor(artist.getId(), …)
@Test void copiesImagesToArtistPrefix()
// ModerationServiceIT (MongoIT + MinIO)
@Test void approveCreatesPublishedArtistOnMap()  // після YES — /map/pins містить slug
@Test void rejectPublishesNothing()              // NO → status REJECTED, artists порожня
@Test void concurrentApprovalsCreateOneArtist()  // Review Focus 1: 2 потоки decide(YES) → 1 артист, 1 AlreadyDecidedException
@Test void decisionIsLoggedWithModerator()       // moderationLog останній: APPROVED, by.username "admin1"
// ModerationCallbackHandlerTest (моки)
@Test void nonAdminCannotModerate()              // CHAT_ADMINS, user не в chatAdminIds → answerCallback з текстом, decide не викликано
@Test void anyMemberPolicyAllowsEveryone()
@Test void alwaysAnswersCallback()               // навіть при винятку в decide
@Test void oldButtonEditsMessageAndExplains()    // AlreadyDecided(APPROVED) → editControl + "Заявку вже оброблено (статус: Схвалено)"
// SubmissionNotifierTest
@Test void sendsAlbumThenControlWithButtons()    // кнопки: "✅ Так" sub:{id}:y, "❌ Ні" sub:{id}:n; replyTo = перше фото
@Test void failureIsLoggedForRetry()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `./gradlew test --tests 'com.nashi.moderation.*'`.
- [ ] **Step 3: Commit** `feat(moderation): moderate suggestions in Telegram group with atomic yes/no and auto publish`

### Task 17: Команди адміністраторів у групі

**Files:**
- Create: `com/nashi/admin/{AdminCommand,AdminCommandHandler,StatsService}.java`, `com/nashi/artist/ArtistVisibilityService.java`
- Test: `admin/AdminCommandTest.java`, `admin/AdminCommandHandlerTest.java`, `artist/ArtistVisibilityServiceIT.java`

**Interfaces:**
- Consumes: V1 `bot/AdminCommand.java` і `service/impl/artist/ArtistVisibilityService.java` (перенести з тестами, змінивши статуси на `PUBLISHED`/`HIDDEN`); T15 `TelegramGateway`, `ChatAdmins`; T19 `MusicianImportService.startInBackground()`; T20 `AudioResolveJob.startInBackground()`.
- Produces:
  - `public record AdminCommand(Action action, String argument)`; `enum Action { HIDE, SHOW, STATS, WIKI_IMPORT, AUDIO_RESOLVE, PING }`; `static Optional<AdminCommand> parse(String text)` — приймає `/cmd` і `/cmd@bot_name`.
  - `ArtistVisibilityService.hide(String slugOrQid): String`, `show(String slugOrQid): String` — відповіді українською, як у V1 («Сховано: …», «Не знайдено: …», «Вже приховано: …»); публікує `ArtistChangedEvent`.
  - `StatsService.summary(): String` — «Музикантів на карті: N · без піна: N · з аудіо: N · заявок на модерації: N · лайків за весь час: N».
  - `AdminCommandHandler.handle(Message m)` — команди виконує лише адміністратор групи (`ChatAdmins`) незалежно від `approverPolicy`; `/ping` → «alive»; невідома команда → підказка зі списком команд.

- [ ] **Step 1: Тести**
```java
@Test void parsesAllCommandsWithAndWithoutBotSuffix()   // "/hide@nashi_bot okean-elzy" → HIDE, "okean-elzy"
@Test void hideRequiresArgument()                       // "/hide" → usage-текст
@Test void hideAndShowToggleStatusAndInvalidateMap()    // IT: PUBLISHED → HIDDEN → PUBLISHED; /map/pins version змінюється
@Test void hideByWikidataId()                           // "/hide Q2017541"
@Test void nonAdminCommandIgnoredSilently()
@Test void wikiImportAlreadyRunningAnswers()            // startInBackground() == false → "Імпорт уже йде"
@Test void statsFormat()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(admin): add hide/show/stats/import/audio commands for group admins`

### Task 18: Фонові задачі — журнал і очищення фото відхилених заявок

**Files:**
- Create: `com/nashi/jobs/{JobRun,JobKind,JobRunLog,SubmissionImageCleanupJob}.java`
- Test: `jobs/JobRunLogIT.java`, `jobs/SubmissionImageCleanupJobIT.java`

**Interfaces:**
- Consumes: T14 `Submission`, T9 `FileStorage`, T15 `TelegramGateway.sendText`.
- Produces:
  - `public enum JobKind { WIKI_IMPORT, AUDIO_RESOLVE, SUBMISSION_CLEANUP, V1_MIGRATION }`
  - `JobRunLog.start(JobKind kind): String runId`; `JobRunLog.finish(String runId, String status, Map<String, Object> report)`; `JobRunLog.lastRun(JobKind): Optional<JobRun>`.
  - `SubmissionImageCleanupJob.run(): int` (`@Scheduled(cron = "0 30 4 * * *", zone = "Europe/Kyiv")`) — заявки `REJECTED`/`DISCARDED` з `decidedAt` (для `DISCARDED` — `createdAt`) старше 30 днів і непорожніми `imageKeys` → видалити файли, `imageKeys = []`; повертає кількість заявок.
  - Кожна задача (тут і в T19–T22) при статусі `FAILED` шле `sendText("⚠️ Задача {kind} завершилася з помилкою: {коротка причина}")`.

- [ ] **Step 1: Тести**
```java
@Test void deletesImagesOfRejectedOlderThan30Days()   // 31 день → файли видалено, запис лишився, imageKeys []
@Test void keepsRecentRejectedAndAllApproved()
@Test void logsRunWithCount()                         // jobs_runs: SUBMISSION_CLEANUP, status OK, report.cleaned == 1
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(jobs): add job run log and cleanup of rejected submission images after 30 days`

---

## Етап 3. Вікіпедія, аудіо, міграція

### Task 19: Порт імпорту з Вікіпедії (лише музиканти) і `SummaryCleaner`

**Files:**
- Create (перенести з V1 `nashi-backend/src/main/java/com/nashi/wiki/`, разом з тестами і `src/test/resources/wiki/*`): `WikiHttp`, `PoliteWikiHttp`, `Sleeper`, `WikiFetchException`, `SparqlParser`, `WikidataEntityParser`, `WikiEntity`, `WikiDate`, `MediaWikiParser`, `MusicBrainzClient`, `MusicBrainzParser`, `MusicBrainzSource`, `WikiQueries`, `CandidateLister`, `ChunkSplitter`, `CandidateGroup`, `WikiCandidate`, `WikiCollector`, `WikiArtistAssembler`, `WikiArtistData`, `PlaceResolver`, `WikiPlace`, `UkraineRegions` (вже в T6), `StateHonours`, `OccupationCategories`, `WikiLinkTable`, `KnownArtists`, `NameNormalizer` (→ замінити на `artist.Names`), `ImportReport`, `AdminNotifier`, `WikiImportRequested`, `WikiImportScheduler`, `WikiDryRun`
- Do NOT port: `CommonsWorks`, `WorkImages`, `WorkSelector` гілки для картин і письмових творів (лишити лише альбоми), `LocalSeedRunner` (замінюється `nashi.wiki-import.on-startup` у `MusicianImportService`)
- Create: `com/nashi/wiki/{MusicianImportService,WikiMusicianMapper,SummaryCleaner,MongoMusicianStore}.java`, `com/nashi/moderation/WikipediaEnrichmentService.java`
- Test: перенесені тести V1 + `wiki/SummaryCleanerTest.java`, `wiki/WikiMusicianMapperTest.java`, `wiki/MusicianImportServiceTest.java`, `wiki/MongoMusicianStoreIT.java`, `moderation/WikipediaEnrichmentServiceTest.java`

**Interfaces:**
- Consumes: T6 `PlaceStore.ensure`, T7 `Artist`, `Names`, `SlugAllocator`, `ArtistRepository`, T8 `PinPlacer`, T15 `TelegramGateway.sendText`, T18 `JobRunLog`.
- Produces:
  - Зміни в перенесеному коді: `CandidateGroup { MUSIC, ENSEMBLE }` (без `VISUAL`, `WRITER`); `OccupationCategories` лише з `MUSICIAN_ROOTS`; `UkraineRegions` — 27 регіонів; `WikiLinkTable` + Apple Music `P2850` → `APPLE_MUSIC` (`https://music.apple.com/artist/{id}`), Deezer `P2722` → `DEEZER` (`https://www.deezer.com/artist/{id}`), Bandcamp `P3283` → `BANDCAMP`, YouTube `P2397` → поле `youtubeChannelUrl` (`https://www.youtube.com/channel/{id}`) **і** посилання `YOUTUBE`; платформи V1, яких немає в `LinkPlatform` V2 (`DISCOGS`, `MUSICBRAINZ`, `LASTFM`, `COMMONS`, `WIKIDATA`, `ESU`, …) → `OTHER`, крім `WIKI` → `WIKIPEDIA`; `WikiArtistData` + поле `String tagline` (Wikidata `descriptions.uk`) і `String placeQid`.
  - Правило походження (**відмінність від V1**, де брався перший з P19/P551/P937): людина — лише місце народження `P19` в Україні (`basis BIRTH`, `precision SETTLEMENT`); гурт — лише місце заснування `P740` (`basis FORMATION`, `precision SETTLEMENT`); інакше `origin = null`, `pin = null` (музикант зберігається, сторінка є, на карті немає). Причина: вимога — «місце походження», а не проживання/роботи (за dry-run V1 це 8 із 4349 записів).
  - `SummaryCleaner.summary(String intro): String` (≤ 300) і `SummaryCleaner.description(String intro): String` (≤ 2000) — тіло:
    ```java
    // 1) MediaWikiParser.cleanIntro (V1: прибирає наголос U+0301, склеєні ISO-дати "1978(19780106)", пробіл перед комою)
    // 2) у першому реченні видалити кожну збалансовану пару дужок, вміст якої містить рік \b(1[0-9]{3}|20[0-9]{2})\b
    //    або одне з: "нар.", "лат.", "англ.", "рос.", "пол.", "справжнє", "псевдонім"; вкладені дужки враховуються
    // 3) прибрати пробіли перед [,.;:)] і подвоєні пробіли
    // 4) summary = MediaWikiParser.truncate(text, 300); description = MediaWikiParser.truncate(text, 2000)
    ```
  - `WikiMusicianMapper.toArtist(WikiArtistData d, Place place, String slug, String artistId): Artist` — `source WIKIPEDIA`, `status PUBLISHED`, `kind = d.ensemble ? BAND : PERSON`, `tagline`, `summary`/`description` (SummaryCleaner), `textSource {WIKIPEDIA, title = wikiTitle, url = WikiLinks.articleUrl, license "CC BY-SA 4.0", licenseUrl "https://creativecommons.org/licenses/by-sa/4.0/deed.uk"}`, `images` — `[ImageAsset(url = 800px Commons, thumbUrl = 400px Commons, credit)]`, `dates {birth, death, activeFrom = foundationYear, activeTo = disbandYear}`, `genres` (до 5, українською), `works` (альбоми з V1 `works`, `type ALBUM`), `pin = PinPlacer.pinFor(artistId, PlaceRef(place), SETTLEMENT)`.
  - `MusicianImportService.runNow(): ImportReport`, `startInBackground(): boolean` (одна копія одночасно, як V1), `@EventListener onRequested(WikiImportRequested)`; `WikiImportScheduler` — `nashi.wiki-import.cron` = `0 0 4 1 * *` Europe/Kyiv, `nashi.wiki-import.enabled`; звіт в групу: знайдено / вже були / додано / без піна / відсіяно через зв'язок з РФ / помилки (як V1 `ImportReport`).
  - `MongoMusicianStore implements ArtistStore` (V1-інтерфейс, адаптований): `loadKnown()` (wikidataId, wikiTitle, `nameNormalized` усіх `SUBMISSION`/`V1_MIGRATION`), `slugExists`, `save`. Дублікати — як V1: за `wikidataId`, `wikiTitle`, нормалізованим ім'ям доданих через форму.
  - Збагачення одного музиканта: `WikipediaEnrichmentService.enrich(String artistId, String wikipediaUrl)` (`@Async("jobsExecutor") @EventListener(EnrichmentRequested)`) — назва статті з URL → `pageprops.wikibase_item` (MediaWiki API) → `WikiCollector.fetchDetails` для одного QID → заповнити `wikidataId`, `wikiTitle`, `tagline`, `summary`, `description`, `textSource`, `dates`, `genres`, `works`, додати відсутні `links`, додати фото Commons **після** фото заявника; `origin`/`pin` не змінюються (їх погодив адмін); якщо `wikidataId` уже належить іншому музиканту — нічого не змінювати, `sendText("⚠️ Вікіпедія …: цей запис уже є як /m/{slug}")`.

- [ ] **Step 1: Перенести код і тести V1 без змін поведінки; переконатися, що вони зелені.** Run: `./gradlew test --tests 'com.nashi.wiki.*'` → PASS (перенесені тести).
- [ ] **Step 2: Нові тести**
```java
// SummaryCleanerTest
@Test void removesBirthParenthesesAndGluedDates()
  // "Оксана Ярославівна Линів (лат. Oksana Lyniv, нар. 6 січня 1978(19780106), Броди, Львівська область, УРСР) — українська диригентка. Перша жінка …"
  // → summary починається з "Оксана Ярославівна Линів — українська диригентка."
@Test void keepsParenthesesWithoutDates()           // "«Океан Ельзи» (гурт) — …" не змінюється
@Test void handlesNestedParentheses()
@Test void summaryCutAtSentenceWithEllipsis()       // довжина ≤ 301 (з «…»)
// WikiMusicianMapperTest
@Test void personOriginIsBirthPlaceOnly()           // P19 поза Україною, P551 у Києві → origin null, pin null
@Test void bandOriginIsFormationPlace()
@Test void kyivMapsToKyivCity()                     // P19 = Q1899 → region KYIV_CITY
@Test void mapsNewLinkProperties()                  // P2850 → APPLE_MUSIC, P2722 → DEEZER, P2397 → youtubeChannelUrl + YOUTUBE
@Test void textSourceIsCcBySa()
// MusicianImportServiceTest (фейкові джерела, як V1 ArtistImportServiceTest)
@Test void importsOnlyMusicGroups()                 // кандидати VISUAL/WRITER не запитуються
@Test void secondRunAddsNothing()
@Test void skipsArtistAddedThroughFormWithSameName()
@Test void unknownPlaceIsEnsuredInPlaces()          // P19 = хутір, якого немає в сиді → PlaceStore.ensure викликано
@Test void excludesRussiaLinked()                   // фікстура V1 entities-awarded.json
// WikipediaEnrichmentServiceTest
@Test void enrichFillsWikiFieldsKeepsOriginAndUserPhotosFirst()
@Test void enrichSkipsWhenQidBelongsToAnotherArtist()   // sendText з посиланням на наявного, музикант не змінений
```
- [ ] **Step 3: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 4: Живий dry run.** Run: `./gradlew wikiDryRun -Plimit=50` → `build/wiki-dry-run.json`; переконатися у виводі: лише групи `MUSIC`, `ENSEMBLE`; частка з `place` ≥ 80 %; є записи з `tagline`.
- [ ] **Step 5: Commit** `feat(wiki): port Wikipedia import for musicians with summary cleaner and origin rules`

### Task 20: Аудіо — провайдери і резолвер

**Files:**
- Create: `com/nashi/audio/{AudioProvider,ExternalIds,HttpJson,DeezerAudioProvider,ItunesAudioProvider,CommonsAudioProvider,AudioDuration,AudioResolver,AudioResolveJob}.java`
- Test: `audio/ExternalIdsTest.java`, `audio/DeezerAudioProviderTest.java`, `audio/ItunesAudioProviderTest.java`, `audio/CommonsAudioProviderTest.java`, `audio/AudioResolverTest.java`, `audio/AudioResolveJobIT.java`; фікстури `src/test/resources/audio/{deezer-artist-top.json, itunes-lookup.json, commons-audio-imageinfo.json, wikidata-p51.json, sample.mp3}` (записані справжні відповіді; `sample.mp3` — 3 с тиші)

**Interfaces:**
- Consumes: T7 `Artist`, `AudioClip`, `Link`; T19 `PoliteWikiHttp` (для Commons/Вікіданих); T18 `JobRunLog`; T16 `AudioResolveRequested`.
- Produces:
  - `public interface AudioProvider { AudioSource source(); List<AudioClip> find(Artist artist); }`
  - `ExternalIds.deezerArtistId(List<Link>): Optional<String>` — `deezer.com/(?:[a-z]{2}/)?artist/(\d+)`; `ExternalIds.appleArtistId(List<Link>): Optional<String>` — `music.apple.com/(?:[a-z]{2}/)?artist/(?:[^/]+/)?(\d+)` або `itunes.apple.com/…/id(\d+)`.
  - `HttpJson.get(String url): JsonObject` — `java.net.http`, таймаут 10 с, User-Agent з Global Constraints, ≤ 1 запит/с на хост, повтор за `Retry-After` на 429/503.
  - `DeezerAudioProvider`: `GET https://api.deezer.com/artist/{id}/top?limit=5` → `AudioClip(clipId "dz_" + trackId, DEEZER, externalId trackId, key null, url null, title, 30, "Прев'ю: Deezer", track.link)`; треки без `preview` відкидаються. URL не зберігається (тимчасовий, див. T21).
  - `ItunesAudioProvider`: `GET https://itunes.apple.com/lookup?id={id}&entity=song&limit=5&country=UA` → для `wrapperType == "track"` з `previewUrl`: `AudioClip("it_" + trackId, ITUNES, trackId, null, previewUrl, trackName, 30, "Прев'ю: Apple Music", trackViewUrl)`.
  - `CommonsAudioProvider`: Вікідані `P51` (аудіо) музиканта → для кожного файла MediaWiki `prop=imageinfo&iiprop=url|size|mime|extmetadata` (+ `prop=videoinfo&viprop=derivatives`) → беремо лише файли з MP3 (оригінал `audio/mpeg` або MP3-похідна) — щоб грало на iPhone; `durationSec` з `duration`; `attribution` = «Запис: {Artist з extmetadata}, {LicenseShortName}, Wikimedia Commons»; `clipId` = `"cm_" + перші 10 hex SHA-1 назви файла`.
  - `AudioDuration.seconds(byte[] audio, String ext): int` — jaudiotagger (для `UPLOAD`, T22).
  - `AudioResolver.resolve(Artist a): List<AudioClip>` — зберігає наявні `UPLOAD`; далі провайдери в порядку `DEEZER`, `ITUNES`, `COMMONS`; ≤ 5 кліпів з джерела, ≤ 10 разом; виняток провайдера → лог і перехід до наступного (результат інших не губиться).
  - `AudioResolveJob.runAll(): Map<String, Integer>` (`@Scheduled(cron = "0 0 3 * * *", zone = "Europe/Kyiv")`) — музиканти з `audioCheckedAt == null` або старше 30 днів, послідовно; `startInBackground(): boolean`; `@EventListener onRequested(AudioResolveRequested)` — один музикант одразу; зберігає `audio`, `audioCheckedAt`, публікує `ArtistChangedEvent`, якщо змінився `hasAudio`; звіт у `jobs_runs` (`withAudioBefore`, `withAudioAfter`, `bySource`).

- [ ] **Step 1: Тести**
```java
@Test void extractsDeezerAndAppleIds()          // "https://www.deezer.com/en/artist/13612387" → "13612387"; "https://music.apple.com/ua/artist/okean-elzy/1029409" → "1029409"
@Test void deezerMapsTopTracksWithoutStoringUrl()   // url == null, externalId == "…", durationSec 30
@Test void deezerSkipsTracksWithoutPreview()
@Test void itunesMapsOnlyTracksWithPreview()
@Test void commonsKeepsOnlyMp3Playable()          // .ogg без MP3-похідної відкинуто
@Test void commonsAttributionHasAuthorAndLicense()
@Test void resolverKeepsUploadsAndOrdersSources() // UPLOAD перші, потім DEEZER, ITUNES, COMMONS
@Test void resolverCapsClips()                    // 3 провайдери × 5 + 2 UPLOAD → 10
@Test void providerFailureDoesNotDropOthers()
@Test void jobSkipsRecentlyChecked()              // audioCheckedAt = 5 днів тому → не запитується
@Test void jobPublishesChangeWhenHasAudioFlips()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `./gradlew test --tests 'com.nashi.audio.*'`.
- [ ] **Step 3: Commit** `feat(audio): resolve preview clips from Deezer, iTunes and Wikimedia Commons by known ids`

### Task 21: Випадковий фрагмент — `GET /api/v2/musicians/{id}/fragment`

**Files:**
- Create: `com/nashi/audio/{FragmentPicker,FragmentService,FragmentController,Fragment,DeezerPreviewUrls}.java`
- Test: `audio/FragmentPickerTest.java`, `audio/DeezerPreviewUrlsTest.java`, `audio/FragmentControllerIT.java`
- Modify: `ContractConformanceIT.IMPLEMENTED_OPERATIONS` (+ `getFragment`)

**Interfaces:**
- Consumes: T20 `AudioClip`, `HttpJson`; T9 `FileStorage.publicUrl`; T2 `NashiProperties.audio.fragmentSeconds`.
- Produces:
  - `public record Pick(AudioClip clip, double startSec, int durationSec)`
  - `FragmentPicker.pick(List<AudioClip> clips, @Nullable String excludeClipId, int fragmentSeconds, RandomGenerator rnd): Optional<Pick>` — тіло:
    ```java
    if (clips.isEmpty()) return Optional.empty();
    List<AudioClip> pool = clips.stream().filter(c -> !c.clipId().equals(excludeClipId)).toList();
    if (pool.isEmpty()) pool = clips;                               // єдиний кліп повторюється
    AudioClip c = pool.get(rnd.nextInt(pool.size()));
    int dur = Math.min(fragmentSeconds, c.durationSec());
    double maxStart = Math.max(0, c.durationSec() - dur);
    double start = Math.floor(rnd.nextDouble() * maxStart * 2) / 2; // крок 0,5 с
    return Optional.of(new Pick(c, start, dur));
    ```
  - `DeezerPreviewUrls.fresh(String trackId): PreviewUrl(String url, Instant expiresAt)` — `GET https://api.deezer.com/track/{id}` → `preview`; `expiresAt` = значення `exp=` з параметра `hdnea` мінус 60 с, а якщо його немає — `now + 10 хв`; кеш Caffeine до `expiresAt`.
  - `FragmentService.fragment(String artistId, @Nullable String exclude): Optional<Fragment>` — URL: `UPLOAD` → `FileStorage.publicUrl(key)`; `DEEZER` → `DeezerPreviewUrls.fresh`; `ITUNES`, `COMMONS` → збережений `url`; якщо Deezer не віддав URL — повторити `pick` без цього кліпу (≤ 3 спроби), далі `Optional.empty()`.
  - `Fragment` = схема 2.7 (`fadeInMs` 300, `fadeOutMs` 600, `expiresAt` лише для `DEEZER`).
  - `GET /api/v2/musicians/{id}/fragment?exclude=` → `200 Fragment` | `204` | `404` (не опублікований).

- [ ] **Step 1: Тести**
```java
@Test void emptyClipsGiveEmpty()
@Test void excludesGivenClipWhenOthersExist()     // 3 кліпи, exclude c2, 1000 seed-ів → c2 ніколи
@Test void singleClipRepeatsEvenIfExcluded()
@Test void fragmentFitsInsideClip()               // 10 000 seed-ів: 0 ≤ start, start + dur ≤ clip.durationSec
@Test void shortClipPlaysWholeFromZero()          // clip 9 с → start 0, dur 9
@Test void previewClip30sGivesStartUpTo15()       // start ∈ [0, 15], крок 0,5
@Test void deezerExpiryParsedFromHdnea()          // "…?hdnea=exp=1794910200~acl=…" (2026-11-17T10:10:00Z) → expiresAt 2026-11-17T10:09:00Z
@Test void deezerCachedUntilExpiry()
// FragmentControllerIT
@Test void noAudioIs204()
@Test void hiddenMusicianIs404()
@Test void returnsContractShape()                 // fadeInMs 300, fadeOutMs 600, durationSec 15
@Test void deezerFailureFallsBackToOtherClip()
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(audio): serve random in-bounds fragment with fresh Deezer preview URLs`

### Task 22: Міграція музикантів з V1

**Files:**
- Create: `com/nashi/migration/{V1MigrationRunner,V1MigrationJob,V1ArtistMapper,MigrationReport,V1MigrationProperties}.java`
- Create: `docs/adr/0002-v1-migration-scope.md`
- Test: `migration/V1ArtistMapperTest.java`, `migration/V1MigrationJobIT.java` (один Mongo-контейнер, дві бази `nashi` і `nashi_v2`; два MinIO-бакети)

**Interfaces:**
- Consumes: T6 `PlaceStore.nearest`, `PlaceSearchService`; T7 `Artist`, `SlugAllocator`, `ArtistRepository.findByLegacyV1Id`; T8 `PinPlacer`; T9 `FileStorage`; T20 `AudioDuration`; T18 `JobRunLog`.
- Produces:
  - **Обсяг (відхилення від буквального D-69, записується в ADR 0002):** переносяться артисти V1 зі `status = APPROVED` і `source = null` (додані через форму; у V1 це музиканти — карта V1 спочатку була музичною). Артисти V1 з `source = WIKIPEDIA` **не копіюються**: їх повністю і багатше (tagline, P2850/P2722, точне `placeId`) створює імпорт T19, а V1 не має для них даних, яких немає у Вікіпедії (лайків у V1 не було). Результат для замовника той самий: усі музиканти V1 є у V2.
  - `@ConfigurationProperties("nashi.migration.v1") record V1MigrationProperties(boolean run, boolean dryRun, String mongoUri, String s3Bucket)`
  - `V1MigrationRunner` (`ApplicationRunner`): якщо `run = true` — виконує `V1MigrationJob.run(dryRun)`, друкує звіт і завершує процес (`SpringApplication.exit`). Запуск: `java -jar app.jar --nashi.migration.v1.run=true --nashi.migration.v1.dry-run=true`.
  - `V1ArtistMapper.map(Document v1, Place place, String artistId, String slug): Artist` — `legacyV1Id = v1._id`, `source V1_MIGRATION`, `kind` = `members` не порожні або `categories` містить `BAND` → `BAND`, інакше `PERSON`; `summary` = `MediaWikiParser.truncate(biography, 1000)`; `links` з `socialLinks` (`SocialLink.Platform` V1 → `LinkPlatform`, невідомі → `OTHER`); `genres` = [`style`] якщо не порожній; `origin` = `{place, basis ORIGIN, precision CITY}`.
  - Місце: (1) пошук `place` за `Names.normalize(v1.city)` у `v1.region` (V1 `KYIV` + місто «Київ» → `KYIV_CITY`); (2) інакше `PlaceStore.nearest(v1.location, 15 000 м)`; (3) інакше артист переноситься без піна і потрапляє у звіт `withoutPlace`.
  - Файли: `imageKeys` з префіксом `artists/` — копіюються з бакета V1 (`s3Bucket`) у `artists/{newId}/{n}.jpg` через `ImageProcessor` (щоб отримати мініатюру і прибрати EXIF); зовнішні URL лишаються `url`. `demoTrackKeys` → копія в `artists/{newId}/audio/{n}.{ext}` + `AudioClip(clipId "up_" + n, UPLOAD, null, key, null, title "Демо {n+1}", durationSec = AudioDuration.seconds, "Завантажено виконавцем", null)`.
  - `V1MigrationJob.run(boolean dryRun): MigrationReport(int found, int migrated, int skippedExisting, int withoutPlace, List<String> errors)` — ідемпотентний (`findByLegacyV1Id` → пропуск); `dryRun` нічого не пише ні в базу, ні в S3.

- [ ] **Step 1: Тести**
```java
// V1ArtistMapperTest
@Test void mapsFormArtistWithMembersAsBand()
@Test void kyivCityResolvesToKyivCity()
@Test void unknownV1PlatformBecomesOther()
// V1MigrationJobIT
@Test void migratesApprovedFormArtistsWithImagesAndDemoTracks()  // 2 APPROVED source=null, 1 PENDING, 1 WIKIPEDIA → migrated 2
@Test void secondRunSkipsExisting()                               // migrated 0, skippedExisting 2
@Test void dryRunWritesNothing()
@Test void fallsBackToNearestPlaceWithin15km()
@Test void demoTrackBecomesUploadClipWithDuration()               // sample.mp3 → durationSec 3
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(migration): migrate approved V1 form artists with photos and demo tracks idempotently`

---
## Етап 4. Web

### Task 23: Ядро карти — рівні агрегації, індекси, камера, стиль, фільтри

**Files:**
- Create: `web/src/core/map/{aggregation.ts,camera.ts,mapStyle.ts,geo.ts,useMapModel.ts}`, `web/src/core/filters/filterState.ts`
- Modify: `web/vite.config.ts` (alias `@contracts` → `../contracts`)
- Test: `src/core/map/aggregation.test.ts`, `camera.test.ts`, `mapStyle.test.ts`, `src/core/filters/filterState.test.ts`

**Interfaces:**
- Consumes: T3 типи `components['schemas'][…]` (`MapPin`, `RegionStat`, `PlaceStat`, `FilterDefinition`), `DesignConfig`; `contracts/geo/*` (план студії, задача 2); V1 `nashi-frontend/src/features/map/utils/clusterPins.ts` (ідея індексу і `formatClusterCount`/`clusterLabel` — перенести з тестами `cluster-pins`), `aeroMapStyle.ts` (`applyUkrainianLabels` — переписати як чисте перетворення стилю, без палітри Aero), `pinFilters.ts` (`normalizeName` — перенести).
- Produces:
  ```ts
  export type AggregationMode = 'REGION_PLACE_PIN' | 'PLACE_PIN' | 'CHOROPLETH_PLACE_PIN';
  export type MapLevel = 'REGION' | 'PLACE' | 'PIN';
  export interface Thresholds { regionMaxZoom: number; placeMaxZoom: number }
  export type BBox = [west: number, south: number, east: number, north: number];
  export type MapItem =
    | { kind: 'region'; code: Region; name: string; lon: number; lat: number; count: number }
    | { kind: 'placeCluster'; id: number; lon: number; lat: number; count: number }
    | { kind: 'place'; placeId: string; name: string; lon: number; lat: number; count: number }
    | { kind: 'pinCluster'; id: number; lon: number; lat: number; count: number }
    | { kind: 'pin'; pin: MapPin };
  export interface MapIndexes { level(zoom: number): MapLevel; items(bbox: BBox, zoom: number): MapItem[]; expansionZoom(item: MapItem): number; total: number }
  export function levelForZoom(zoom: number, t: Thresholds, mode: AggregationMode): MapLevel;
  export function buildIndexes(input: { pins: MapPin[]; regions: RegionStat[]; places: PlaceStat[]; filters: FilterState; thresholds: Thresholds; mode: AggregationMode }): MapIndexes;
  export function formatCount(n: number): string;            // 7 → "7", 1234 → "1,2 тис." (V1)
  // camera.ts
  export interface Insets { top: number; right: number; bottom: number; left: number }
  export type CameraMove =
    | { type: 'fitBounds'; bounds: [[number, number], [number, number]]; padding: Insets; duration: number }
    | { type: 'flyTo'; center: [number, number]; zoom: number; duration: number };
  export const UKRAINE_BBOX: BBox;                          // [22.137, 44.361, 40.228, 52.379]
  export function ukraineCamera(insets: Insets, reducedMotion: boolean): CameraMove;
  export function regionsCamera(regions: RegionStat[], insets: Insets, reducedMotion: boolean): CameraMove; // об'єднання bbox, padding 40 + insets, 1200 мс
  export function placeCamera(lon: number, lat: number, reducedMotion: boolean): CameraMove;               // zoom 12, 1200 мс
  export function clusterCamera(lon: number, lat: number, expansionZoom: number, reducedMotion: boolean): CameraMove; // zoom min(exp, 17), 600 мс
  // mapStyle.ts
  export function prepareMapStyle(style: StyleSpecification, urls: { tiles: string; glyphs: string }): StyleSpecification;
  export function blankStyle(): StyleSpecification;
  // geo.ts
  export const regionsGeoJson: FeatureCollection; export const ukraineOutline: FeatureCollection;
  export function regionAt(lon: number, lat: number): Region | null;   // point-in-polygon (@turf/boolean-point-in-polygon)
  // filterState.ts
  export type FilterState = Record<string, string[]>;
  export function parseFilterParams(sp: URLSearchParams, defs: FilterDefinition[]): FilterState;
  export function writeFilterParams(sp: URLSearchParams, state: FilterState): URLSearchParams;
  export function applyFilters(pins: MapPin[], state: FilterState): MapPin[];
  export function activeFilterCount(state: FilterState): number;
  // useMapModel.ts
  export function useMapModel(): { indexes: MapIndexes | null; filters: FilterState; setFilters(s: FilterState): void; loading: boolean; error: ApiProblem | null };
  ```

Рівень і лічильники (тіло, бо тести не визначають порядок умов):
```ts
export function levelForZoom(zoom: number, t: Thresholds, mode: AggregationMode): MapLevel {
  if (zoom >= t.placeMaxZoom) return 'PIN';
  if (mode !== 'PLACE_PIN' && zoom < t.regionMaxZoom) return 'REGION';
  return 'PLACE';
}
// buildIndexes: filtered = applyFilters(pins, filters)
//   regionCounts = count(filtered by pin.region); placeCounts = count(filtered by pin.placeId)
//   поки pins порожні (ще вантажаться) — лічильники з regions[].count / places[].count (перший кадр)
//   REGION: по одному item на регіон з count > 0 у точці center (polylabel)
//   PLACE:  supercluster над places (radius 60, maxZoom = placeMaxZoom, reduce: сума count) з placeCounts
//   PIN:    supercluster над filtered (radius 40, maxZoom 16); однакові координати → pinCluster, що не розпадається:
//           expansionZoom > 17 → UI показує список (як V1 StackMarker)
```

- [ ] **Step 1: Тести**
```ts
// aggregation.test.ts
it('levels for REGION_PLACE_PIN at zoom 5, 6.99, 7, 9.99, 10', () =>
  expect([5, 6.99, 7, 9.99, 10].map(z => levelForZoom(z, { regionMaxZoom: 7, placeMaxZoom: 10 }, 'REGION_PLACE_PIN')))
    .toEqual(['REGION', 'REGION', 'PLACE', 'PLACE', 'PIN']));
it('PLACE_PIN never returns REGION', () => {});
it('region items only for regions with count > 0 and selected regions when filtered', () => {});
it('place counts are recomputed from filtered pins', () => {});
it('uses server counts until pins are loaded', () => {});
it('nearby places merge into a placeCluster with summed count at zoom 7', () => {});
it('pins inside bbox only', () => {});
it('identical coordinates stay one pinCluster up to zoom 17', () => {});
it('formatCount', () => expect([7, 999, 1234, 4000].map(formatCount)).toEqual(['7', '999', '1,2 тис.', '4 тис.']));
// camera.test.ts
it('regionsCamera fits union bbox with padding 40 + insets', () => {});
it('reduced motion gives duration 0', () => {});
it('clusterCamera caps zoom at 17', () => {});
// mapStyle.test.ts (фікстура: мінімальний стиль OpenMapTiles з 3 symbol-шарами, 2 boundary-шарами)
it('replaces {{TILES_URL}} and {{GLYPHS_URL}}', () => {});
it('every symbol text-field is coalesce(name:uk, name)', () => {});
it('hides every layer of source-layer boundary (borders come from our GeoJSON with Crimea)', () => {});
it('is pure and idempotent', () => {});
// filterState.test.ts
it('parses region=LVIV,KYIV_CITY', () => {});
it('drops unknown keys and unknown values', () => {});    // ?region=MOSCOW&genre=x → {}
it('writes comma-joined and removes empty keys', () => {});
it('applyFilters ORs values within a key and ANDs keys', () => {});
```
- [ ] **Step 2: Запустити — падає.** Run: `npm run test -w web -- src/core/map src/core/filters` → FAIL.
- [ ] **Step 3: Реалізувати.** `useMapModel` читає фільтри з `useSearchParams` (запис з `{ replace: true }`, як V1), визначення — з `useFilters()`, пороги — з `usePublicConfig()`, режим — `useDesign().variant('map.aggregation')`; індекси перебудовуються лише при зміні даних/фільтрів (`useMemo`).
- [ ] **Step 4: Запустити — PASS.**
- [ ] **Step 5: Commit** `feat(web-core): add map aggregation levels, indexes, camera moves, style prep and URL filters`

### Task 24: Екран карти (каркасний скін)

**Files:**
- Create: `web/src/ui/map/{MapView.tsx,MapOverlays.tsx,RegionBubble.tsx,PlaceBubble.tsx,ClusterBubble.tsx,PinMarker.tsx,PinStackList.tsx,OriginNotice.tsx,FilterSheet.tsx,RegionFilter.tsx,NearMeButton.tsx,SoundToggle.tsx,BackToUkraine.tsx}`, `web/src/ui/layout/{AppShell.tsx,BottomSheet.tsx}`
- Modify: `web/src/pages/MapPage.tsx`
- Test: `e2e/map-drilldown.spec.ts`, `e2e/origin-notice.spec.ts`, `e2e/filters.spec.ts`, `e2e/near-me.spec.ts`

**Interfaces:**
- Consumes: T23 `useMapModel`, `MapItem`, камери, `prepareMapStyle`, `regionAt`; T3 `useCopy`, `useDesign`; T25 `useFragmentPlayer` (піни передають жести в аудіо; до T25 — заглушка без звуку).
- Produces (ці атрибути — контракт для E2E і для скіну T29; скін не має права їх прибрати):
  - контейнер карти `data-testid="map"` з `data-level="REGION|PLACE|PIN"`;
  - бульбашки `role="button"`, `data-testid="region-bubble|place-bubble|cluster-bubble"`, `aria-label` з `cluster.ariaLabel`;
  - пін `role="button"`, `data-testid="pin"`, `data-slug`, `data-has-audio`, `aria-label` з `pin.ariaLabel`;
  - пояснення геомітки `data-testid="origin-notice"` (текст `map.originNotice`), видиме на всіх рівнях;
  - кнопки `data-testid="filters-open|near-me|sound-toggle|back-to-ukraine"`; лічильник `data-testid="results-count"`.
- Поведінка: камера неконтрольована (`initialViewState` = `ukraineCamera`), `maxBounds` = `UKRAINE_BBOX` ± 1°, `minZoom 5`, `maxZoom 17`; тап по області → `regionsCamera([region])`; по пункту → `placeCamera`; по кластеру → `clusterCamera`; по стосу з однаковими координатами на zoom 17 → `PinStackList` (список у шторці); `?region=` при завантаженні → `regionsCamera` (D-10); «Біля мене» → `navigator.geolocation` → `regionAt` → `regionsCamera`; відмова → тост `map.nearMeDenied`; поза Україною → тост і нічого. Рендер шарів `MapOverlays`: контур України (`color.map.ua-border`), лінії областей (`color.map.region-line`), заливка вибраних (`color.map.region-fill-active`; для `CHOROPLETH_PLACE_PIN` — заливка всіх з інтенсивністю за `count`). DOM-маркерів одночасно ≤ 300 (більше — лише кластери, попередження в консоль у dev).

- [ ] **Step 1: E2E-тести (проєкти `mobile` і `desktop`, MSW, `VITE_MAP_STYLE=blank`)**
```ts
test('opens on all of Ukraine with region bubbles and origin notice', …)  // data-level=REGION, ≥ 20 region-bubble, origin-notice visible
test('drills down region → place → pins', …)       // click «Львівська область» → data-level=PLACE; click «Львів» → data-level=PIN; ≥ 1 pin
test('deep link ?region=LVIV opens the region', …)
test('origin notice stays visible on every level', …)
test('region filter writes URL, survives reload, reset clears', …)  // ?region=LVIV; results-count == сума LVIV з фікстур
test('near me flies to the user region', …)        // context.setGeolocation({ latitude: 49.84, longitude: 24.03 }) → ?region відсутній, але камера в LVIV: data-level=PLACE і є place-bubble «Львів»
test('touch targets are at least 44px on mobile', …) // boundingBox() pin, region-bubble, filters-open ≥ 44×44
```
- [ ] **Step 2: Запустити — падає.** Run: `npm run e2e -w web -- e2e/map-drilldown.spec.ts e2e/origin-notice.spec.ts e2e/filters.spec.ts e2e/near-me.spec.ts` → FAIL.
- [ ] **Step 3: Реалізувати** компоненти з нейтральним каркасним скіном (лише токени `--n-*`, тексти — `useCopy`). Варіант `map.origin-notice = PERSISTENT_CHIP` — плашка над нижньою панеллю.
- [ ] **Step 4: Запустити — PASS** на обох проєктах.
- [ ] **Step 5: Commit** `feat(web): map screen with region/place/pin drill-down, filters, near-me and persistent origin notice`

### Task 25: Аудіо-двигун і тригери (desktop hover, mobile-стратегії)

**Files:**
- Create: `web/src/core/audio/{engine.ts,activation.ts,hoverIntent.ts,mobileTrigger.ts,fragments.ts,useFragmentPlayer.ts,AudioRoot.tsx}`
- Test: `src/core/audio/{engine,hoverIntent,mobileTrigger,useFragmentPlayer}.test.ts`, `e2e/audio.spec.ts`

**Interfaces:**
- Consumes: T3 `apiFetch` (`getFragment`, 204 → `null`), `useDesign().variant('audio.mobile-trigger')`, `param('audio.mobile-trigger','longPressMs',400)`.
- Produces:
  ```ts
  export interface AudioLike { src: string; currentTime: number; volume: number; play(): Promise<void>; pause(): void;
    addEventListener(t: 'ended' | 'error' | 'loadedmetadata', l: () => void): void; removeEventListener(t: string, l: () => void): void }
  export interface Clock { now(): number; setTimeout(fn: () => void, ms: number): number; clearTimeout(h: number): void }
  export type EngineStatus = 'idle' | 'loading' | 'playing' | 'blocked' | 'error';
  export interface EngineState { status: EngineStatus; musicianId: string | null; clipId: string | null }
  export interface AudioEngine { play(musicianId: string, f: Fragment): Promise<void>; stop(opts?: { fade?: boolean }): void;
    getState(): EngineState; subscribe(l: (s: EngineState) => void): () => void }
  export function createAudioEngine(deps: { audio: AudioLike; clock: Clock; volumeWritable: boolean }): AudioEngine;
  export function isVolumeWritable(a: HTMLAudioElement): boolean;  // volume = 0.5 → читається 0.5? (iOS — ні)
  export function hasUserActivation(): boolean; export function onFirstActivation(target: EventTarget, cb: () => void): () => void;
  export function createHoverIntent(delayMs: number, clock: Clock): { enter(id: string, fire: () => void): void; leave(id: string): void; cancel(): void };
  export type MobileTrigger = 'TAP_PREVIEW_AND_PLAY' | 'TAP_PREVIEW_PLAY_BUTTON' | 'LONG_PRESS_PLAY';
  export type PinGesture = { type: 'tap' | 'longPress' | 'playButton' | 'closePreview'; musicianId: string };
  export interface PinUiState { previewId: string | null; playingId: string | null }
  export interface PinAction { preview?: string | null; play?: string; stop?: true }
  export function reducePinGesture(mode: MobileTrigger, s: PinUiState, g: PinGesture): PinAction;
  export function useFragmentPlayer(): { state: EngineState; previewId: string | null; noAudio: Set<string>;
    pointerEnter(id: string): void; pointerLeave(id: string): void; gesture(g: PinGesture): void; playMusician(id: string): void; stop(): void };
  ```
- Поведінка двигуна (тіло — бо таймінги не випливають із сигнатур): `play` скасовує попереднє відтворення (токен покоління); `src = f.url`; після `loadedmetadata` — `currentTime = f.startSec`; якщо `volumeWritable` — `volume = 0` і лінійний підйом до 1 за `fadeInMs` (крок 50 мс); `play()` → `NotAllowedError` → `status 'blocked'` (без винятку назовні); таймер на `durationSec·1000 − fadeOutMs` → спад до 0 за `fadeOutMs` → `pause()` → `idle`; без `volumeWritable` — без змін гучності, `pause()` рівно на `durationSec·1000`; подія `error` → `status 'error'`.
- `useFragmentPlayer`: один `AudioEngine` на застосунок (`AudioRoot`); desktop (`matchMedia('(hover: hover) and (pointer: fine)')`) — `pointerEnter` одразу префетчить фрагмент, через 250 мс грає, `pointerLeave` → `stop({fade:true})`; touch — `gesture` через `reducePinGesture`; `exclude` = останній `clipId` цього музиканта; помилка відтворення → один повторний запит з `exclude` = зіпсований кліп; `204` → `noAudio.add(id)`; стоп при зміні маршруту, `visibilitychange: hidden`, відкритті іншого прев'ю.

- [ ] **Step 1: Тести** (фейкові `AudioLike` і `Clock`)
```ts
it('plays from startSec with linear fade-in when volume is writable', …)  // t=0 volume 0; t=150 ≈0.5; t=300 1; currentTime == startSec
it('fades out and pauses at durationSec', …)                               // fadeOut 600: t=14 400 починає спад, t=15 000 paused, status idle
it('a new play cancels the previous one', …)
it('play rejected with NotAllowedError goes to blocked', …)               // Review Focus 3
it('skips fades when volume is not writable', …)                          // Review Focus 3: volume не змінювався; pause рівно на 15 000
it('stop without fade pauses immediately', …)
it('hover intent fires after 250 ms and is cancelled by leave', …)
// mobileTrigger.test.ts — таблиця
it.each([
  ['TAP_PREVIEW_AND_PLAY',    { previewId: null, playingId: null }, { type: 'tap', musicianId: 'a' },        { preview: 'a', play: 'a' }],
  ['TAP_PREVIEW_AND_PLAY',    { previewId: 'a', playingId: 'a' },   { type: 'tap', musicianId: 'a' },        { stop: true }],
  ['TAP_PREVIEW_AND_PLAY',    { previewId: 'a', playingId: 'a' },   { type: 'tap', musicianId: 'b' },        { preview: 'b', play: 'b' }],
  ['TAP_PREVIEW_AND_PLAY',    { previewId: 'a', playingId: 'a' },   { type: 'closePreview', musicianId: 'a' }, { preview: null, stop: true }],
  ['TAP_PREVIEW_PLAY_BUTTON', { previewId: null, playingId: null }, { type: 'tap', musicianId: 'a' },        { preview: 'a' }],
  ['TAP_PREVIEW_PLAY_BUTTON', { previewId: 'a', playingId: null },  { type: 'playButton', musicianId: 'a' }, { play: 'a' }],
  ['TAP_PREVIEW_PLAY_BUTTON', { previewId: 'a', playingId: 'a' },   { type: 'playButton', musicianId: 'a' }, { stop: true }],
  ['TAP_PREVIEW_PLAY_BUTTON', { previewId: 'a', playingId: 'a' },   { type: 'tap', musicianId: 'b' },        { preview: 'b', stop: true }],
  ['LONG_PRESS_PLAY',         { previewId: null, playingId: null }, { type: 'longPress', musicianId: 'a' },  { play: 'a' }],
  ['LONG_PRESS_PLAY',         { previewId: null, playingId: null }, { type: 'tap', musicianId: 'a' },        { preview: 'a' }],
  ['LONG_PRESS_PLAY',         { previewId: null, playingId: 'a' },  { type: 'longPress', musicianId: 'a' },  { stop: true }],
  ['LONG_PRESS_PLAY',         { previewId: 'a', playingId: 'a' },   { type: 'tap', musicianId: 'b' },        { preview: 'b', stop: true }],
])('%s %o %o → %o', (mode, s, g, expected) => expect(reducePinGesture(mode, s, g)).toEqual(expected));
// useFragmentPlayer.test.ts
it('204 marks musician as noAudio and does not play', …)
it('second play of same musician sends exclude = previous clipId', …)
it('stops on route change and when page becomes hidden', …)
```
`e2e/audio.spec.ts`: desktop — клік по сторінці (активація), наведення на пін з `data-has-audio=true` → за ≤ 1 с `window.__nashiAudioState.status === 'playing'` (у dev/e2e двигун публікує стан у `window`), відведення курсора → `idle`; до першого кліку → `blocked` і видно `map.soundBlockedHint`; mobile — тап по піну → прев'ю відкрите і `playing`, повторний тап → `idle`.
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `npm run test -w web -- src/core/audio && npm run e2e -w web -- e2e/audio.spec.ts`.
- [ ] **Step 3: Commit** `feat(web-core): audio engine with fades, autoplay handling, hover intent and mobile trigger strategies`

### Task 26: Прев'ю музиканта і сторінка `/m/:slug`

**Files:**
- Create: `web/src/core/musician/view.ts`, `web/src/ui/musician/{MusicianPreview.tsx,MusicianHeader.tsx,ImageGallery.tsx,MaterialsSection.tsx,LinksSection.tsx,OriginSection.tsx,LikeButton.tsx,TextSourceNote.tsx,PhotoCredit.tsx,YouTubeEmbed.tsx}`
- Modify: `web/src/pages/MusicianPage.tsx`, `web/src/pages/MapPage.tsx` (`?musician=<slug>`)
- Test: `src/core/musician/view.test.ts`, `src/pages/MusicianPage.test.tsx`, `e2e/musician-page.spec.ts`

**Interfaces:**
- Consumes: T3 `useMusician`, `useCopy`, `useDesign`; T25 `useFragmentPlayer`; T27 `useLike`; V1 `nashi-frontend/src/features/artists/utils/{lifeDates.ts,media.ts,wikiAttribution.ts}` і `lib/utils/isWebUrl.ts` (перенести з тестами).
- Produces:
  - `view.ts`: `originLabel(o: Origin, t: Copy): string` (за `basis`: «Народився/народилася в …» не робимо — рід невідомий, тому `musician.originLabel.BIRTH` = «Місце народження: {place}», `FORMATION` = «Місце заснування: {place}», `ORIGIN` = «Походить з: {place}»; для `precision CITY` додається `musician.precisionNote.CITY`); `lifeDatesLabel(d: LifeDates): string | null`; `groupLinks(links: Link[]): Record<LinkGroup, Link[]>`; `isSafeHttpUrl(url: string): boolean`; `youtubeEmbedUrl(youtubeId: string): string` (`https://www.youtube-nocookie.com/embed/{id}`).
  - Сторінка має блоки з `data-testid`: `musician-name`, `musician-tagline`, `musician-summary`, `musician-image`, `musician-materials`, `musician-music-links`, `musician-youtube`, `musician-origin` (з `origin-notice`), `like-button`, `likes-count`, `text-source`, `photo-credit`; кнопки «Слухати фрагмент» (якщо `hasAudio`) і «Показати на карті» (`/?musician=<slug>` → карта летить до піна і відкриває прев'ю; невідомий slug → тост `errors.notFound`, як V1 `?artist=`).
  - Відео YouTube вбудовуються лише після натискання (без завантаження iframe наперед); усі зовнішні посилання — `target="_blank" rel="noopener noreferrer"` і лише `isSafeHttpUrl`.
  - `<title>` і `<meta name="description">` — через React 19 (рендер `<title>` у компоненті).
  - Невідомий slug (404) → `NotFoundPage` з посиланням на карту.

- [ ] **Step 1: Тести**
```ts
it('origin labels by basis and precision note for CITY', …)
it('rejects javascript: and data: urls', …)
it('groups links MUSIC, VIDEO, REFERENCE, SOCIAL', …)
// MusicianPage.test.tsx (MSW, фікстура з усіма полями)
it('renders all required blocks', …)   // 9 блоків вимоги: name, summary, image, materials, music-links, youtube, origin, like-button, likes-count
it('hides listen button and shows musician.noAudio when hasAudio is false', …)
it('shows CC BY-SA source under Wikipedia text and photo credit', …)
it('renders without images', …)
// e2e/musician-page.spec.ts
test('page from map preview and back to map', …)
test('unknown slug shows not found with link to map', …)
test('youtube iframe loads only after click', …)
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(web): musician preview and page with materials, links, origin, likes and attribution`

### Task 27: Лайки на клієнті — батчинг без втрат

**Files:**
- Create: `web/src/core/likes/{batcher.ts,useLike.ts,LikesRoot.tsx}`
- Test: `src/core/likes/batcher.test.ts`, `e2e/likes.spec.ts`

**Interfaces:**
- Consumes: T3 `apiFetch` (`addLikes`), `usePublicConfig().likes`.
- Produces:
  ```ts
  export type SendResult = { ok: true; likesCount: number } | { ok: false; retryAfterMs: number };
  export interface LikeBatcherDeps { send(id: string, count: number, opts: { keepalive: boolean }): Promise<SendResult>; clock: Clock }
  export interface LikeBatcher { tap(id: string): void; pending(id: string): number; flushAll(opts: { keepalive: boolean }): void;
    onServerCount(l: (id: string, likesCount: number) => void): () => void }
  export function createLikeBatcher(deps: LikeBatcherDeps, cfg: { flushMs: number; max: number }): LikeBatcher;
  export function useLike(musicianId: string, serverCount: number): { count: number; tap(): void };  // count = останній серверний + pending
  ```
  Алгоритм (тіло):
  ```ts
  // tap(id): pending[id]++; якщо pending[id] ≥ max → flush(id) зараз; інакше перезапустити таймер id на flushMs
  // flush(id, keepalive=false): якщо inFlight[id] — вийти (після відповіді flush повториться, якщо pending > 0)
  //   n = min(pending[id], max); pending[id] -= n; inFlight[id] = true; r = await send(id, n, {keepalive})
  //   r.ok      → emit(id, r.likesCount); якщо pending[id] > 0 → flush(id)
  //   !r.ok     → pending[id] += n; таймер на r.retryAfterMs (429)
  //   виняток   → pending[id] += n; повтор через 2000 мс, не більше 5 разів поспіль; далі чекати наступного tap
  // flushAll({keepalive:true}) на 'pagehide' і 'visibilitychange' (hidden): надсилає всі pending частинами ≤ max з keepalive
  ```
  Серверний лічильник у UI = `max(відомий з відповіді, відомий з даних сторінки)` + `pending` (без «відскоку» назад при паралельних відповідях).

- [ ] **Step 1: Тести** (фейковий `Clock`)
```ts
it('groups 7 quick taps into one request of 7 after 600 ms', …)
it('flushes immediately at 50 taps and continues batching the rest', …)  // 53 тапи → send(50), потім send(3) через 600 мс
it('retries rate-limited taps without losing any', …)                   // Review Focus 2: send → {ok:false, retryAfterMs: 3000}; сума всіх успішних count == кількість тапів
it('flushAll sends remaining with keepalive', …)                         // Review Focus 2
it('never has two requests in flight for one musician', …)
it('optimistic count = server count + pending', …)
```
`e2e/likes.spec.ts`: 7 тапів → лічильник +7 одразу; за 600 мс один `POST …/likes` з `{"count":7}` (перехоплення `page.waitForRequest`); перезавантаження → лічильник із сервера містить +7.
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(web-core): batch unlimited likes with optimistic count, retry and keepalive flush`

### Task 28: Форма «Запропонувати музиканта»

**Files:**
- Create: `web/src/core/submission/{schema.ts,platforms.ts,imageSize.ts,compressImage.ts,upload.ts,problemErrors.ts,draft.ts}`, `web/src/ui/suggest/{SuggestForm.tsx,PlaceAutocomplete.tsx,LinksField.tsx,ImagesField.tsx,SubmitProgress.tsx,SuggestSuccess.tsx}`
- Modify: `web/src/pages/SuggestPage.tsx`
- Test: `src/core/submission/{schema,platforms,imageSize,upload,problemErrors}.test.ts`, `e2e/suggest.spec.ts` (фікстури `e2e/files/{photo1.jpg,photo2.png,fake.jpg}`)

**Interfaces:**
- Consumes: T3 `usePlaceSearch`, `usePublicConfig().submission`, `ApiProblem`; `contracts/test-vectors/links.json`; V1 `nashi-frontend/src/lib/utils/detectSocialPlatform.ts` (ідея — переписати під `LinkPlatform` V2), V1 `stores/useSubmissionStore.ts` (патерн фонового прогресу і повтору — не переносимо як є: у V2 прогрес реальний, з XHR).
- Produces:
  - `export const submissionSchema: z.ZodType<SubmissionFormValues>` — ті самі правила, що `SubmissionValidator` (T14), повідомлення — ключі `submit.error.*`; `export type SubmissionFormValues = Omit<SubmissionRequest, 'faxNumber'> & { images: File[] }`.
  - `export function detectPlatform(url: string): LinkPlatform` (паритет з backend `LinkClassifier` на `links.json`).
  - `export function targetSize(w: number, h: number, maxSide: number): { width: number; height: number }`
  - `export async function compressImage(file: File, maxSide = 2048, quality = 0.85): Promise<File>` — `createImageBitmap(file, { imageOrientation: 'from-image' })` → `OffscreenCanvas`/`canvas` → `image/jpeg`; файли ≤ 2048 px і ≤ 2 МБ не чіпає.
  - `export function uploadSubmission(values: SubmissionFormValues, onProgress: (ratio: number) => void, signal?: AbortSignal): Promise<SubmissionAccepted>` — `XMLHttpRequest` (реальний прогрес завантаження), `FormData`: `data` як `Blob([JSON.stringify(...)], { type: 'application/json' })`, `images` × N, `faxNumber: ''` з прихованого поля.
  - `export function problemToFieldErrors(p: ApiProblem): Record<string, string>` (`images[2]` → поле фото з індексом).
  - `draft.ts`: чернетка текстових полів у `sessionStorage` (після помилки мережі дані не губляться).
  - UI (`SINGLE_PAGE` за замовчуванням): поля 2.10 у порядку: ім'я/назва → тип → місто походження (автодоповнення, 250 мс) → точне місце народження (лише для «Людина») → короткий опис з лічильником символів → посилання (додати/видалити, бейдж платформи) → YouTube-канал → Вікіпедія → жанри (до 5 чипів) → фото (1–5, прев'ю, видалення) → контакт → згода → «Надіслати»; приховане поле `faxNumber` (`aria-hidden`, `tabIndex=-1`, поза екраном). Під час відправки — прогрес `SubmitProgress` і блокування повторного натискання; успіх — `SuggestSuccess` (`submit.success`), форма і чернетка очищаються.

- [ ] **Step 1: Тести**
```ts
it('schema mirrors backend rules', …)          // ті самі кейси, що SubmissionValidatorTest: summary < 30, без music/video link, BAND+birthPlace, 6 фото, javascript: URL
it.each(linksJson)('detectPlatform %s', …)    // паритет з backend
it('targetSize keeps aspect and never upscales', …)   // (4032,3024,2048) → (2048,1536); (800,600,2048) → (800,600)
it('upload reports progress and resolves 202', …)
it('maps problem errors to fields including images[i]', …)
// e2e/suggest.spec.ts (mobile і desktop)
test('happy path: autocomplete, links, two photos, success', …)   // "Бро" → «Броди, Львівська область»
test('server validation errors are shown at fields', …)          // MSW повертає 400 з errors[summary]
test('network failure keeps data and allows retry', …)           // перший запит abort → кнопка «Спробувати ще», поля заповнені
test('birth place field only for a person', …)
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.**
- [ ] **Step 3: Commit** `feat(web): musician suggestion form with place autocomplete, link detection, photo compression and real upload progress`

### Task 29: Підключення затвердженого дизайну (handoff студії)

Задача з двох частин: **29a** (до handoff, т.7) — механізм підключення; **29b** (після handoff, т.8–9) — імплементація скіну. Залежить від плану студії (`docs/plans/…-design-studio-plan.md`): пакет `studio/handoff/` за `02-contracts.md` розділ 7.

**Files:**
- Create: `web/scripts/design-sync.mjs`, `web/scripts/design-sync.test.mjs`, `web/src/ui/variants.ts`, `web/src/ui/variants.test.tsx`, `web/src/design/tokens-contrast.test.ts`, `e2e/visual.spec.ts`
- Modify (29b): лише `web/src/ui/**`, `web/src/design/**` (результат `design:sync`), `web/src/index.css`, `web/public/` (favicon, manifest-іконки), знімки `e2e/visual.spec.ts-snapshots/**`

**Interfaces:**
- Consumes: T3 `loadDesignConfig`, `DecisionId`; T24–T28 компоненти `src/ui/**` і їхні `data-testid`.
- Produces:
  - `npm run design:sync -w web` → `node scripts/design-sync.mjs ../studio/handoff src/design`: копіює `decisions.json`, `tokens.css`, `map-style.json`, `copy.uk.json`, `assets/**`; **падає з переліком**, якщо: `schemaVersion ≠ 1`; у `tokens.css` бракує будь-якої змінної з `02-contracts.md` розділ 5; у `copy.uk.json` бракує обов'язкового ключа розділу 8; `map-style.json` не містить плейсхолдерів `{{TILES_URL}}`/`{{GLYPHS_URL}}`; варіант технічного рішення не входить у колонку «Технічні ID» розділу 6.
  - `variants.ts`: реєстр `export const VARIANTS = { 'map.pin': { AVATAR_RING, DROP_ICON, DOT_LABEL }, 'map.cluster': {…}, 'map.origin-notice': {…}, 'audio.desktop-hover': {…}, 'audio.player': {…}, 'musician.preview': {…}, 'musician.page': {…}, 'like.button': {…}, 'filters.region': {…}, 'submission.form': {…}, 'nav.structure': {…}, 'onboarding': {…} } satisfies Record<DecisionId, Record<string, ComponentType<any>>>`; рішення `map.aggregation`, `map.initial-view`, `audio.mobile-trigger` — конфігурація ядра (T23, T25), не компоненти.

- [ ] **Step 1 (29a): Тести механізму**
```js
// design-sync.test.mjs (node:test, тимчасові теки)
test('copies a valid handoff', …)
test('fails listing missing tokens', …)          // без --n-color-like → помилка містить "color.like"
test('fails on unknown technical variant', …)    // audio.mobile-trigger = "SHAKE" → помилка
test('fails when map style has no placeholders', …)
```
```ts
// variants.test.tsx
it('every technical variant id from contracts section 6 is registered or is core config', …)
// tokens-contrast.test.ts — WCAG з 02-contracts розділ 5
it('text on bg/surface ≥ 4.5, on-accent on accent ≥ 4.5, on-like on like ≥ 4.5, text-muted on surface ≥ 3, cluster.text on cluster.bg ≥ 3', …)
```
- [ ] **Step 2: Запустити — падає; реалізувати; PASS.** Run: `node --test web/scripts/ && npm run test -w web -- src/ui/variants src/design`.
- [ ] **Step 3: Commit** `feat(web): add design handoff sync with validation and variant registry`
- [ ] **Step 4 (29b): Синхронізувати handoff.** Run: `npm run design:sync -w web` → PASS без помилок (якщо помилка — повернути студії перелік, не правити handoff вручну).
- [ ] **Step 5: Імплементувати кожне `APPROVED`-рішення** за `studio/handoff/HANDOFF.md` у відповідних компонентах `src/ui/**` (один коміт на рішення: `feat(ui): implement approved <decision-id> = <variant>`); шрифти — `@font-face` з `src/design/assets/fonts` з `font-display: swap`; іконки — SVG з `assets/icons`.
- [ ] **Step 6: Доказ «дизайн без переробки логіки».** Run:
```bash
git diff --stat <commit кроку 3>..HEAD -- web/src/core backend contracts web/e2e ':!web/e2e/visual.spec.ts' ':!web/e2e/visual.spec.ts-snapshots'
```
Expected: порожній вивід. І всі тести зелені **без змін**: `npm run test -w web && npm run e2e -w web && (cd backend && ./gradlew test)`.
- [ ] **Step 7: Візуальні знімки.** `e2e/visual.spec.ts`: ключові екрани (карта на 3 рівнях, прев'ю, сторінка, форма, порожні стани «немає фрагмента», «нічого не знайдено») на `mobile` і `desktop`; після рев'ю дизайн-командою — `npm run e2e -w web -- e2e/visual.spec.ts --update-snapshots`, далі `toHaveScreenshot({ maxDiffPixelRatio: 0.01 })`.
- [ ] **Step 8: Commit** `test(ui): add visual baselines approved by design team`

### Task 30: Доступність і продуктивність

**Files:**
- Create: `web/e2e/a11y.spec.ts`, `web/lighthouserc.json`, `web/scripts/check-bundle.mjs`
- Modify: `web/vite.config.ts` (`build.rollupOptions.output.manualChunks`: `maplibre` окремо), `web/package.json` (`"check:bundle"`, `"lhci"`)

**Interfaces:**
- Consumes: T24–T28.
- Produces: бюджети, що перевіряються в CI (T31).

- [ ] **Step 1: Тести і бюджети**
```ts
// a11y.spec.ts (@axe-core/playwright)
test('map, musician page and suggest form have no serious or critical violations', …)
test('pins and bubbles are reachable by keyboard and show focus', …)   // Tab до першого pin, Enter відкриває прев'ю
test('reduced motion makes camera moves instant', …)                   // emulateMedia({ reducedMotion: 'reduce' })
```
`check-bundle.mjs`: стартовий JS (без чанку `maplibre`) ≤ 180 КБ gzip, чанк `maplibre` завантажується лише на `/` і `/m/:slug` (мінікарта не використовується — лише на `/`). `lighthouserc.json` (mobile emulation, staging або `vite preview` + MSW): `/m/<slug>` і `/suggest` — performance ≥ 85, accessibility ≥ 95, best-practices ≥ 90; `/` — performance ≥ 70 (WebGL), accessibility ≥ 95.
- [ ] **Step 2: Запустити — виправити виявлене — PASS.** Run: `npm run e2e -w web -- e2e/a11y.spec.ts && npm run build -w web && npm run check:bundle -w web && npm run lhci -w web`.
- [ ] **Step 3: Commit** `test(web): add accessibility checks and performance budgets`

---

## Етап 5. Запуск

### Task 31: CI/CD і production-інфраструктура

**Files:**
- Create: `.github/workflows/{backend.yml,web.yml,deploy.yml}`, `infra/docker-compose.prod.yml`, `infra/nginx/edge.conf`, `infra/secrets.example.properties`, `infra/scripts/deploy.sh`

**Interfaces:**
- Consumes: T2–T4, T13, T30.
- Produces:
  - `backend.yml` (PR і push у `main`, шляхи `backend/**`, `contracts/**`): Temurin 21, кеш Gradle, `./gradlew test` (Docker на `ubuntu-latest` є — Testcontainers працює).
  - `web.yml` (шляхи `web/**`, `contracts/**`): Node 24, `npm ci`, `npm run lint -w web`, `npm run type-check -w web`, `npm run test -w web`, `node --test contracts/tests/`, `npm run build -w web`, `npm run check:bundle -w web`, `npx playwright install --with-deps chromium webkit`, `npm run e2e -w web`.
  - `deploy.yml` (push у `main` після успіху обох; `concurrency: deploy`): збірка й публікація `ghcr.io/<org>/nashi-v2-backend:<sha>` і `…/nashi-v2-web:<sha>` (web з build-arg `VITE_API_BASE_URL=/api/v2`, `VITE_MAP_TILES_URL`), SSH (`appleboy/ssh-action`) → `infra/scripts/deploy.sh <sha>`: перевірка файла секретів і ключів (як V1 `deploy.yml`, ключі з T2), `IMAGE_TAG=<sha> docker compose -f docker-compose.prod.yml pull && … up -d`, очікування `/api/v2/health` до 90 с, при невдачі — повернення попереднього тегу (`/opt/nashi-v2/.last-good-tag`).
  - `docker-compose.prod.yml`: `mongo:8` (том, авторизація, лише внутрішня мережа), `backend` (`SPRING_PROFILES_ACTIVE=prod`, `NASHI_SECRETS_FILE=/run/secrets/backend.properties`, том `/etc/nashi-v2/backend.properties:ro`, лише внутрішня мережа + `127.0.0.1:8081`), `web` (`127.0.0.1:4174`); `logging: json-file, max-size 20m, max-file 5`.
  - `edge.conf` (nginx хоста): TLS (certbot), HTTP → HTTPS, `client_max_body_size 60m`, `gzip on` + `brotli on` (якщо модуль) для `application/json` (зокрема `/api/v2/map/pins`), `proxy_set_header X-Forwarded-For $remote_addr` (перезаписує, а не дописує — захист `ClientIp`), загальний захисний ліміт `limit_req zone=api rate=30r/s burst=60 nodelay` на `/api/` (DoS-бекстоп; кількість лайків не обмежує), заголовки безпеки (CSP: `img-src` self + CDN + `upload.wikimedia.org`; `media-src` self + CDN + `*.dzcdn.net` + `audio-ssl.itunes.apple.com` + `upload.wikimedia.org`; `connect-src` self + хост тайлів; `frame-src https://www.youtube-nocookie.com`).
  - `secrets.example.properties`: ключі з T2 без значень + `nashi.migration.v1.*` (закоментовано).

- [ ] **Step 1: Перевірити workflow-и на гілці.** Відкрити PR → обидва workflow зелені.
- [ ] **Step 2: Розгорнути staging** (піддомен `v2-staging.<домен>`): `infra/scripts/deploy.sh <sha>` → `curl -fsS https://v2-staging.<домен>/api/v2/health` → `{"status":"UP"}`; `curl -sI …/api/v2/map/pins | grep -i 'content-encoding: \(gzip\|br\)'` → є.
- [ ] **Step 3: Commit** `chore(ci): add CI, image publishing, SSH deploy with health check and rollback`

### Task 32: Бекапи і моніторинг

**Files:**
- Create: `infra/scripts/backup-mongo.sh`, `infra/scripts/restore-check.sh`, `infra/MONITORING.md`
- Modify: `backend/src/main/resources/application.properties` (`logging.structured.format.console=ecs`, `management.endpoints.web.exposure.include=health`)

**Interfaces:**
- Consumes: T31, T18 (сповіщення про збої задач у Telegram).
- Produces: щоденний `mongodump --archive --gzip` о 02:30 Києва → S3 `backups/mongo/YYYY-MM-DD.archive.gz` (lifecycle 14 днів); `restore-check.sh` відновлює останній бекап у тимчасову базу `nashi_v2_restore` і порівнює кількість документів `artists`, `submissions`, `places`; зовнішня перевірка доступності `/api/v2/health` кожні 5 хв (UptimeRobot або аналог) зі сповіщенням у Telegram-групу.

- [ ] **Step 1: Налаштувати cron на VPS і провести відновлення на staging.** Run: `infra/scripts/backup-mongo.sh && infra/scripts/restore-check.sh` → Expected: `artists: N = N, submissions: M = M, places: K = K`.
- [ ] **Step 2: Commit** `chore(ops): add nightly mongo backups with restore check and structured logs`

### Task 33: Staging, приймання, перемикання з V1

**Files:**
- Create: `infra/RELEASE.md`
- Modify: `web/e2e/*.spec.ts` (тег `@smoke` на 6 сценаріях, що працюють без MSW: карта, сторінка, лайк, фільтр, форма без відправки, 404)

**Interfaces:**
- Consumes: усі задачі.
- Produces: `infra/RELEASE.md` — чек-лист:
  1. Секрети в `/etc/nashi-v2/backend.properties` (нові ключі; бот V2 — **новий** токен BotFather, не бойовий бот V1).
  2. Бот доданий у групу модерації; для команд адміністраторам — `/cmd@bot_name` або вимкнений privacy mode у BotFather (`/setprivacy` → Disable); перевірка `/ping` → «alive».
  3. Деплой staging (T31) → `/wiki_import` (≈ 2–3 год для ≈ 4,3 тис. музикантів; звіт у групі) → `/audio_resolve` (звіт: скільки з аудіо за джерелами).
  4. Міграція V1: dry run → огляд звіту з замовником → реальний запуск → `/stats`.
  5. Smoke на staging: `BASE_URL=https://v2-staging.<домен> npm run e2e -w web -- --grep @smoke`.
  6. Приймання з замовником за матрицею критеріїв (нижче) на реальних пристроях: iPhone (Safari), Android (Chrome), desktop (Chrome, Safari, Firefox).
  7. Тест модерації наживо: дві заявки; «Так» і «Ні» різними адміністраторами; одночасне натискання; кнопка не-адміністратора.
  8. Перемикання: DNS основного домену → V2; V1 лишається на `v1.<домен>` 30 днів у режимі лише читання (`telegrambots.enabled=false` у V1, щоб не було двох ботів на одній групі).
  9. Відкат: DNS назад на V1 (дані V2 не втрачаються; V1 не змінювався).

- [ ] **Step 1: Пройти чек-лист на staging, зафіксувати результати в `infra/RELEASE.md` (дата, хто, результат кожного пункту).**
- [ ] **Step 2: Production-запуск за пунктами 8–9.**
- [ ] **Step 3: Commit** `docs(release): add V2 release checklist and record acceptance results`

---

## Матриця: критерій приймання замовника → задача і тест

| # | Критерій (ТЗ технічної команди) | Задача | Доказ |
|---|---|---|---|
| 1 | V1 проаналізовано, повторно використані технології і компоненти задокументовано | — / T2, T19, T23 | `docs/00-v1-audit.md`; перенесені тести V1 зелені в T19; ADR 0001, 0002 |
| 2 | Працює на мобільному і desktop | T3, T24–T30 | усі E2E на проєктах `mobile` (iPhone 13, WebKit) і `desktop`; T30 a11y, Lighthouse; T33 п. 6 |
| 3 | На карті Україна або визначений початковий регіон | T23, T24 | `opens on all of Ukraine…`, `deep link ?region=LVIV…` |
| 4 | Плавне переміщення і масштабування | T23, T24 | `camera.test.ts`; `drills down region → place → pins` |
| 5 | Кількість музикантів для міста/регіону | T11, T23, T24 | `MapServiceIT.regionsAlways27WithCounts`, `placesAggregateByPlaceId`; `aggregation.test.ts` |
| 6 | Після наближення — піни окремих музикантів | T23, T24 | `levels for REGION_PLACE_PIN…`; E2E `data-level=PIN` |
| 7 | Пін = місце народження за наявності даних | T19, T16, T8 | `personOriginIsBirthPlaceOnly`, `personWithBirthPlaceGetsSettlementPrecision` |
| 8 | Без точних даних — у межах міста походження | T8, T16 | `PinPlacerTest.staysWithinRadius`, `bandUsesFormationAndCityPrecision` |
| 9 | Пояснення геомітки постійно біля карти | T24, T26 | `origin notice stays visible on every level`; `MusicianPage renders all required blocks` |
| 10 | Взаємодія з піном запускає випадковий фрагмент | T20, T21, T25 | `FragmentPickerTest.*`, `engine.test.ts`, `mobileTrigger` таблиця, `e2e/audio.spec.ts` |
| 11 | Окрема сторінка з описом, зображеннями, матеріалами, посиланнями | T10, T26 | `MusicianControllerIT.detailsBySlug`; `renders all required blocks` |
| 12 | Parser Вікіпедії передає дані в систему | T19 | перенесені тести V1 + `WikiMusicianMapperTest`, `SummaryCleanerTest`; dry run |
| 13 | Фільтр за областю | T10, T11, T23, T24 | `filtersByRepeatedRegion`, `regionFilterHas27OptionsWithCounts`, `region filter writes URL…` |
| 14 | Необмежені лайки без реєстрації | T12, T27 | `incrementsAtomicallyUnderConcurrency`, `e2e/likes.spec.ts` |
| 15 | Кожен лайк +1 до загального лічильника | T12, T27 | `addsBatchCount`, `retries rate-limited taps without losing any` (сума == кількість тапів) |
| 16 | Форма приймає дані, посилання, зображення | T14, T28 | `acceptsValidSubmissionAndStoresProcessedImages`, `happy path…` |
| 17 | Заявка надходить у визначену Telegram-групу | T15, T16 | `sendsAlbumThenControlWithButtons`, `gatewayAddsThreadIdWhenConfigured`; T33 п. 7 |
| 18 | Схвалити «Так» або відхилити «Ні» | T16 | `approveCreatesPublishedArtistOnMap`, `nonAdminCannotModerate`, `concurrentApprovalsCreateOneArtist` |
| 19 | Відхилена заявка не публікується | T16 | `rejectPublishesNothing` |
| 20 | Затверджений дизайн підключається без переробки backend-логіки | T3, T29 | поділ `core/`↔`ui/`; T29 Step 6 — порожній `git diff` по `core`, `backend`, `contracts`, поведінкових E2E |

## Відповіді на «Питання до виконавця» (ТЗ технічної команди)

1. **Технічний аудит V1 і повторне використання стеку.** Детально — `docs/00-v1-audit.md`. Коротко: стек V1 (Spring Boot + MongoDB + S3 + Telegram-бот; React + Vite + TanStack Query + MapLibre + supercluster) підходить і зберігається, оновлюються версії (Java 21, Boot 4, Tailwind 4, Vitest). Переносяться з тестами: модуль імпорту Вікіпедії (T19 — найцінніший, ≈ 6 тис. рядків з правилами виключення РФ), `StartupConfigGuard` (T2), логіка повторів Telegram (T15), команди `/hide`/`/show` (T17), індекс кластерів і форматування лічильників (T23), українські підписи карти (T23), утиліти сторінки (T26). Не переносяться: події, радіо, новини, OpenAI (теги, пости, пошук міст через AI — замінено довідником `places`), дизайн Frutiger Aero, синхронний «JSON-дамп» артиста в Telegram, `GeoCoordinateService` (no-op у V1).
2. **Етапи, терміни, вартість.** Розділ «Етапи, оцінка, календар»: ≈ 93 людино-дні (BE 47, FE 31, OPS 6, QA 9), ≈ 11 календарних тижнів командою 1 BE + 1 FE + часткові OPS/QA. Вартість = людино-дні × ставки ролей.
3. **Агрегація пінів і кількостей на різних масштабах.** Три рівні (D-12): області (`zoom < 7`) → населені пункти (`7..10`, близькі пункти зливаються в бульбашку з сумою) → піни (`≥ 10`, щільні групуються supercluster). Клієнт отримує один компактний список пінів (≈ 3,7 тис. ≈ 90 КБ gzip, ETag) і будує індекси в браузері (T23); `/map/regions` і `/map/places` дають координати центрів і лічильники для першого кадру. Піни детерміновано розкидані в межах населеного пункту (T8), тому в Києві вони розходяться при наближенні. Шлях масштабування (> 50 тис. пінів, наступні фази з іншими митцями): серверні кластери за `bbox`+`zoom` або векторні тайли (PMTiles, нічна генерація) — API `/map/pins` тоді замінюється без зміни ядра UI-рівнів.
4. **Механізм випадкових аудіофрагментів.** Бекенд збирає кліпи з джерел за відомими ID (T20: Deezer, Apple/iTunes, Wikimedia Commons, власні завантаження), випадково обирає кліп і старт так, щоб 15 с вмістилися (T21), віддає свіже посилання (Deezer-посилання тимчасові). Клієнт грає один `HTMLAudioElement` з fade (де браузер дозволяє), обробляє заборону автоплею, на desktop — наведення з затримкою 250 мс, на mobile — одна з трьох стратегій, обрана дизайном (T25). Обмеження: покриття (D-27) і **юридичне підтвердження** умов Deezer/Apple до запуску.
5. **Обмеження автоматичного отримання даних з Вікіпедії.** Правові: текст — CC BY-SA 4.0 (показуємо джерело й ліцензію, наш короткий опис — похідний і теж під CC BY-SA), Вікідані — CC0, фото — ліцензія кожного файла (автор і ліцензія під фото). Технічні: лише офіційні API, User-Agent, послідовні запити з паузами, повтори за `Retry-After`; важкі SPARQL падають за 60 с — тому запити дробляться (успадковано з V1); повний імпорт музикантів ≈ 2–3 год. Якість даних (dry run V1, 4349 записів): з місцем в Україні — 3666, з фото — 1579, з жанрами — 894; тож частина сторінок буде без фото і жанрів. Оновлення вже імпортованих — не у фазі 1 (лише додавання нових, як у V1).
6. **Модель модерації через Telegram-групу.** Заявка → альбом фото і картка з кнопками «✅ Так»/«❌ Ні» у групі (за потреби — в окремій темі форуму). Натискати можуть адміністратори групи (або всі учасники — налаштування). Перший клік виграє атомарно, інші бачать «вже оброблено». «Так» → музикант одразу на карті, у фоні — збагачення з Вікіпедії і пошук аудіо; «Ні» → нічого не публікується, заявка й журнал зберігаються, фото видаляються через 30 днів. Помилка відправки в Telegram не губить заявку (повтор кожні 10 хв). Виправлення — `/hide`, `/show`.
7. **API-контракти, які треба погодити з дизайн-командою до затвердження UI** (КТ1, кінець т.1): (а) поля піна `MapPin` — усе, що дизайн хоче показати на піні без додаткових запитів (зараз: ім'я, мініатюра, тип, наявність звуку); (б) склад `MusicianDetails` — блоки сторінки і що робити, коли блок порожній; (в) `Fragment` — тривалість, fade, обов'язкова атрибуція джерела («Прев'ю: Deezer») і посилання «Слухати на …»; (г) `PublicConfig` — пороги зуму і параметри, які дизайн може змінювати без релізу; (ґ) поля і ліміти `SubmissionRequest` та тексти помилок `Problem`; (д) `FilterDefinition` — як рендерити нові типи фільтрів; (е) імена дизайн-токенів, ключі `copy.uk.json`, ID рішень і технічні ID варіантів (`02-contracts.md` розділи 5, 6, 8) — усе це вже зафіксовано в `docs/02-contracts.md`.

## Ризики

| Ризик | Імовірність | Вплив | Мітигація |
|---|---|---|---|
| Умови Deezer/Apple не дозволяють прев'ю в нашому сценарії | середня | високий | SPI провайдерів (T20): джерело вимикається конфігом; лишаються `UPLOAD` і `COMMONS`; юридична перевірка — залежність замовника (`01-decisions.md` I.4) до КТ5 |
| Більшість історичних музикантів без звуку | висока | середній | стан «немає фрагмента» в дизайні (D-27); `/stats` і звіт резолвера; фаза 2 — ручне додавання кліпів адміністратором |
| Накрутка лайків скриптом | висока | низький | вимога замовника (D-30); обмеження частоти запитів і nginx-бекстоп; фаза 2 — аналітика аномалій |
| Spring Boot 4 несумісний з бібліотекою Telegram або springdoc | низька | середній | ADR 0001, fallback 3.5.x; Telegram без Spring-стартера |
| Запізнення handoff студії | середня | середній | каркасний скін і повна логіка готові без нього; КТ4 зсувається, КТ1–КТ3 — ні |
| Бот не бачить команд у групі (privacy mode) | середня | низький | `/cmd@bot_name` або `/setprivacy Disable`; кнопки (callback) працюють завжди; перевірка в T33 п. 2 |
| Тайли провайдера показують кордони, що не відповідають позиції України | середня | високий | усі `boundary`-шари тайлів приховані (T23), кордони — з власного GeoJSON з Кримом |
| Wikidata недоступна/повільна під час імпорту | середня | низький | успадковані з V1 поділ запитів і зупинка після 12 невдач поспіль; повтор `/wiki_import` |
| Telegram не завантажує фото за URL (приватний бакет) | низька | середній | публічне читання лише префіксів `artists/` і `submissions/`; або presigned URL на 1 год (перемикач у `SubmissionNotifier`) |
| Пін у великому місті потрапляє на воду | середня | низький | прийнятно у фазі 1 (`02-contracts.md` розділ 4); фаза 2 — маска суші для 20 найбільших міст |

## Поза цим планом (фаза 2)

Адмін-панель і редагування музиканта; сповіщення заявникам; оновлення вже імпортованих з Вікіпедії; прев'ю посилань для соцмереж (Open Graph для `/m/:slug`); нові фільтри (тип, жанр, період, «є фрагмент») — без зміни UI-механізму завдяки `GET /filters`; пошук аудіо за іменем з ручним підтвердженням; маска суші для пінів; англійська мова; інші види мистецтва (`disciplines`).
