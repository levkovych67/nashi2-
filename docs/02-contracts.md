# «Наші V2» — спільні контракти

Дата: 2026-11-17. Нормативний документ для обох команд: що б не робила одна команда, вона не порушує цей файл без
узгодження з іншою. Зміна контракту = зміна цього файлу + `contracts/openapi.yaml` в одному коміті.

Зміст:
1. Загальні правила API
2. Ендпойнти і схеми
3. Модель даних (MongoDB)
4. Розміщення піна — формула
5. Дизайн-токени
6. Каталог рішень студії (ID рішень і варіантів)
7. Пакет handoff (студія → продукт)
8. Тексти інтерфейсу (`copy.uk.json`)
9. Тека `contracts/`: фікстури і геодані

---

## 1. Загальні правила API

- База: `/api/v2`. JSON, `camelCase`, UTF-8. Часткові дати — рядок `YYYY`, `YYYY-MM` або `YYYY-MM-DD` (як у V1).
  Мітки часу — ISO 8601 UTC (`2026-11-17T10:15:00Z`). Координати в тілах — `lat`, `lon` (числа);
  у масивах `center`/`bbox` — порядок GeoJSON: `[lon, lat]`, `[west, south, east, north]`.
- Публічні ендпойнти без автентифікації. CORS — лише origin-и з `nashi.cors.allowed-origins`, без credentials.
- Помилки — RFC 9457, `Content-Type: application/problem+json`:
  ```json
  {
    "type": "https://nashi.example/problems/validation",
    "title": "Некоректні дані",
    "status": 400,
    "detail": "Поле summary коротше за 30 символів",
    "instance": "/api/v2/submissions",
    "code": "VALIDATION_FAILED",
    "errors": [{ "field": "summary", "message": "Мінімум 30 символів" }]
  }
  ```
  Коди `code`: `VALIDATION_FAILED` (400), `NOT_FOUND` (404), `PAYLOAD_TOO_LARGE` (413),
  `UNSUPPORTED_MEDIA_TYPE` (415), `RATE_LIMITED` (429, заголовок `Retry-After` у секундах), `INTERNAL` (500),
  `UNAVAILABLE` (503).
- Тексти `title`, `detail`, `errors[].message` — українською, безпечні для показу користувачу.
- Кешування: `GET /map/*` і `GET /filters` віддають `ETag` і `Cache-Control: public, max-age=60`;
  `If-None-Match` → `304`.

## 2. Ендпойнти і схеми

### Перелічення

| Тип | Значення |
|---|---|
| `Region` | `CRIMEA`, `VINNYTSIA`, `VOLYN`, `DNIPROPETROVSK`, `DONETSK`, `ZHYTOMYR`, `ZAKARPATTIA`, `ZAPORIZHZHIA`, `IVANO_FRANKIVSK`, `KYIV`, `KIROVOHRAD`, `LUHANSK`, `LVIV`, `MYKOLAIV`, `ODESA`, `POLTAVA`, `RIVNE`, `SUMY`, `TERNOPIL`, `KHARKIV`, `KHERSON`, `KHMELNYTSKYI`, `CHERKASY`, `CHERNIVTSI`, `CHERNIHIV`, `KYIV_CITY`, `SEVASTOPOL_CITY` (27) |
| `MusicianKind` | `PERSON`, `BAND` |
| `OriginBasis` | `BIRTH` (місце народження), `FORMATION` (місце заснування гурту), `ORIGIN` (місто походження зі слів заявника) |
| `PinPrecision` | `EXACT`, `SETTLEMENT`, `CITY` |
| `AudioSource` | `UPLOAD`, `DEEZER`, `ITUNES`, `COMMONS` |
| `LinkPlatform` | `SPOTIFY`, `APPLE_MUSIC`, `DEEZER`, `YOUTUBE_MUSIC`, `SOUNDCLOUD`, `BANDCAMP`, `YOUTUBE`, `WIKIPEDIA`, `WEBSITE`, `INSTAGRAM`, `FACEBOOK`, `TIKTOK`, `TELEGRAM`, `X`, `OTHER` |
| `LinkGroup` | `MUSIC` (Spotify, Apple Music, Deezer, YouTube Music, SoundCloud, Bandcamp), `VIDEO` (YouTube), `REFERENCE` (Wikipedia, Website), `SOCIAL` (решта) |
| `PlaceType` | `CITY`, `TOWN` (селище, смт), `VILLAGE`, `OTHER` |
| `WorkType` | `ALBUM`, `SINGLE`, `SONG`, `COMPOSITION`, `OTHER` |

Назви регіонів (`name` у відповідях): «Автономна Республіка Крим», «Вінницька область», …, «Київ», «Севастополь»
(для областей — «<прикметник> область», як у V1 `Region.uaName`).

### 2.1 `GET /api/v2/map/pins` — усі опубліковані музиканти з пінами

Компактно, бо завантажується на телефоні одразу (≈3,7 тис. записів ≈ 90 КБ gzip).

```json
{
  "version": "7f3a9c1e",
  "pins": [
    {
      "id": "6650f0c2a1b2c3d4e5f60718",
      "slug": "okean-elzy",
      "name": "Океан Ельзи",
      "kind": "BAND",
      "lat": 49.8412,
      "lon": 24.0297,
      "region": "LVIV",
      "placeId": "Q36036",
      "thumbUrl": "https://cdn.nashi.example/artists/6650…/thumb.jpg",
      "hasAudio": true
    }
  ]
}
```
- `thumbUrl` може бути відсутнім (тоді UI показує ініціал). `version` = `ETag` без лапок.
- Музиканти без піна (місце не в Україні) сюди не входять.

### 2.2 `GET /api/v2/map/regions` — 27 регіонів з кількостями

```json
[
  { "code": "LVIV", "name": "Львівська область", "center": [24.0, 49.7], "bbox": [22.6, 48.7, 25.5, 50.6], "count": 487 }
]
```
Завжди 27 елементів, `count` може бути 0. `center` — візуальний центр (polylabel) полігону.

### 2.3 `GET /api/v2/map/places` — населені пункти, з яких є хоча б один музикант

```json
[
  { "placeId": "Q36036", "name": "Львів", "region": "LVIV", "type": "CITY", "lat": 49.8419, "lon": 24.0315, "count": 273 }
]
```

### 2.4 `GET /api/v2/filters` — опис фільтрів (розширювана структура критеріїв)

```json
[
  {
    "key": "region",
    "label": "Область",
    "type": "MULTI_SELECT",
    "options": [{ "value": "LVIV", "label": "Львівська область", "count": 487 }]
  }
]
```
`type`: `MULTI_SELECT` | `SINGLE_SELECT` | `RANGE` | `BOOLEAN`. У фазі 1 — лише `region`. Нові критерії (тип,
жанр, період, «є фрагмент») додаються як нові елементи масиву; UI фільтрів рендерить їх за `type`, без переробки.
Значення фільтра в URL продукту: `?<key>=<v1>,<v2>`.

### 2.5 `GET /api/v2/musicians` — список

Параметри: `region` (повторюваний: `region=LVIV&region=KYIV_CITY`), `q` (пошук за ім'ям, ≥ 2 символи, без
урахування регістру, апострофів і наголосів), `sort` = `NAME` (за замовчуванням) | `LIKES` | `RECENT`,
`page` (з 0), `size` (1..100, за замовчуванням 20).

```json
{
  "items": [
    { "id": "…", "slug": "okean-elzy", "name": "Океан Ельзи", "kind": "BAND", "thumbUrl": "…",
      "placeName": "Львів", "region": "LVIV", "likesCount": 1234, "hasAudio": true }
  ],
  "page": 0, "size": 20, "totalItems": 487, "totalPages": 25
}
```

`placeName` і `region` = `null`, якщо місце походження поза Україною.

### 2.6 `GET /api/v2/musicians/{slug}` — сторінка музиканта

404 (`NOT_FOUND`), якщо не опублікований.

```json
{
  "id": "…",
  "slug": "solomiia-krushelnytska",
  "name": "Соломія Крушельницька",
  "kind": "PERSON",
  "tagline": "українська оперна співачка",
  "summary": "Соломія Амвросіївна Крушельницька — українська оперна співачка (сопрано), педагогиня.",
  "description": "… повний вступ статті, до 2000 символів …",
  "textSource": {
    "type": "WIKIPEDIA",
    "title": "Крушельницька Соломія Амвросіївна",
    "url": "https://uk.wikipedia.org/wiki/…",
    "license": "CC BY-SA 4.0",
    "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/deed.uk"
  },
  "images": [
    {
      "url": "https://upload.wikimedia.org/…/800px-….jpg",
      "thumbUrl": "https://upload.wikimedia.org/…/400px-….jpg",
      "alt": "Соломія Крушельницька",
      "credit": { "author": "…", "license": "Public domain", "licenseUrl": null, "sourceUrl": "https://commons.wikimedia.org/wiki/File:…" }
    }
  ],
  "origin": {
    "placeId": "Q4251478", "placeName": "Білявинці", "region": "TERNOPIL", "regionName": "Тернопільська область",
    "basis": "BIRTH", "precision": "SETTLEMENT", "lat": 49.07, "lon": 25.32
  },
  "dates": { "birth": "1872-09-23", "death": "1952-11-16", "activeFrom": null, "activeTo": null },
  "genres": ["опера"],
  "materials": {
    "clips": [{ "clipId": "c_01", "title": "Аріозо", "source": "COMMONS", "durationSec": 184, "externalUrl": "https://commons.wikimedia.org/wiki/File:…" }],
    "works": [{ "title": "…", "year": 1995, "type": "ALBUM", "url": "https://…" }],
    "videos": [{ "youtubeId": "dQw4w9WgXcQ", "title": "…" }]
  },
  "links": [{ "platform": "SPOTIFY", "group": "MUSIC", "url": "https://open.spotify.com/artist/…" }],
  "youtubeChannelUrl": "https://www.youtube.com/channel/…",
  "likesCount": 1234,
  "hasAudio": true,
  "updatedAt": "2026-11-17T10:15:00Z"
}
```
- `textSource` = `null` для тексту від заявника. `credit` = `null` для наших завантажень.
- `origin` = `null`, якщо музикант не на карті (місце поза Україною) — сторінка все одно відкривається.
- `origin.lat/lon` — координати **піна** (те, що на карті). `images` може бути порожнім.
- `videos` може бути порожнім (у фазі 1 заповнюється лише якщо налаштовано ключ YouTube Data API).

### 2.7 `GET /api/v2/musicians/{id}/fragment` — випадковий фрагмент

Параметр `exclude` (необов'язковий) — `clipId`, який не повторювати. `204 No Content`, якщо аудіо немає.

```json
{
  "clipId": "c_01",
  "url": "https://cdnt-preview.dzcdn.net/…mp3?hdnea=…",
  "startSec": 7.5,
  "durationSec": 15,
  "fadeInMs": 300,
  "fadeOutMs": 600,
  "title": "Обійми",
  "source": "DEEZER",
  "attribution": "Прев'ю: Deezer",
  "externalUrl": "https://www.deezer.com/track/…",
  "expiresAt": "2026-11-17T10:30:00Z"
}
```
- `url` грається напряму браузером. Для `DEEZER` посилання тимчасове (`expiresAt`): клієнт не кешує його довше.
- Інваріант: `0 ≤ startSec` і `startSec + durationSec ≤ тривалість кліпу`; якщо кліп коротший за фрагмент —
  `startSec = 0`, `durationSec` = тривалість кліпу.

### 2.8 `POST /api/v2/musicians/{id}/likes` — лайки

Тіло: `{ "count": 3 }` (ціле 1..50). Відповідь `200`: `{ "likesCount": 1237 }`. Помилки: 400, 404, 429.

### 2.9 `GET /api/v2/places/search` — автодоповнення населених пунктів

Параметри: `q` (≥ 2 символи, префікс слова, без урахування регістру й апострофів), `region` (необов'язковий),
`limit` (1..20, за замовчуванням 10). Сортування — за населенням спадно.

```json
[
  { "placeId": "Q465104", "name": "Броди", "type": "CITY", "region": "LVIV", "regionName": "Львівська область", "lat": 50.078055555, "lon": 25.154166666 }
]
```

### 2.10 `POST /api/v2/submissions` — пропозиція музиканта

`multipart/form-data`: частина `data` (`application/json`, схема нижче), частини `images` (1..5 файлів:
`image/jpeg`, `image/png`, `image/webp`; ≤ 10 МБ кожен; ≤ 55 МБ увесь запит).

```json
{
  "name": "Назва або ім'я",
  "kind": "BAND",
  "originPlaceId": "Q36036",
  "birthPlaceId": null,
  "summary": "Від 30 до 1000 символів",
  "genres": ["інді", "фолк"],
  "links": ["https://open.spotify.com/artist/…", "https://soundcloud.com/…"],
  "youtubeChannelUrl": "https://www.youtube.com/@…",
  "wikipediaUrl": null,
  "contact": "telegram @nick або email",
  "consent": true,
  "faxNumber": ""
}
```
Правила: `name` 2..100; `kind` обов'язковий; `originPlaceId` — існуючий `placeId` (з 2.9); `birthPlaceId` — існуючий
або `null` (лише для `PERSON`); `summary` 30..1000; `genres` ≤ 5, кожен 2..40; `links` 1..10 абсолютних `http(s)` URL,
щонайменше одне з групи `MUSIC` або `VIDEO` (платформу визначає сервер), разом із `youtubeChannelUrl` теж зараховується;
`youtubeChannelUrl` — домен `youtube.com`/`youtu.be`; `wikipediaUrl` — домен `uk.wikipedia.org` або `en.wikipedia.org`;
`contact` ≤ 200; `consent` = `true`; `faxNumber` — пастка для ботів: має бути порожнім, інакше сервер відповідає
`202`, але заявку мовчки відкидає.

Відповідь `202`: `{ "id": "…", "status": "PENDING" }`. Помилки: 400 `VALIDATION_FAILED` (з `errors[]` по полях,
`field` для файлів — `images[2]`), 413, 415, 429.

### 2.11 `GET /api/v2/config/public` — налаштування, від яких залежить UI

```json
{
  "fragmentSeconds": 15,
  "map": { "regionMaxZoom": 7, "placeMaxZoom": 10, "minZoom": 5, "maxZoom": 17 },
  "likes": { "batchMax": 50, "flushMs": 600 },
  "submission": { "maxImages": 5, "maxImageMb": 10, "summaryMin": 30, "summaryMax": 1000 },
  "turnstileSiteKey": null
}
```

### 2.12 `GET /api/v2/health`

`200 {"status":"UP"}` або `503 {"status":"DOWN"}` (MongoDB недоступна; без тексту винятку).

---

## 3. Модель даних (MongoDB, база `nashi_v2`)

Внутрішня модель бекенду; фронтенд її не бачить. Дизайн-команді — довідково: які дані взагалі існують.

### `artists`
| Поле | Тип | Примітка |
|---|---|---|
| `_id` | ObjectId | |
| `slug` | string | унікальний; транслітерація імені (КМУ 2010), при конфлікті `-2`, `-3`… |
| `name`, `nameNormalized` | string | нормалізація: нижній регістр, без апострофів `'’ʼ`, лапок, наголосів, зайвих пробілів |
| `disciplines` | string[] | фаза 1: `["MUSIC"]` |
| `kind` | `MusicianKind` | |
| `status` | `PUBLISHED` \| `HIDDEN` | |
| `source` | `WIKIPEDIA` \| `SUBMISSION` \| `V1_MIGRATION` | |
| `wikidataId`, `wikiTitle` | string? | `wikidataId` — унікальний розріджений індекс |
| `tagline`, `summary`, `description` | string? | |
| `textSource` | object? | як у 2.6 |
| `images` | `{key?, url?, thumbKey?, thumbUrl?, alt, credit?}[]` | `key` — S3, `url` — зовнішній (Commons) |
| `origin` | `{placeId, placeName, region, basis, precision, center: GeoJSON Point}` | `center` — координати населеного пункту |
| `pin` | GeoJSON Point? | індекс `2dsphere`; `null` = не на карті |
| `dates` | `{birth?, death?, activeFrom?, activeTo?}` | |
| `genres` | string[] | українською |
| `links` | `{platform, url}[]` | |
| `youtubeChannelUrl` | string? | |
| `works` | `{title, year?, type, url?}[]` | |
| `videos` | `{youtubeId, title}[]` | |
| `audio` | `{clipId, source, externalId?, key?, url?, title, durationSec, attribution, externalUrl?}[]` | |
| `audioCheckedAt` | Instant? | коли востаннє шукали аудіо |
| `likesCount` | long | лише `$inc` |
| `submissionId` | string? | |
| `createdAt`, `updatedAt`, `publishedAt` | Instant | |

Індекси: `slug_unique`, `wikidataId_unique_sparse`, `legacyV1Id_unique_sparse` (внутрішній, для міграції з V1), `pin_2dsphere`, `status_1_origin.region_1`,
`status_1_nameNormalized_1`, `status_1_likesCount_-1`, `status_1_origin.placeId_1`.

### `places` — довідник населених пунктів України
`_id` = Wikidata QID; `name`, `nameNormalized`, `type` (`PlaceType`), `region`, `point` (GeoJSON), `areaKm2?`,
`population?`. Індекси: `nameNormalized_1_population_-1`, `region_1`, `point_2dsphere`. Наповнюється сидом із
Вікіданих (≥ 25 тис. пунктів).

### `submissions`
`_id`, `status` (`PENDING` | `APPROVED` | `REJECTED` | `DISCARDED` — спрацювала пастка), усі поля з 2.10 (крім
`faxNumber` і `consent` → `consentAt`), `imageKeys[]`, `ipHash` (HMAC-SHA-256 з секретною сіллю), `duplicateOfArtistId?`,
`telegram` (`chatId`, `threadId?`, `albumMessageIds[]`, `controlMessageId`), `moderationLog[]`
(`{action: SENT|APPROVED|REJECTED|DUPLICATE_WARNING|FAILED, by?: {telegramUserId, username, displayName}, at, note?}`),
`artistId?`, `createdAt`, `decidedAt?`.

### `jobs_runs` (журнал фонових задач)
`_id`, `job` (`WIKI_IMPORT` | `AUDIO_RESOLVE` | `SUBMISSION_CLEANUP` | `V1_MIGRATION`), `startedAt`, `finishedAt`,
`status`, `report` (довільний документ).

---

## 4. Розміщення піна — формула

Вхід: `artistId` (hex ObjectId), населений пункт `place` (`placeId`, `lat0`, `lon0`, `type`, `areaKm2?`), точність.

```
R (метри) =
  0                                               якщо precision = EXACT
  clamp(0.6 · sqrt(areaKm2 / π) · 1000, 150, 8000) якщо areaKm2 відома
  CITY → 3000, TOWN → 1200, VILLAGE → 400, OTHER → 400   інакше

h  = SHA-256(UTF-8(artistId + ":" + placeId))
u1 = беззнакове big-endian ціле з байтів h[0..3] / 2^32
u2 = беззнакове big-endian ціле з байтів h[4..7] / 2^32
r  = R · sqrt(u1)          (рівномірно по площі кола)
θ  = 2π · u2
lat = lat0 + (r · cos θ) / 111320
lon = lon0 + (r · sin θ) / (111320 · cos(lat0 · π / 180))
```
Для `EXACT` пін = точні координати, які ввів адміністратор. Пін перераховується лише при зміні `origin`.
Відомі обмеження: у місті з річкою (Київ) пін може опинитися на воді — прийнятно у фазі 1; у фазі 2 — обрізка
маскою суші для 20 найбільших міст.

---

## 5. Дизайн-токени

Формат — W3C Design Tokens Community Group (`$value`, `$type`). Студія заповнює **всі** токени нижче для кожного
напряму айдентики; продукт використовує **лише** їх (CSS-змінна = `--n-` + шлях через дефіс, напр.
`color.surface-raised` → `--n-color-surface-raised`). Додавати нові токени можна; видаляти чи перейменовувати —
лише зі зміною цього розділу.

| Група | Токени |
|---|---|
| Кольори інтерфейсу | `color.bg`, `color.surface`, `color.surface-raised`, `color.overlay`, `color.border`, `color.text`, `color.text-muted`, `color.text-inverse`, `color.accent`, `color.accent-hover`, `color.on-accent`, `color.like`, `color.on-like`, `color.success`, `color.danger`, `color.focus` |
| Кольори карти | `color.map.land`, `color.map.water`, `color.map.road`, `color.map.label`, `color.map.label-halo`, `color.map.ua-border`, `color.map.region-line`, `color.map.region-fill-active` |
| Піни і кластери | `color.pin.ring`, `color.pin.ring-audio`, `color.pin.ring-playing`, `color.pin.fallback-bg`, `color.pin.fallback-text`, `color.cluster.bg`, `color.cluster.text`, `color.cluster.ring` |
| Типографіка | `font.family.display`, `font.family.body`, `font.weight.regular`, `font.weight.medium`, `font.weight.bold`, `font.size.xs` … `font.size.3xl` (7 щаблів, rem), `font.line-height.tight`, `font.line-height.normal` |
| Форма | `radius.sm`, `radius.md`, `radius.lg`, `radius.pill`, `space.1` … `space.12` (крок 4 px), `shadow.sm`, `shadow.md`, `shadow.lg` |
| Рух | `motion.duration.fast` (≈150 ms), `motion.duration.base` (≈250), `motion.duration.slow` (≈400), `motion.duration.map-fly` (1200), `motion.easing.standard`, `motion.easing.emphasized` |
| Розміри | `size.pin.sm`, `size.pin.md`, `size.pin.lg`, `size.touch-min` (≥ 44 px), `size.sheet.peek` (висота згорнутої нижньої шторки) |

Вимоги до значень: контраст тексту ≥ 4.5:1 (`text` на `bg`/`surface`, `on-accent` на `accent`, `on-like` на `like`),
≥ 3:1 для `text-muted` на `surface` і для `cluster.text` на `cluster.bg`. Шрифти — лише з ліцензією для вебу
(OFL або куплена), з кирилицею, файли `woff2` у handoff.

---

## 6. Каталог рішень студії

Кожне рішення має ID і варіанти. Продукт технічно підтримує варіанти з колонки «Технічні ID» (для них потрібна
лише конфігурація або токени). Варіанти, що відсутні в колонці, дизайн може пропонувати вільно — тоді в handoff
мусить бути опис поведінки достатній для імплементації.

| ID рішення | Що вибираємо | Технічні ID варіантів (підтримує продукт) |
|---|---|---|
| `identity` | Айдентика: логотип, палітра, шрифти, тон голосу | вільні ID напрямів; продукт читає лише `tokens.json` |
| `map.basemap` | Стиль базової карти | вільні; продукт читає `map-style.json` |
| `map.initial-view` | Початковий вигляд | `UKRAINE`, `UKRAINE_WITH_NEAR_ME_PROMPT` |
| `map.aggregation` | Як показати кількість | `REGION_PLACE_PIN` (3 рівні, D-12), `PLACE_PIN` (2 рівні, без областей), `CHOROPLETH_PLACE_PIN` (області заливкою з числом) |
| `map.pin` | Вигляд піна музиканта | `AVATAR_RING`, `DROP_ICON`, `DOT_LABEL` |
| `map.cluster` | Вигляд бульбашки з числом | `BUBBLE_COUNT`, `BUBBLE_COUNT_LABEL` (число + назва пункту/області) |
| `map.origin-notice` | Пояснення геомітки | `PERSISTENT_CHIP`, `LEGEND_BAR`, `PIN_CARD_LABEL` (можна поєднувати; хоча б один постійний — D-14) |
| `audio.mobile-trigger` | Запуск фрагмента на телефоні | `TAP_PREVIEW_AND_PLAY`, `TAP_PREVIEW_PLAY_BUTTON`, `LONG_PRESS_PLAY` |
| `audio.desktop-hover` | Відгук на наведення | `RING_PROGRESS`, `EQUALIZER_BADGE` |
| `audio.player` | Де керування звуком | `MINI_BAR`, `IN_CARD` |
| `musician.preview` | Картка-прев'ю з карти | `BOTTOM_SHEET`, `FLOATING_CARD` |
| `musician.page` | Сторінка музиканта | `HERO`, `EDITORIAL`, `COMPACT` |
| `like.button` | Кнопка лайку і анімація | `HEART_BURST`, `APPLAUSE`, `CUSTOM_SYMBOL` |
| `filters.region` | Фільтр за областю | `SHEET_LIST`, `CHIPS_SCROLL`, `MAP_TAP_REGION` |
| `submission.form` | Форма пропозиції | `SINGLE_PAGE`, `WIZARD` |
| `nav.structure` | Структура навігації | `MAP_ONLY_SHEETS`, `TAB_BAR`, `TOP_BAR_MENU` |
| `onboarding` | Перше відкриття | `NONE`, `FIRST_VISIT_TIPS` |

## 7. Пакет handoff (студія → продукт)

Генерується командою `npm run handoff -w studio` у теку `studio/handoff/` і комітиться. Продукт копіює його в
`web/src/design/` командою `npm run design:sync -w web`.

```
studio/handoff/
  decisions.json      рішення (схема нижче)
  tokens.json         DTCG, обраний напрям айдентики
  tokens.css          згенеровані CSS-змінні --n-* на :root
  map-style.json      стиль MapLibre; URL тайлів — плейсхолдер "{{TILES_URL}}", гліфи — "{{GLYPHS_URL}}"
  copy.uk.json        тексти інтерфейсу (розділ 8)
  assets/             logo.svg, logo-mark.svg, favicon.svg, icons/*.svg, fonts/*.woff2 + LICENSE-*.txt
  screens/<decision-id>/<variant-id>--mobile.png, --desktop.png
  HANDOFF.md          людський опис: кожне рішення, обраний варіант, специфікація поведінки, скриншоти
```

`decisions.json`:
```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-12-15T12:00:00Z",
  "studioCommit": "a1b2c3d",
  "decisions": {
    "audio.mobile-trigger": {
      "variant": "TAP_PREVIEW_AND_PLAY",
      "status": "APPROVED",
      "approvedBy": "Замовник",
      "approvedAt": "2026-12-10",
      "params": { "longPressMs": 400 },
      "note": "Погоджено на зустрічі 10.12"
    }
  }
}
```
`status`: `PROPOSED` | `APPROVED`. Продукт імплементує лише `APPROVED`; для решти — поведінка за замовчуванням
з `01-decisions.md`. `params` — необов'язкові числові/рядкові параметри варіанту (затримки, розміри, пороги зуму).

## 8. Тексти інтерфейсу (`copy.uk.json`)

Плоский JSON «ключ → рядок», множина — ICU MessageFormat (`{count, plural, one {…} few {…} many {…} other {…}}`).
Продукт має тексти за замовчуванням для **всіх** ключів нижче; handoff їх перекриває.

Обов'язкові ключі: `app.name`, `app.tagline`, `map.originNotice`, `map.soundOn`, `map.soundOff`,
`map.soundBlockedHint`, `map.nearMe`, `map.nearMeDenied`, `map.resultsCount`, `map.backToUkraine`,
`cluster.ariaLabel`, `pin.ariaLabel`, `pin.noAudio`, `preview.openProfile`, `preview.play`, `preview.stop`,
`musician.originLabel.BIRTH`, `musician.originLabel.FORMATION`, `musician.originLabel.ORIGIN`,
`musician.precisionNote.CITY`, `musician.likeAria`, `musician.likesCount`, `musician.listenOn`,
`musician.youtubeChannel`, `musician.textSource`, `musician.photoCredit`, `musician.noAudio`,
`filters.title`, `filters.region`, `filters.reset`, `filters.apply`, `list.empty`, `submit.title`, `submit.intro`,
`submit.field.<name>.label` і `.hint` для кожного поля 2.10, `submit.consent`, `submit.send`, `submit.sending`,
`submit.success`, `submit.error.<code>`, `errors.network`, `errors.notFound`, `errors.rateLimited`, `errors.generic`.

## 9. Тека `contracts/`

```
contracts/
  openapi.yaml                 OpenAPI 3.1 за розділом 2          — створює технічна команда (план T, задача 1)
  fixtures/
    map-pins.json              MapPinsResponse                     — створює дизайн-команда (план S, задача 2)
    map-regions.json           RegionStat[] (27)
    map-places.json            PlaceStat[]
    filters.json               FilterDefinition[]
    musicians/index.json       сторінка 0 списку (size 20)
    musicians/<slug>.json      MusicianDetails, ≥ 40 шт.: з фото і без, з аудіо і без, PERSON і BAND, з Вікіпедії і від заявника
    fragments/<id>.json        Fragment для кожного з hasAudio = true
    places-search.json         PlaceSuggestion[] (≈ 2000 найбільших пунктів — для моку автодоповнення)
    audio/clip-01.wav … 06     синтетичні звуки 15 с, 22 050 Гц моно (жодних авторських прав)
  geo/
    regions.geojson            27 полігонів, properties: { code, name }, спрощено до ≤ 400 КБ
    ukraine-outline.geojson    зовнішній контур України з Кримом
    regions-meta.json          [{ code, name, center:[lon,lat] (polylabel), bbox }]
  scripts/
    build-fixtures.mjs         з dry-run V1 → fixtures/                — план S, задача 2
    build-geo.mjs              geoBoundaries UKR ADM1 → geo/           — план S, задача 2
```
Джерело фікстур — `nashi-backend/build/wiki-dry-run.json` з V1 (реальні музиканти з Вікіпедії, 21,6 МБ; якщо файла
немає — `./gradlew wikiDryRun` у `nashi-backend`). Джерело геоданих — geoBoundaries gbOpen UKR ADM1 (дані похідні від OpenStreetMap, ODbL 1.0;
перевірити, що є 27 одиниць, включно з Кримом і Севастополем; атрибуція — у `contracts/geo/README.md`; UI мапи показує «© OpenStreetMap contributors»).
