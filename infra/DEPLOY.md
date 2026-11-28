# Деплой V2

Як розгорнути «Наші» V2 на сервері, як оновлювати й відкочувати, як налаштувати пайплайн. Локальна розробка —
[LOCAL.md](LOCAL.md).

## Як це влаштовано

```
браузер ─HTTPS─▶ nginx сервера (TLS, certbot) ─▶ 127.0.0.1:4174 ─▶ web (nginx: сторінки + /api/) ─▶ backend ─▶ mongo
                                                                                                      ├─▶ S3 (фото)
                                                                                                      ├─▶ Telegram (модерація)
                                                                                                      └─▶ Вікіпедія, Commons
```

| Файл | Що це |
|---|---|
| `infra/docker-compose.prod.yml` | стек: mongo (з паролем), backend, web; MinIO — за бажанням (`COMPOSE_PROFILES=minio`) |
| `infra/.env.example` | шаблон налаштувань; копія `infra/.env` живе лише на сервері (у git не потрапляє) |
| `infra/scripts/deploy.sh <тег>` | оновлення: підтягує образи, перезапускає, чекає здоров'я, при збої повертає попередній тег |
| `.github/workflows/deploy.yml` | пайплайн: пуш у `main` → тести → образи в GHCR → `deploy.sh` на сервері по SSH |

Усі команди нижче — з кореня репозиторію. `docker compose` сам читає `infra/.env`, бо він лежить поряд з compose-файлом.

## 1. Спробувати локально

На цій машині вже є робочий `infra/.env` для локального запуску (у git його немає). Якщо його немає — скопіюйте
`infra/.env.example` у `infra/.env` і задайте локальні значення з таблиці нижче.

```bash
docker compose -f infra/docker-compose.prod.yml up -d --build --wait
open http://localhost:8088
docker compose -f infra/docker-compose.prod.yml down        # зупинити (дані лишаються)
docker compose -f infra/docker-compose.prod.yml down -v     # зупинити й стерти базу та фото
```

Локальний `.env` відрізняється від продакшену лише цим:

| Ключ | Локально | Продакшн |
|---|---|---|
| `DEPLOY_WEB_PORT` | `8088` | `4174` |
| `COMPOSE_PROFILES` | `minio` (S3 у стеку) | порожньо (AWS S3) |
| `NASHI_S3_ENDPOINT`, `NASHI_S3_PATH_STYLE_ACCESS` | `http://minio:9000`, `true` | не задавати |
| `NASHI_S3_PUBLIC_BASE_URL` | `http://localhost:9100/nashi-v2` | адреса бакета або CDN |
| `NASHI_TELEGRAM_*_ENABLED` | `false`, токен і чат — заглушки | справжній бот і група |
| `NASHI_SITE_BASE_URL`, `NASHI_CORS_ALLOWED_ORIGINS` | `http://localhost:8088` | `https://<домен>` |
| `NASHI_WIKI_IMPORT_*` | увімкнено, 5 музикантів на категорію при першому старті | вимкнено до міграції V1 (розділ 6) |

Паролі й секрети генеруйте: `openssl rand -hex 24` (hex не треба екранувати в адресі Mongo).

## 2. Що підготувати до першого деплою

- **Сервер:** Ubuntu 24.04, від 2 vCPU / 4 ГБ RAM / 30 ГБ диска. Встановити Docker Engine з плагіном compose, nginx,
  certbot (`python3-certbot-nginx`), git. Окремий користувач для деплою в групі `docker`.
- **Домен:** A-запис на IP сервера.
- **S3 (AWS):** бакет у `eu-north-1`; IAM-користувач з `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` на
  `arn:aws:s3:::<бакет>/*` і `s3:ListBucket` на `arn:aws:s3:::<бакет>`. Публічно читається лише префікс `artists/`
  (фото музикантів); `submissions/` лишається приватним. Політика бакета (і дозволити bucket policies у «Block public access»):
  ```json
  {"Version": "2012-10-17", "Statement": [{"Sid": "PublicArtists", "Effect": "Allow", "Principal": "*",
    "Action": "s3:GetObject", "Resource": "arn:aws:s3:::<бакет>/artists/*"}]}
  ```
- **Telegram:** **новий** бот у @BotFather (`/newbot`) — не бойовий бот V1, інакше два застосунки забиратимуть один
  одному оновлення. Додати бота в групу модерації адміністратором. Щоб бот бачив команди без `@ім'я_бота`:
  @BotFather → `/setprivacy` → Disable. ID групи: напишіть щось у групі й до старту backend виконайте
  `curl -s https://api.telegram.org/bot<ТОКЕН>/getUpdates` — потрібне `chat.id` (від'ємне, `-100…`).
- **Для міграції V1:** доступ на читання до бази й бакета V1.

## 3. Перший запуск на сервері

```bash
sudo mkdir -p /opt/nashi-v2 && sudo chown "$USER" /opt/nashi-v2
git clone https://github.com/levkovych67/nashi2-.git /opt/nashi-v2
cd /opt/nashi-v2
cp infra/.env.example infra/.env && chmod 600 infra/.env
nano infra/.env                   # заповнити всі ключі з розділу «required»
docker compose -f infra/docker-compose.prod.yml up -d --build --wait
```

`--build` збирає образи прямо на сервері. З пайплайном (розділ 9) образи збирає GitHub, а сервер лише їх завантажує.

Якщо бракує ключа, backend не стартує і пише в лог, якого саме: `docker compose -f infra/docker-compose.prod.yml logs backend`.

## 4. nginx і HTTPS

`/etc/nginx/sites-available/nashi-v2` (потім `ln -s` у `sites-enabled`):

```nginx
server {
    listen 80;
    server_name <домен>;
    client_max_body_size 60m;            # заявка з фото — до 55 МБ

    location / {
        proxy_pass http://127.0.0.1:4174;
        proxy_set_header Host $host;
        # Перезаписати, не дописувати: backend рахує ліміти за IP клієнта
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d <домен>       # додає HTTPS і переадресацію з HTTP
```

### Фото в MinIO на тому самому сервері (так працює nashi.now)

Без AWS: MinIO запускається в стеку, nginx віддає з нього лише читання за адресою `/s3/`.

У `infra/.env`:
```
COMPOSE_PROFILES=minio
NASHI_S3_ENDPOINT=http://minio:9000
NASHI_S3_PATH_STYLE_ACCESS=true
NASHI_S3_BUCKET=nashi-v2
NASHI_S3_REGION=us-east-1
NASHI_S3_ACCESS_KEY_ID=<довільний логін MinIO>
NASHI_S3_SECRET_ACCESS_KEY=<openssl rand -hex 24>
NASHI_S3_PUBLIC_BASE_URL=https://<домен>/s3/nashi-v2
DEPLOY_MINIO_PORT=9100
```

У блок `server` домену в nginx:
```nginx
    location /s3/ {
        limit_except GET HEAD { deny all; }
        proxy_pass http://127.0.0.1:9100/;
        proxy_set_header Host 127.0.0.1:9100;
        proxy_hide_header Set-Cookie;
        add_header Cache-Control "public, max-age=86400";
    }
```

Фото тоді живуть лише на диску сервера (Docker-том `nashi-v2-prod_minio-data`), тож їх треба бекапити разом із
базою (розділ 8).

## 5. Перевірка

```bash
curl -fsS https://<домен>/api/v2/health           # {"status":"UP"}
docker compose -f infra/docker-compose.prod.yml ps  # усі healthy
```

У групі модерації: `/ping` → «alive». Відкрити сайт на телефоні й комп'ютері.

## 6. Дані при першому запуску продакшену

Порядок важливий: імпорт з Вікіпедії пропускає музикантів, перенесених з V1, тому він іде після міграції.

1. У `infra/.env` розкоментувати й заповнити `NASHI_MIGRATION_V1_*`, `NASHI_WIKI_IMPORT_ENABLED=false`;
   `docker compose -f infra/docker-compose.prod.yml up -d --wait`.
2. У групі: `/migrate_v1 dry` → переглянути звіт → `/migrate_v1 run` → `/stats`.
3. У `.env`: прибрати `NASHI_MIGRATION_V1_*`, поставити `NASHI_WIKI_IMPORT_ENABLED=true`, знову `up -d --wait`.
4. У групі: `/wiki_import` (2–3 години, звіт прийде в групу), потім `/audio_resolve`, `/stats`.

Інші команди: `/hide <slug>`, `/show <slug>` — сховати або повернути музиканта.

## 7. Оновлення і відкат

- **Пайплайном** — автоматично після пушу в `main` (розділ 9).
- **Вручну, збираючи на сервері:**
  ```bash
  cd /opt/nashi-v2 && git pull && docker compose -f infra/docker-compose.prod.yml up -d --build --wait
  ```
- **Вручну, з готових образів:** `infra/scripts/deploy.sh <git sha>`. Скрипт чекає, доки всі контейнери стануть
  healthy (до 180 с), записує тег в `infra/.env` і `infra/.last-good-tag`. Якщо новий тег не піднявся, він сам
  повертає попередній.
- **Відкат на конкретну версію:** `infra/scripts/deploy.sh <попередній sha>`. База при цьому не змінюється.

## 8. Бекапи

Бекап бази в архів (на сервері, щодня через cron):

```bash
mkdir -p /opt/nashi-v2-backups
# crontab -e:  30 2 * * *  cd /opt/nashi-v2 && docker compose -f infra/docker-compose.prod.yml exec -T mongo sh -c 'mongodump --quiet --archive --gzip -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db nashi_v2' > /opt/nashi-v2-backups/nashi_v2-$(date +\%F).archive.gz && find /opt/nashi-v2-backups -mtime +14 -delete
```

Відновлення:

```bash
docker compose -f infra/docker-compose.prod.yml exec -T mongo sh -c 'mongorestore --quiet --archive --gzip --drop -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin' < /opt/nashi-v2-backups/nashi_v2-<дата>.archive.gz
```

Архіви варто копіювати з сервера (наприклад, в окремий S3-бакет). Для фото — увімкнути versioning на бакеті.

## 9. Пайплайн (GitHub Actions)

Staging немає: `main` — це продакшен. `.github/workflows/deploy.yml` запускається на кожен пуш у `main`, що
змінює `backend/`, `web/`, `contracts/`, `infra/` або залежності (зміни лише в `*.md`, `docs/`, `studio/` деплой не
запускають). Його також можна запустити вручну: **Actions → Deploy → Run workflow** — наприклад, щоб передеплоїти.

Кроки:
1. **backend** — `./gradlew test` (Mongo і MinIO піднімає Testcontainers);
2. **web** — тести контракту, типи, юніт-тести, збірка, бюджети бандла;
3. **images** — лише якщо обидві перевірки зелені: образи `ghcr.io/<власник>/nashi-v2-backend:<sha>` і `…-web:<sha>` у GHCR;
4. **deploy** — по SSH на сервері: `git checkout <sha>` → `infra/scripts/deploy.sh <sha>`. Якщо нова версія не стала
   healthy за 180 с, скрипт повертає попередню, а запуск у GitHub стає червоним.

Поки змінна `DEPLOY_ENABLED` не дорівнює `true`, виконуються лише перевірки (кроки 1–2), а збірка образів і деплой
пропускаються. Так можна пушити ще до того, як готовий сервер.

**У GitHub:**
Settings → Secrets and variables → Actions:

| Секрет | Що це |
|---|---|
| `DEPLOY_HOST` | `користувач@сервер`, наприклад `deploy@185.233.45.191` (або лише сервер — тоді користувач зі змінної `DEPLOY_USER`) |
| `DEPLOY_SSH_KEY` | приватний ключ цього користувача (окремий ключ лише для деплою; його публічна частина — у `~/.ssh/authorized_keys` користувача на сервері) |
| `DEPLOY_KNOWN_HOSTS` | результат `ssh-keyscan <сервер>` |

| Змінна (Variables) | Що це |
|---|---|
| `DEPLOY_ENABLED` | `true` — збирати образи й деплоїти; інакше лише перевірки |
| `DEPLOY_USER` | користувач, якщо в `DEPLOY_HOST` його немає (за замовчуванням `deploy`) |
| `DEPLOY_SSH_PORT` | порт SSH, якщо не 22 |
| `DEPLOY_DIR` | каталог на сервері (за замовчуванням `/opt/nashi-v2`) |

**На сервері:**
- репозиторій склонований у `/opt/nashi-v2` (розділ 3), `infra/.env` заповнений;
- в `infra/.env`: `DEPLOY_IMAGE_PREFIX=ghcr.io/<власник маленькими літерами>/nashi-v2`;
- пакети GHCR приватні. Пайплайн на час деплою сам логінить сервер у GHCR тимчасовим токеном запуску і після
  деплою виходить, тож нічого налаштовувати не треба. Для ручного `deploy.sh <sha>` спершу
  `docker login ghcr.io` з токеном, що має лише `read:packages` (або зробити пакети `nashi-v2-backend` і
  `nashi-v2-web` публічними в налаштуваннях пакета — секретів в образах немає, усе береться з `infra/.env`);
- якщо пакети колись пушили вручну, а не цим пайплайном, крок **images** може отримати 403 на пуш: тоді в
  налаштуваннях кожного пакета → Manage Actions access додати репозиторій з роллю Write.

## Скільки файлів конфігурації

Застосунок налаштовує **один файл** — `infra/.env` на сервері: і Docker (образи, порт, пароль Mongo), і backend
(база, S3, Telegram, домен). У git він не потрапляє, `git checkout` під час деплою його не чіпає, `deploy.sh` змінює в
ньому лише `DEPLOY_IMAGE_TAG`. Файл лежить усередині копії репозиторію, тож `git clean -fdx` у `/opt/nashi-v2`
його видалить — тримайте копію значень у менеджері паролів.

Окрім нього є ще дві речі, які налаштовуються один раз і не змінюються з релізами: nginx сервера (розділ 4) і
SSH-секрети в GitHub (розділ 9). `infra/docker-compose.prod.yml` лежить у репозиторії, редагувати його на сервері не треба.

## Ще не зроблено перед продакшеном

- E2E-тести web у пайплайні (зараз їхні знімки-еталони є лише для macOS).
- Заголовки безпеки (CSP) і загальний ліміт запитів на nginx сервера.
- Автоматичне копіювання бекапів поза сервер і моніторинг доступності.
- Юридичне підтвердження умов Deezer і Apple — без нього прев'ю з цих джерел вимкнені.
- Приймання на реальних пристроях (iPhone Safari, Android Chrome) за чек-листом.
