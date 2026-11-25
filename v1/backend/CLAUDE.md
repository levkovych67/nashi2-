# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Nashi (Наші)** is a digital cultural heritage platform: a geo-cultural atlas of Ukraine that connects Ukrainian geography with artists and events. This is a Spring Boot backend application that provides REST APIs for a map-based frontend.

## Development Commands

### Local Development Setup

Start MongoDB:
```bash
docker compose up -d
```

Run the application with the local profile (local MongoDB; no Telegram, S3 or OpenAI):
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
Never start it without `SPRING_PROFILES_ACTIVE=local` on a dev machine (see Configuration & Secrets).

Run tests (plain JUnit 5 + Mockito; no Spring context, no database):
```bash
./gradlew test
```

Build the project:
```bash
./gradlew build
```

### API Documentation

- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- API contract: `swagger.json` (source of truth for frontend integration)

## Architecture & Key Concepts

### Technology Stack

- **Spring Boot 3.4.1** with Java 17
- **MongoDB** (Spring Data MongoDB with geospatial indexing)
- **Spring AI** (OpenAI integration for AI-generated tags and coordinates)
- **AWS S3** (file storage for images and audio tracks)
- **Telegram Bot API** (admin notifications and approvals)
- **MapStruct** (DTO mapping)
- **Lombok** (boilerplate reduction)

### Core Domain Models

**Artist** (`com.nashi.domain.Artist`):
- Cultural artists/bands with biography, press release, timeline, media files
- Geospatial location with collision avoidance (see below)
- Status-based moderation workflow (PENDING → APPROVED/REJECTED)
- AI-generated tags (music genres, secondary tags)
- Embedded members and social links

**Event** (`com.nashi.domain.Event`):
- Cultural events with venue, datetime, tickets
- Geospatial location for map display
- Status-based moderation workflow

Both use GeoJSON Point with 2dsphere indexing for efficient map queries.

### Geospatial Coordinate Collision System

**Critical Feature**: The system automatically prevents map pin overlaps when multiple artists share the same location.

- When artist coordinates collide (within 5m), new artists are offset using a deterministic spiral pattern
- Minimum spacing: 25 meters between pins for visual clarity
- Spiral search: 8 positions per ring at 30m, 45m, 60m... up to 200m radius
- Implementation: `GeoCoordinateService` / `GeoCoordinateServiceImpl`
- See `COORDINATE_COLLISION_GUIDE.md` for full details

**Key Point**: When working with artist creation, ALWAYS use `geoCoordinateService.adjustCoordinatesIfCollision()` before saving location data.

### Suggestion Processing

Artist and event suggestions are processed synchronously, inside the request (no `@Async` method exists):

- `POST /api/v1/artists`: `ArtistServiceImpl.createSuggestion` picks a free slug, uploads the files to S3, asks the AI
  for tags and saves the artist as `PENDING`; the controller then sends it to the admin chat and answers
  `201 CREATED` with a text body.
- `POST /api/v1/events/suggest`: `EventService.suggestEvent` asks the AI for coordinates, uploads the image and saves
  the event as `PENDING`, then sends it to the admin chat; the answer is `200 OK` with no body.
- The response comes only after the uploads and the AI call, so it can take several seconds.
- `AsyncConfig` (`@EnableAsync`, 5 core threads, 10 max, 100 queue capacity) is still there, unused.
  `ASYNC_IMPLEMENTATION_GUIDE.md` describes an earlier asynchronous design, not the current code.

### Error Handling System

Comprehensive error handling with consistent API responses:

- Custom exceptions: `BusinessException` (base), `ResourceNotFoundException`, `ValidationException`, `ExternalServiceException`, `FileUploadException`
- Global handler: `GlobalExceptionHandler` catches all exceptions
- Standard response format with timestamp, status, error code, message, path
- See `ERROR_HANDLING_GUIDE.md` for usage patterns

**Always use custom exceptions, never `RuntimeException` or `IllegalArgumentException` directly.**

### AI Integration

**AI-Powered Features**:
1. **Tag Generation** (`AiRequestService.requestAiTags`): Generates music tags and secondary tags from artist biography/style
2. **Coordinate Extraction** (`AiRequestService.requestEventCoordinates`): Extracts GPS coordinates from event venue descriptions using structured output

**Important**: AI calls are long-running (5-10s) and, in suggestions, run inside the request (see Suggestion Processing).
Keep a timeout on new AI calls.

### File Upload & S3 Storage

- Validation: `DataValidationService` checks file types, sizes (100MB max)
- Storage: `S3Service` handles uploads to AWS S3
- Supported: Images (JPG, PNG, WEBP), Audio (MP3, WAV, FLAC, AAC, OGG)
- Keys stored in domain models (e.g., `imageKeys`, `demoTrackKeys`)
- Public URLs generated via `S3Service.getPublicUrl(key)`

### Telegram Integration

Admin workflow uses Telegram Bot:
- Suggestions sent to admin chat for approval
- Inline keyboards for approve/reject actions; they act on `PENDING` submissions only. An old button for anything
  else changes nothing and the bot answers «Заявку вже оброблено (статус: …)» (`AlreadyProcessedException`)
- Callback handlers in `TelegramBot` and approval services
- News posts published to channel when approved
- The bot listens to the admin chat only (`telegram.admin-chat-id`): callbacks and texts from any other chat are
  logged and ignored, with no reply.
- Admin commands: `/wiki_import`; `/hide <slug або Q-id>` sets an `APPROVED` artist to `HIDDEN` (the document stays,
  so the import never adds it again); `/show <slug або Q-id>` sets a `HIDDEN` artist back to `APPROVED`
  (`ArtistVisibilityService`). `/hide` changes nothing for a `PENDING` or `REJECTED` one (a pending suggestion is
  answered «Це заявка на модерації — відхиліть її кнопкою в чаті.»), so `/show` never publishes an unmoderated one.

**Configuration**: `telegram.bot-token`, `telegram.admin-chat-id` and `telegram.channel-id` come from the secrets file.
`telegram.enabled=false` turns every outgoing send (admin text, photos, media groups, audio, channel posts) into a
logged no-op. `telegrambots.enabled=false` stops the starter's long polling (its `TelegramBotStarterConfiguration` is
`@ConditionalOnProperty(prefix = "telegrambots", name = "enabled", havingValue = "true", matchIfMissing = true)`).

### MongoDB Indexes

Geospatial queries require proper indexing:
- Spring Data's auto-index creation is off, so `MongoIndexConfig` creates the `artists` indexes on startup, each on
  its own (a failure is a warning): `location_2dsphere`, `wikidataId_unique`, `slug_unique`, `status_1_region_1`,
  `categories_1`
- `@Indexed` and `@GeoSpatialIndexed` annotations in domain models are documentation only
- Callbacks: `ArtistBeforeConvertCallback`, `EventBeforeConvertCallback` generate slugs and geohashes. The artist
  callback fills only a blank slug; because of `slug_unique`, suggestions (`createSuggestion`: name, then `-2`, `-3`, …)
  and the Wikipedia import (name, then `-q<id>`) pick a free slug before saving

### API Structure

RESTful endpoints organized by feature (public artist and event endpoints return `APPROVED` ones only; any other
status is a 404):
- `/api/v1/map/*` - Map pins with clustering support
- `/api/v1/artists/*` - Artist CRUD and search
- `/api/v1/events/*` - Event CRUD and suggestions
- `/api/v1/radio/*` - Regional music mix generation
- `/api/v1/metadata/*` - Platform stats and news
- `/api/v1/lookup/*` - Region and city lookups

**CORS**: `CorsConfig` allows the origins in `nashi.cors.allowed-origins` (a comma list of origins or origin
patterns; `*` allows any), never with credentials.

**Health**: `/api/v1/health` answers 503 when MongoDB is down, without the exception text.

## Wikipedia Import (`com.nashi.wiki`)

Imports every Ukrainian musician, band, visual artist and writer with a uk.wikipedia article who is not in the
database yet. Spec: `docs/superpowers/specs/2026-11-04-wikipedia-import-design.md`.

- **Who**: people who are Ukrainian citizens or were born in Ukraine, with an occupation (P106) in one of three trees;
  ensembles (Q2088357 incl. subclasses) from Ukraine. Never imported (owner decisions, 2026-11-08 and -09): citizens of
  Russia, and holders of a state honour of the Russian Federation. An award (P166) counts when Q159 is the only country
  it states (P17) or its P131, and it is a state honour (`StateHonours`): its P31 is in the subclass tree of order,
  medal, state decoration or title of honour (minus honorary degrees, honorary citizenships, honorifics, titles of
  federal subjects Q4375517), not in the tree of departmental award Q6866472, not conferred by the Russian Orthodox
  Church; a degree or class of an award is judged by the award it is part of (P361). Soviet titles of the RSFSR (P17 =
  USSR, P131 = RSFSR), Soviet, imperial and multi-country awards, private and union prizes and honorary diplomas do not
  count. The admin report counts them («відсіяно через зв'язок з РФ»).
  - Trees (`OccupationCategories`): musician / singer / composer; visual artist minus filmmaker, stand-up comedian,
    make-up artist and cartographer; writer minus columnist and correspondent. An occupation in two trees counts for
    the first of musician, visual artist, writer.
- **What**: kinds (`categories`, from the occupation trees in P106 order; ensembles are `BAND`; else the kind of the
  query that found the item), birth/death dates, birth place label (also abroad), the map point and its
  `locationBasis`, genres (P136 then P135, up to 5), works (`WikiCollector.works`; paintings without an image are
  dropped) and links (`WikiLinkTable`, templates from P1630).
- **How**: a query by occupation root with all subclasses (`wdt:P106/wdt:P279*`) times out on the public endpoint, so
  `WikiCollector` fetches each tree (`?occ wdt:P279* wd:ROOT`, once per run) and `CandidateLister` asks for people a
  chunk of occupations at a time (`VALUES ?occ { … }`), per rule. A chunk that looks too heavy (answer cut off, HTTP
  500/502/503/504, client timeout) is halved and asked again down to one occupation; one that still fails is skipped
  and counted in the admin report. Anything else (429/403 after retries, other 4xx, connection failure) and 12 failed
  requests in a row stop the run instead, so an outage never multiplies requests. Each candidate keeps the group
  (`CandidateGroup`) of the first query that found it. Listing takes about 90 SPARQL queries and 15 minutes; details
  (entities, places, awards, intros, photos, works) follow in chunks of 200. Measured on 2026-11-08: the local seed
  of 600 candidates took 80 minutes, the 160-candidate sample dry run 15; a full run takes many hours.
- **Duplicates**: skipped by `wikidataId`, by `wikiTitle`, and by normalized name against artists added through the
  form (not rejected). Unique sparse index `wikidataId_unique` is created in `MongoIndexConfig`.
- **Imported artists** are `APPROVED`, `source = WIKIPEDIA`; no moderation, no channel posts. Artists without a place
  in Ukraine are saved without `location` and stay off the map.
- **Runs**: monthly on the 1st at 04:00 Kyiv time (`nashi.wiki-import.cron`, `nashi.wiki-import.enabled`, defaults in
  code) and on `/wiki_import` sent in the admin chat. One run at a time; one report to the admin chat per run.
- **Dry run** (no Spring, no database, no Telegram): `./gradlew wikiDryRun` (or `-Plimit=50`: the first 50 of each
  candidate group) writes `build/wiki-dry-run.json` and prints counts per kind, location basis, dates, works, photos,
  links per platform and Russia-linked exclusions.
- **Tests**: plain JUnit 5 only. Never `@SpringBootTest` or Mongo slice tests (see Testing Considerations).
  Fixtures in `src/test/resources/wiki/` are real API responses.

## Important Implementation Patterns

### Multipart Form Handling

Artist/event creation uses multipart/form-data:
```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<String> suggestArtist(
    @RequestPart("data") @Valid ArtistCreateRequestDTO dto,
    @RequestPart(value = "images", required = false) List<MultipartFile> images,
    @RequestPart(value = "demoTracks", required = false) List<MultipartFile> demoTracks
)
```

**JSON data passed as `data` part, files as separate parts.**

### Pagination

Uses Spring Data's `Pageable`:
```java
@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
```

Returns `Page<DTO>` with metadata for frontend pagination.

### DTO Mapping

- Request DTOs: Incoming data with validation annotations
- View DTOs: Outgoing data with URLs resolved (S3 public URLs)
- Use MapStruct for complex mappings (e.g., `EventMapper`)
- Manual mapping for simple cases (builder pattern in services)

### Region Enum

`Region` enum represents the Autonomous Republic of Crimea and the 24 oblasts (no separate value for the cities of
Kyiv or Sevastopol; `swagger.json` lists exactly these, checked by `SwaggerContractTest`):
- Used throughout for filtering and categorization
- Frontend should use `/api/v1/lookup/regions` for human-readable labels
- Never hardcode region values in frontend

## Configuration & Secrets

No secrets live in this repo. `application.properties` holds only non-secret settings and imports the secrets file:
`spring.config.import=optional:file:${NASHI_SECRETS_FILE:/etc/nashi/nashi-backend.properties}`.

- **Production** reads its secrets from that file: `/etc/nashi/nashi-backend.properties` by default, or the path in
  `NASHI_SECRETS_FILE`. Keys: `spring.data.mongodb.uri`, `spring.ai.openai.api-key`, `aws.s3.access-key-id`,
  `aws.s3.secret-access-key`, `telegram.bot-token`, `telegram.admin-chat-id`, `telegram.channel-id`. Environment
  variables (`SPRING_DATA_MONGODB_URI`, ...) override the file.
- **Local** runs use `SPRING_PROFILES_ACTIVE=local` (`application-local.properties`, tracked, no secrets): MongoDB on
  localhost, `telegram.enabled=false` and `telegrambots.enabled=false` (long polling would steal production updates),
  `local-disabled` placeholders for the OpenAI and S3 keys, the monthly Wikipedia import off, and seeding of an empty
  database at startup (`LocalSeedRunner`: `nashi.wiki-import.on-startup=true`, at most
  `nashi.wiki-import.limit-per-category=150` new candidates per group).
- **`StartupConfigGuard`** (an `EnvironmentPostProcessor` registered in `META-INF/spring.factories`) runs after config
  data is loaded and stops startup:
  - without the `local` profile, when any of the seven keys above is missing;
  - with the `local` profile, when `spring.data.mongodb.uri` is not `mongodb://` on `localhost`, `127.0.0.1`, `mongo`
    or `host.docker.internal` (`mongodb+srv` is never local), when `telegram.enabled` or `telegrambots.enabled` is not
    `false`, or when an OpenAI or S3 key (`spring.ai.openai.api-key`, `aws.s3.access-key-id`,
    `aws.s3.secret-access-key`) is set to anything else: it must be blank or `local-disabled`.

  Its messages name the key and the rule, never the value.
- Never print, log or commit secret values. `/aws/` and `/config/` at the repo root are git-ignored.

## Testing Considerations

- Tests are plain JUnit 5 + Mockito; `MockEnvironment` (spring-test) is fine. Never `@SpringBootTest`, `@DataMongoTest`
  or other Spring context or slice tests: a context would run `StartupConfigGuard` and, given secrets, reach live
  services.

## Frontend Integration Notes

The frontend (separate repo) should:
- Use `swagger.json` to generate TypeScript types
- Expect `201 CREATED` from `POST /api/v1/artists` and `200 OK` from `POST /api/v1/events/suggest` (both synchronous)
- Parse error responses with `errorCode` from `details` field
- Use MapLibre GL for geospatial visualization with clustering
- Implement region/tag/style filters for map pins
- See `AI_FRONTEND_INSTRUCTIONS.md` and `INSTRUCTION.md` for detailed frontend implementation guidance

## Code Style & Patterns

- Use Lombok annotations (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`)
- Constructor injection via `@RequiredArgsConstructor` (never field injection)
- `@Slf4j` for logging
- `@Transactional` on service methods that modify data
- Validation: `@Valid` on controller params, manual validation in services
- Always log important operations (AI calls, uploads, async processing) with `[ASYNC]` prefix for background tasks
