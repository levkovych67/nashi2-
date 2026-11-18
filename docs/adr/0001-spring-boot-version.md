# ADR 0001: Версія Spring Boot

- Статус: прийнято (T2)
- Дата: 2026-11-18

## Контекст

D-61: backend на Java 21 і останній GA-версії Spring Boot 4.x, якщо `springdoc-openapi` і бібліотеки Telegram
(`telegrambots-longpolling`, `telegrambots-client`, D-65) її підтримують; інакше — Spring Boot 3.5.x.

Стан на 2026-11-18 (Maven Central і start.spring.io): остання GA — **Spring Boot 4.1.2** (start.spring.io пропонує її
за замовчуванням); 4.2.0 — лише RC2; лінія 3.5 закінчується на 3.5.16.

Перевірено на самому проєкті (`./gradlew test`, 59 тестів, з них 9 інтеграційних на Testcontainers):

| Бібліотека | Версія | Сумісність з Boot 4.1.2 |
|---|---|---|
| springdoc-openapi-starter-webmvc-ui | 3.2.0 | Зібрана з батьківським `spring-boot-starter-parent` 4.1.2; `/v3/api-docs` віддає `/api/v2/health` в IT |
| telegrambots-longpolling, telegrambots-client | 10.3.0 | Без залежностей від Spring; використовують Jackson 2 (`com.fasterxml`), який Boot 4.1.2 досі керує (2.21.7) поруч із Jackson 3. Перевірено окремим запуском: серіалізація `SendMessage`, десеріалізація `Update` з `callback_query`, створення `OkHttpTelegramClient` і `TelegramBotsLongPollingApplication` |
| Testcontainers | 2.0.5 (керує Boot) | `testcontainers-mongodb`, `testcontainers-minio`, `testcontainers-junit-jupiter` |
| AWS SDK v2 (BOM) | 2.59.3 | Лише резолвиться (S3-клієнт — T9) |
| Інші | bucket4j_jdk17-core 8.22.0, thumbnailator 0.4.21, imageio-webp 3.15.3, metadata-extractor 2.22.0, jaudiotagger 3.0.1; caffeine 3.2.4 і gson 2.13.2 — версії Boot | Резолвляться; код з ними — у наступних задачах |

Збірка: Gradle 9.9.1 (wrapper від start.spring.io для Boot 4.1.2), Java toolchain 21 через
`org.gradle.toolchains.foojay-resolver-convention` 1.0.0 (на машині розробника лише JDK 23 — Gradle сам завантажує 21).

## Рішення

**Spring Boot 4.1.2**, Java 21, springdoc 3.2.0, telegrambots 10.3.0, Testcontainers 2.0.5. Fallback на 3.5.x не
потрібен.

Зміни Boot 4 / Spring Framework 7, які довелося врахувати (важливо для наступних задач):

- `EnvironmentPostProcessor` переїхав у `org.springframework.boot.EnvironmentPostProcessor`; старий
  `org.springframework.boot.env.EnvironmentPostProcessor` застарілий. Ключ у `META-INF/spring.factories` — новий.
- URI MongoDB — `spring.mongodb.uri` (і `spring.mongodb.database`); `spring.data.mongodb.uri` більше не читається
  (застарілий з рівнем `error`).
- Стартери перейменовані/розділені: `spring-boot-starter-webmvc` (не `-web`), тестові — по модулях
  (`spring-boot-starter-webmvc-test`, `-data-mongodb-test`, …). `TestRestTemplate` і `RestTestClient` — у модулі
  `spring-boot-resttestclient` і вмикаються `@AutoConfigureRestTestClient` / `@AutoConfigureTestRestTemplate`.
- Spring MVC серіалізує JSON через **Jackson 3** (`tools.jackson.*`); він за замовчуванням сортує властивості за
  абеткою. Анотації `com.fasterxml.jackson.annotation.*` спільні для обох версій.
- `ProblemDetail.type` у Spring 7 за замовчуванням не задано (поле пропускається), тож `ProblemDetailsAdvice` явно
  ставить `about:blank` — контракт вимагає `type`.
- `HttpStatus.PAYLOAD_TOO_LARGE` застарілий — `HttpStatus.CONTENT_TOO_LARGE` (413).
- Testcontainers 2: `org.testcontainers.mongodb.MongoDBContainer` (старий клас у `containers` застарілий);
  `MinIOContainer` лишився в `org.testcontainers.containers`.
- JUnit 6 (керує Boot 4.1).

Окремо: образ `minio/minio` більше не доступний на Docker Hub (і `quay.io/minio/minio`). Тести використовують
`pgsty/minio:RELEASE.2026-08-04T00-00-00Z` — спільнотну збірку того самого сервера (той самий entrypoint і API) через
`asCompatibleSubstituteFor("minio/minio")`. T4 (docker-compose) має взяти той самий образ; клієнт `mc` для
`minio-init` теж треба перевірити.

## Альтернативи

- **Spring Boot 4.2.0-RC2** — не GA.
- **Spring Boot 3.5.16** — усе потрібне сумісне й з 4.1.2, тож відкат лише відклав би міграцію (Jackson 3, нові
  модулі) на пізніше.
- **telegrambots-springboot-longpolling-starter** (як у V1) — не використовуємо (D-65): ручна реєстрація в T15
  не залежить від версії Boot.

## Наслідки

- Наступні задачі пишуть під Boot 4 API (див. список змін вище); ключ Mongo — `spring.mongodb.uri`.
- Пакунки з Jackson 2 (telegrambots, swagger-core у springdoc) працюють поруч із Jackson 3; Boot тримає версії обох.
- Оновлення до 4.2.x — після GA, окремою задачею з повним `./gradlew test`.
