# Web — продукт «Наші V2»

SPA на React 19 + Vite 7. `src/core/` — логіка без вигляду (карта, аудіо, лайки, форма, фільтри, конфіг дизайну),
покрита тестами; `src/ui/` — скін, що бере кольори й шрифти лише з токенів `--n-*`, а тексти — лише з ключів копірайту;
`src/design/` — пакет студії (не редагувати вручну, див. «Дизайн»). Усі команди — з кореня репозиторію (`nashi_v2/`),
Node.js 24, після `npm install`.

## Запуск

### На фікстурах, без backend

```bash
VITE_API_MOCK=1 npm run dev -w web                    # http://127.0.0.1:5174
VITE_API_MOCK=1 VITE_MAP_STYLE=blank npm run dev -w web   # те саме без зовнішніх тайлів (офлайн)
```

MSW у браузері відповідає даними з `contracts/fixtures`; аудіо й фото фікстур віддаються з `/fixtures/**`.

### Проти справжнього backend

```bash
infra/scripts/local-up.sh        # mongo, minio, backend на http://127.0.0.1:8081 (див. infra/LOCAL.md)
npm run dev -w web               # http://127.0.0.1:5174, /api проксується на backend
```

Проксі прибирає заголовок `Origin`: через нього API для сторінки той самий origin, тож POST (лайки, заявки) не впирається
в CORS backend. Інша адреса backend — `VITE_DEV_API_PROXY=http://host:port`.

### Змінні оточення

| Змінна | Що робить |
|---|---|
| `VITE_API_MOCK=1` | MSW замість API; збірка з цією змінною містить воркер і фікстури (для тестів, не для продакшену) |
| `VITE_API_BASE_URL` | база API, за замовчуванням `/api/v2` |
| `VITE_DEV_API_PROXY` | куди dev-сервер проксує `/api`, за замовчуванням `http://127.0.0.1:8081` |
| `VITE_MAP_STYLE=blank` | порожня основа карти без мережі (e2e, Lighthouse) |
| `VITE_MAP_TILES_URL`, `VITE_MAP_GLYPHS_URL` | тайли й гліфи для стилю студії (за замовчуванням OpenFreeMap) |
| `VITE_SITE_BASE_URL` | адреса сайту для посилань (задає Docker-збірка) |

## Перевірки

```bash
npm run test -w web              # Vitest (jsdom, MSW у Node)
npm run test:scripts -w web      # node --test для scripts/
npx tsc --noEmit -p web          # типи (src, e2e, конфіги)
npm run build -w web && npm run check:bundle -w web   # бюджети: старт ≤ 180 КБ gzip без maplibre, maplibre лише на «/», жодних тестових файлів у dist
npm run lhci -w web              # Lighthouse CI: власна збірка dist-lhci і локальний сервер на :4173 з API з фікстур
npm run gen:api -w web           # типи API з contracts/openapi.yaml
```

### e2e (Playwright)

```bash
PLAYWRIGHT_CHROMIUM_EXECUTABLE="/шлях/до/Chromium" npm run e2e -w web            # усе
PLAYWRIGHT_CHROMIUM_EXECUTABLE="/шлях/до/Chromium" npm run e2e -w web -- likes.spec.ts
```

- Playwright завжди запускає власний dev-сервер на `127.0.0.1:5174` (`VITE_API_MOCK=1`, `VITE_MAP_STYLE=blank`), тож
  **порт 5174 має бути вільним** — запущений `npm run dev -w web` треба спершу зупинити.
- `PLAYWRIGHT_CHROMIUM_EXECUTABLE` вказує на локальний Chromium / Chrome for Testing; без неї потрібен браузер,
  встановлений через `npx playwright install chromium`.
- Два проєкти: `mobile` (iPhone 13 на Chromium) і `desktop` (1440×900).
- Тестові гачки працюють лише в dev / mock-режимі: `window.__nashiMockFail` (помилка відповіді API, `map-errors.spec.ts`),
  `window.__nashiDecisions` (рішення студії як APPROVED, `approved-decisions.spec.ts`), `window.__nashiAudioState`.

### Візуальні еталони

`e2e/visual.spec.ts-snapshots/*-darwin.png` знято на macOS; на Linux (CI) їх поки немає. Еталон оновлюють лише тоді,
коли пікселі екрана змінюються навмисно, і лише для цих екранів:

```bash
npm run e2e -w web -- visual.spec.ts -g "map: pin level" --update-snapshots
npm run e2e -w web -- visual.spec.ts   # потім двічі без оновлення: еталони мають бути стабільними
```

## Дизайн

```bash
npm run design:sync -w web       # studio/handoff → src/design (рішення, токени, стиль карти, копірайт, ресурси)
```

Продукт застосовує лише рішення зі статусом `APPROVED`; для решти — варіанти за замовчуванням із `docs/01-decisions.md`.
Тексти: ключі з `docs/02-contracts.md` розділ 8 лежать у `src/design/copy.uk.json` (від студії), продуктові ключі поза
контрактом — у `src/core/i18n/defaults.uk.ts`.

## Продакшен

`infra/web.Dockerfile` збирає `dist` (без тестових файлів) і віддає його через nginx (`infra/nginx/web.conf`): gzip для
JS, CSS, JSON і GeoJSON, `Cache-Control: no-cache` для `index.html` і маршрутів SPA, `immutable` лише для `/assets/`,
`/api/` проксується на backend. Локальний запуск і перевірка — `infra/LOCAL.md`, розділ «Web».
