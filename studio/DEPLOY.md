# Локальний перегляд студії

Потрібні Node 24 і npm. Виконайте з кореня робочої копії `nashi_v2/`.

```sh
npm ci
npm run dev -w studio
```

Каталог відкривається за адресою Vite. Усі 17 рішень мають порівняння на `/d/<decisionId>` та пряме посилання `/v/<decisionId>/<variantId>`. Перемикач пристрою та QR-код доступні в кожному порівнянні. Розділи `/audit`, `/states`, `/flow/map-to-musician`, `/flow/suggest-musician` і `/handoff` відкриваються також напряму. Студія використовує дані з `contracts/fixtures`. Для перегляду без зовнішнього провайдера тайлів запустіть зі змінною `VITE_STUDIO_OFFLINE_MAP=1`.

## Перевірки

```sh
npm test -w contracts
npm run lint -w studio
npm run typecheck -w studio
npm test -w studio
npm run build -w studio
npx playwright install chromium webkit
npm run e2e -w studio
```

## Приватне демо на телефоні

Для спільного перегляду потрібна доступна з телефону HTTPS-адреса. QR-код використовує адресу відкритої студії. Якщо відкрити її як localhost, телефон теж отримає localhost.

Результат `npm run build -w studio` міститься в `studio/dist/`. На сервері HTTPS для окремого домену підключається [конфігурація nginx](../infra/nginx/studio.conf). Облікові дані Basic Auth зберігаються на сервері в `/etc/nginx/nashi-studio.htpasswd`. Після розгортання перевірте відповідь 401 без пароля на каталог, JSON і WAV, а після входу — сторінку порівняння, атрибуцію та відтворення з телефона. Автоматизація в репозиторії запускає перевірки при змінах студії або контрактів. Автопублікація після успішних перевірок у main вмикається змінною репозиторію `STUDIO_DEPLOY_ENABLED=true`. Задайте секрети Actions `STUDIO_HOST` (user@host), `STUDIO_SSH_KEY` та `STUDIO_KNOWN_HOSTS` із перевіреним ключем сервера. Статичні файли синхронізуються у `/var/www/nashi-studio/`. Збірку можна розгорнути й окремо, скопіювавши вміст `studio/dist/` на свій приватний HTTPS-хост.

## Передача технічній команді

Завантажте decisions.json у розділі «Передача». Пакет за зафіксованим файлом збирає команда `npm run handoff -w studio`. Формат пакета описано в [розділі 7 спільного контракту](../docs/02-contracts.md).
