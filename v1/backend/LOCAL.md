# Локальний запуск Nashi

## Що потрібно

- Docker Desktop (Compose v2).
- Приблизно 4 ГБ вільної оперативної пам'яті.
- Репозиторій фронтенду поруч: `../nashi-frontend` (збирається в Docker).

## Запуск однією командою

```bash
./scripts/local-up.sh            # перша збірка триває кілька хвилин
./scripts/local-up.sh --no-build # без перезбірки образів
./scripts/local-down.sh          # зупинити (база зберігається)
```

Після запуску:

- Сайт: http://localhost:4173
- API: http://localhost:8080/swagger-ui

Ключ MapTiler скрипт бере зі змінної `VITE_MAPTILER_API_KEY` або з `../nashi-frontend/.env.local` (значення не виводиться).

Образ фронтенду містить ключ MapTiler, тому його не можна публікувати чи передавати іншим.

## Що всередині

Три сервіси Compose:

- `mongo` — MongoDB 7 на `127.0.0.1:27017`, том `mongo_data`.
- `backend` — Spring Boot у профілі `local` на `127.0.0.1:8080`.
- `frontend` — зібраний Vite-сайт за nginx на `127.0.0.1:4173`.

Нічого не йде в продакшн-базу, Telegram-бот, S3 чи OpenAI: профіль `local` вимикає їх, а `StartupConfigGuard` не дає стартувати з іншими налаштуваннями. Секрети в образ не потрапляють (`.dockerignore`).

## Звідки дані

Порожня база заповнюється з Вікіпедії при старті бекенда (`LocalSeedRunner`, `nashi.wiki-import.on-startup=true`): імпорт іде у фоні, якщо в базі ще немає жодного митця з Вікіпедії. Береться до 150 нових кандидатів з кожної групи запитів (музиканти, гурти, художники, письменники). Спершу імпорт отримує весь список кандидатів, тому до перших записів минає хвилин 20. Кінець видно в логах (`docker compose logs -f backend`) за рядком «Імпорт з Вікіпедії: …».

Кількість на групу задається змінною `NASHI_WIKIIMPORT_LIMITPERCATEGORY` (це `nashi.wiki-import.limit-per-category` за правилами relaxed binding у Spring; `0` — без ліміту). Додайте її в `environment` сервісу `backend` у `docker-compose.yml`. Щоб наповнити базу заново, скиньте її (нижче) і запустіть стек знову.

## Скинути базу

```bash
./scripts/local-down.sh --reset
```

Це видаляє том `mongo_data`.

## Режим розробки

```bash
docker compose up -d mongo
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

У фронтенді створіть `.env.local` з `VITE_API_BASE_URL=http://localhost:8080` і виконайте `npm run dev`.

Увага: якщо на 27017 уже працює інша MongoDB, `bootRun` писатиме саме в ту базу. Або звільніть 27017 для Mongo з Compose, або задайте `SPRING_DATA_MONGODB_URI` явно.

## Що не працює локально

- Завантаження фото та аудіо у формі (S3 вимкнено).
- Модерація через Telegram.
- AI-теги (OpenAI вимкнено).

## Якщо щось не так

- `docker compose logs backend` — логи бекенду.
- `docker compose ps` — стан сервісів.
- Порти 8080, 4173, 27017 мають бути вільні. Якщо 27017 зайнятий (наприклад, локальним mongod), задайте інший: `NASHI_MONGO_PORT=27018 ./scripts/local-up.sh`.
- Повна перезбірка: `docker compose build --no-cache`.
- Чиста база: `./scripts/local-down.sh --reset`.
