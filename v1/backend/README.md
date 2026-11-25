# Nashi

Spring Boot application.

## Local stack

One-command local stack (Mongo + backend + frontend): see [LOCAL.md](LOCAL.md).

## MongoDB (local dev)

This project uses MongoDB via Spring Data MongoDB.

### Start MongoDB

```bash
docker compose up -d
```

Mongo will be available on `mongodb://localhost:27017/nashi`.

### Run the app with dev profile

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Tests

Tests use an embedded MongoDB (Flapdoodle) so you don’t need to run Mongo locally for CI/unit tests:

```bash
./gradlew test
```

## Swagger / OpenAPI

This project includes Swagger UI via **springdoc-openapi**.

- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Configuration / Secrets

This project expects secrets to be provided via environment variables (they are **not** committed to the repo).

Required env vars:

```bash
export SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/nashi'
export SPRING_AI_OPENAI_API_KEY='...'

export AWS_S3_ACCESS_KEY_ID='...'
export AWS_S3_SECRET_ACCESS_KEY='...'
export AWS_S3_REGION='eu-north-1'
export AWS_S3_BUCKET='nashi-music-storage-2025'

export TELEGRAM_BOT_TOKEN='...'
export TELEGRAM_ADMIN_CHAT_ID='...'
export TELEGRAM_CHANNEL_ID='...'
```

## Notes

- `spring.data.mongodb.auto-index-creation` is enabled in `dev`/`test` profiles to automatically create indexes from annotations like `@Indexed` and `@GeoSpatialIndexed`.
- In production it’s often better to manage indexes explicitly (set `SPRING_DATA_MONGODB_AUTO_INDEX_CREATION=true` only if you accept auto-creation).
# nashi-backend
