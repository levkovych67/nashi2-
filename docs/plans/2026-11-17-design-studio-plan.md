# Платформа-студія дизайну «Наші V2» — план імплементації

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Побудувати вебплатформу-студію `nashi_v2/studio/`, у якій замовник на телефоні й на комп'ютері порівнює живі
прототипи айдентики та UX «Наших V2» на реальних даних про музикантів, фіксує вибір по кожному рішенню, а дизайн-команда
одною командою формує пакет handoff для технічної команди.

**Architecture:** Статичний SPA (Vite + React) без власного бекенду. Кожне рішення з каталогу (розділ 6
`02-contracts.md`) має 2–4 варіанти — React-компоненти, що рендеряться в рамках пристроїв (390×844 і 1440×900) і в будь-якій
айдентиці (набір DTCG-токенів → CSS-змінні `--n-*`). Варіанти користуються спільними прототипними «двигунами» (карта з
трирівневою агрегацією, аудіо з моделюванням обмежень браузерів, лайки з батчингом) і фікстурами `contracts/fixtures`,
згенерованими з dry-run V1. Вибір живе в localStorage і в посиланні `?sel=`; затверджене — у `studio/decisions/decisions.json`
(git, PR дизайн-ліда); генератор `npm run handoff -w studio` збирає `studio/handoff/` за розділом 7 контрактів.

**Tech Stack:** Node 24 LTS, npm workspaces; React 19.2, TypeScript ~5.9, Vite 7, Tailwind CSS 4.1, React Router 7,
`react-map-gl` 8 + `maplibre-gl` 5, `supercluster` 8, Zustand 5, React Hook Form 7 + Zod 4, `intl-messageformat` 10,
`qrcode` 1.5, `fontkit` 2; Vitest 3 + Testing Library 16 (jsdom); Playwright 1.5x (`iPhone 13` на WebKit, `Desktop 1440` на
Chromium) + `@axe-core/playwright`; `tsx` 4 для скриптів; у `contracts/`: `node:test`, `mapshaper` 0.6, `polylabel` 2,
`@turf/boolean-point-in-polygon` 7. Тайли — OpenFreeMap (без ключа).

**Spec:** `nashi_v2/docs/tasks/01-design-team-task.md` (ТЗ дизайн-команді). Нормативні: `nashi_v2/docs/01-decisions.md`,
`nashi_v2/docs/02-contracts.md` (розділи 2, 5–9). Довідково: `nashi_v2/docs/00-v1-audit.md`, протокол переговорів (задача 1).

## Global Constraints

- Студія не імпортує нічого з `web/`, а `web/` — нічого зі `studio/`. Спільне — лише `contracts/` і пакет `studio/handoff/`.
- ID рішень і технічні ID варіантів — буквально з розділу 6 `02-contracts.md` (17 рішень). Нові варіанти — дозволено; перейменування технічних ID — ні.
- Опорні екрани: mobile 390×844 (мінімальний 360×640), desktop 1440×900 (мінімальний 1024×768). Кожен варіант має mobile-версію; desktop-версію — кожен, крім тих, що за природою лише мобільні (`audio.mobile-trigger`).
- Ціль дотику ≥ 44×44 px (`size.touch-min`), зокрема для пінів і бульбашок.
- Кольори, шрифти, радіуси, відступи, тіні, тривалості у варіантах — лише через токени розділу 5 (`--n-*` або утиліти Tailwind, змаплені на них). Жодних hex/rgb/hsl і палітри Tailwind (`bg-red-500`) у `src/decisions/`, `src/proto/components/`, `src/flows/`.
- Кожна айдентика заповнює всі 76 токенів розділу 5; контраст: `text` на `bg` і `surface`, `on-accent` на `accent`, `on-like` на `like` ≥ 4.5:1; `text-muted` на `surface`, `cluster.text` на `cluster.bg` ≥ 3:1.
- Тексти — українською, лише через ключі `copy.uk.json` (розділ 8), множина — ICU MessageFormat. Текст пояснення геомітки за замовчуванням: «Позначка — місце походження музиканта, а не те, де він зараз» (D-14).
- На кожному екрані з картою в кожному варіанті й сценарії видно пояснення геомітки (`data-testid="origin-notice"`) — D-14.
- Агрегація: `regionMaxZoom = 7`, `placeMaxZoom = 10` (D-12); переліт: область — `fitBounds` 1200 мс, пункт — `flyTo` zoom 12, 1200 мс; `prefers-reduced-motion` → 0 мс (D-17).
- Аудіо: фрагмент 15 с, fade-in 300 мс, fade-out 600 мс (D-21); hover-intent 250 мс (D-26); звук лише після жесту (D-23); на iOS без fade (D-24); три mobile-режими (D-25); стан «фрагмента немає» (D-27).
- Лайки: батч ≤ 50, flush через 600 мс після останнього тапу, flush при `pagehide` (D-31); 429 не губить тапи (D-32).
- У репозиторії немає захищеного авторським правом аудіо: лише синтетичні WAV з `contracts/fixtures/audio/`. Фото — посилання на Wikimedia Commons з атрибуцією; файли з S3 V1 не використовуються.
- Студія не індексується: `robots.txt` → `Disallow: /`, `<meta name="robots" content="noindex">`, nginx `X-Robots-Tag: noindex`, basic auth.
- Доступність: `@axe-core/playwright` без порушень рівня `serious` і `critical` на кожному варіанті в кожній айдентиці.
- Жодних ключів і секретів у репозиторії; MapTiler-ключ студії не потрібен.
- Коміти — Conventional Commits з областю: `feat(studio): …`, `feat(contracts): …`, `test(studio): …`, `chore(studio): …`.

## Review Focus

1. **Зіпсоване або чуже посилання `?sel=`** (обрізане в месенджері, старої версії, з неіснуючим варіантом) — студія відкривається, розпізнане застосовує, решту перелічує в тості «Частину вибору з посилання не розпізнано», не падає. Тести: `tests/unit/shareLink.test.ts` (S4), `tests/e2e/share-link.spec.ts` (S4).
2. **Неповні або дивні записи V1** (без місця, без фото, `region: null`, місто Київ з `region: "KYIV"`, Севастополь з `"CRIMEA"`, категорії `WRITER`+`MUSICIAN`, артефакти `1978(19780106)` у біографії, порожня біографія) — фікстури валідні, Київ → `KYIV_CITY`, Севастополь → `SEVASTOPOL_CITY`, письменники відсіяні. Тести: `contracts/scripts/test/build-fixtures.test.mjs` (S2).
3. **Звук на реальному телефоні**: автоплей заблоковано, швидке перемикання між пінами, iOS без керування гучністю — одночасно звучить не більше одного фрагмента, стан `blocked` з підказкою, жодного необробленого `Promise`-відхилення. Тести: `tests/unit/audioEngine.test.ts`, `tests/unit/playController.test.ts` (S10).
4. **Лайки під навантаженням**: 120 тапів поспіль, відповідь 429, закриття вкладки посеред батчу — жоден тап не загублено, показаний лічильник ніколи не зменшується. Тести: `tests/unit/likeBatcher.test.ts` (S12).
5. **Неповний handoff**: затверджено варіант, якого немає в реєстрі; в айдентиці бракує токена; у текстах бракує ключа — генератор завершується з кодом 1 і переліком проблем, нічого не записавши частково. Тести: `tests/unit/handoff.test.ts` (S17).

---

## Структура файлів

```
nashi_v2/
  package.json                      workspaces: contracts, studio (+ web — додає технічна команда)
  .github/workflows/studio.yml      CI + деплой студії (S3)
  infra/nginx/studio.conf           віртуальний хост студії (S3)
  contracts/
    package.json                    "nashi-contracts": скрипти build:fixtures, build:geo, test
    fixtures/ …                     вихід S2 (розділ 9 контрактів)
    geo/ …                          вихід S2; geo/source/ — у .gitignore
    scripts/
      build-fixtures.mjs            CLI
      build-geo.mjs                 CLI
      fixture-extras.json           3 вигадані «заявки» (без Вікіпедії)
      lib/                          regions.mjs, translit.mjs, text.mjs, pin.mjs, links.mjs, audio.mjs, wav.mjs,
                                    select.mjs, shapes.mjs, fixtures.mjs, geo.mjs
      test/                         *.test.mjs (node:test) + sample-dry-run.json
  studio/
    package.json, index.html, vite.config.ts, playwright.config.ts, tsconfig.json
    public/robots.txt, public/audit/*.png
    decisions/decisions.json        затверджені рішення (git, PR дизайн-ліда)
    handoff/                        вихід S17 (комітиться)
    scripts/build-tokens.ts, handoff.ts, capture-v1.ts
    src/
      main.tsx, app/router.tsx, app/StudioLayout.tsx
      shell/                        DeviceFrame, QrCode, IdentitySwitcher, StudioToolbar, devices.ts, urls.ts
      pages/                        OverviewPage, DecisionPage, VariantFullscreenPage, FlowPage, HandoffPage, AuditPage
      decisions/
        catalog.ts                  17 рішень (розділ 6)
        registry.ts                 VariantDef, реєстр
        index.ts                    import.meta.glob('./*/index.ts', { eager: true })
        <decision-id>/index.ts, <variant-id>.tsx   варіанти (S7, S9–S15)
        map.basemap/templates/*.json               шаблони стилю карти
      selection/                    types.ts, shareLink.ts, store.ts, effective.ts, decisionsFile.ts, webhook.ts
      tokens/                       required.ts, dtcg.ts, css.ts, contrast.ts
      identities/<id>/              tokens.json, meta.ts, copy.uk.json (опц.), assets/
      identities/generated/identities.css          вихід build-tokens
      styles/theme.css              Tailwind 4 @theme → --n-*
      proto/
        data/                       types.ts (схеми розділу 2), fixtures.ts
        copy/                       copy.uk.json, required.ts, t.ts
        map/                        aggregation.ts, placeIndex.ts, pinIndex.ts, camera.ts, regionAt.ts, mapStyle.ts,
                                    overlay.ts, format.ts, ProtoMap.tsx
        audio/                      engine.ts, platform.ts, hoverIntent.ts, longPress.ts, mobileTrigger.ts,
                                    playController.ts, fragments.ts, useAudio.ts, SoundToggle.tsx
        likes/                      likeBatcher.ts, fixtureLikeServer.ts, useLikes.ts
        filters/                    filterParams.ts, FilterRenderer.tsx
        submission/                 schema.ts, platform.ts, placeSearch.ts, image.ts, fixtureSubmit.ts
        musician/                   lifeDates.ts, linkGroups.ts, TextSourceNote.tsx, PhotoCredit.tsx
        components/                 спільні примітиви на токенах: Button, Sheet, Card, Chip, Field, Toast
      flows/                        scenarios.ts, FlowPlayer.tsx, map-to-musician.tsx, suggest-musician.tsx
      audit/findings.ts
    tests/unit/*.test.ts(x), tests/e2e/*.spec.ts
```

## Команда, оцінки, календар

Ролі: **Д1** — UX/UI-дизайнер (лід, власник `decisions.json`), **Д2** — бренд-дизайнер, **І1, І2** — дизайн-інженери
(фронтенд). Оцінки — людино-дні (лд).

| Задача | Дизайнер, лд | Дизайн-інженер, лд |
|---|---|---|
| S1 Каркас, рамки пристроїв, QR | — | 2 |
| S2 Фікстури і геодані (`contracts/`) | — | 3 |
| S3 Деплой і CI студії | — | 1 |
| S4 Каталог, реєстр, стан вибору, шерінг | — | 2.5 |
| S5 Токени, Tailwind-міст, контраст, тексти | 0.5 | 2 |
| S6 UX-аудит V1 | 2 | 0.5 |
| S7 Напрями айдентики | 6 | 2 |
| S8 Прототипний двигун карти | — | 4 |
| S9 Варіанти карти | 4 | 3 |
| S10 Аудіо-двигун і варіанти | 2 | 3.5 |
| S11 Картка-прев'ю і сторінка музиканта | 3 | 2.5 |
| S12 Лайки | 1 | 1.5 |
| S13 Фільтр за областю | 1 | 1.5 |
| S14 Форма пропозиції | 2 | 2.5 |
| S15 Навігація, онбординг, стани | 2 | 1.5 |
| S16 Наскрізні сценарії, повнота реєстру | 1 | 2 |
| S17 Генератор handoff | — | 2.5 |
| S18 Погодження і передача | 3 | 1 |
| **Разом** | **27.5** | **38.5** |

Разом 66 лд + резерв 15 % ≈ **76 лд**. Вартість = Σ(лд ролі × денна ставка ролі) — ставки визначає виконавець.
При складі Д1+Д2+І1+І2 тривалість ≈ **5 тижнів** (4 тижні роботи + тиждень на зворотний зв'язок замовника).

Календар (за умови старту в понеділок 23.11.2026; зсувається разом зі стартом):

| Тиждень | Задачі | Контрольна точка |
|---|---|---|
| 1 (23–27.11) | S1, S2, S3, S4, S5, S6; старт S7 (мудборди) | Пт 27.11 — внутрішнє демо: студія доступна з телефона, аудит V1 |
| 2 (30.11–04.12) | S7, S8, старт S9 | **Демо 1, пт 04.12** — 3 напрями айдентики + V1 на реальній карті → замовник звужує до 1–2 напрямів |
| 3 (07–11.12) | S9, S10, S11 | **Демо 2, пт 11.12** — карта, піни, агрегація, звук на телефонах → вибір `map.*`, `audio.*`, `musician.*` |
| 4 (14–18.12) | S12, S13, S14, S15, S16 | **Демо 3, пт 18.12** — лайк, фільтри, форма, навігація, наскрізні сценарії → вибір решти |
| 5 (21–25.12) | доопрацювання за фідбеком, S17, S18 | **Фінальне погодження, ср 23.12**; **handoff технічній команді, пт 25.12** |

## Матриця «вимога замовника → задача»

| Вимога / критерій приймання (задача 1 протоколу) | Задачі |
|---|---|
| Проаналізувати дизайн, структуру та UX V1 | S6 |
| Окрема платформа-студія для перегляду й вибору варіантів | S1, S3, S4 |
| Варіанти повного UX основних сценаріїв; сценарій від загального вигляду карти до музиканта | S16 (з S8–S15) |
| Альтернативні підходи до айдентики; вибрати й зафіксувати айдентику та візуальний стиль | S5, S7, S4 |
| Варіанти візуальних стилів і оформлення | S5, S7, S9 (`map.basemap`) |
| Варіанти розташування й поведінки основних елементів | S9, S11, S15 |
| Відображення музикантів як pin-маркерів | S9 (`map.pin`) |
| Карта: початковий вигляд України/регіону | S9 (`map.initial-view`), S8 |
| Карта: плавне переміщення до центру території | S8 (`camera.ts`) |
| Карта: кількість музикантів міста/регіону | S8 (`aggregation.ts`), S9 (`map.aggregation`, `map.cluster`) |
| Карта: масштабування; окремі музиканти після наближення | S8, S9 |
| UX карти на mobile і desktop | S8, S9, S16 |
| Запуск випадкового фрагмента з карти; варіанти для mobile без hover | S10 |
| Сторінка музиканта: ім'я, опис, зображення, матеріали, посилання на музику, YouTube-канал, походження, кнопка лайку, кількість лайків | S11, S12 |
| Фільтри, щонайменше за областю | S13 |
| Форма пропозиції: дані, посилання, зображення | S14 |
| Постійне пояснення: геомітка = походження, не місцеперебування | S9 (`map.origin-notice`), тест у S16 |
| Mobile-first + адаптація для desktop | Global Constraints, S1, усі e2e (`mobile`, `desktop`) |
| Зафіксувати вибраний варіант для кожної складової | S4, S18 |
| Передати рішення технічній команді без повторного узгодження | S17, S18 |
| Питання до виконавця (терміни/вартість, формат фіксації, mobile-аудіо, незалежність від backend) | S18, розділ «Відповіді на питання замовника» |

---

## Задачі

### Task S1: Каркас студії, рамки пристроїв і QR-код

**Files:**
- Create: `nashi_v2/package.json` (якщо його ще немає; якщо технічна команда вже створила — лише додати `"contracts"`, `"studio"` у `workspaces`)
- Create: `studio/package.json`, `studio/index.html`, `studio/vite.config.ts`, `studio/tsconfig.json`, `studio/playwright.config.ts`, `studio/public/robots.txt`
- Create: `studio/src/main.tsx`, `studio/src/app/router.tsx`, `studio/src/app/StudioLayout.tsx`
- Create: `studio/src/shell/devices.ts`, `studio/src/shell/DeviceFrame.tsx`, `studio/src/shell/QrCode.tsx`, `studio/src/shell/urls.ts`
- Create: `studio/src/pages/OverviewPage.tsx` (заглушка), `DecisionPage.tsx`, `VariantFullscreenPage.tsx`, `FlowPage.tsx`, `HandoffPage.tsx`, `AuditPage.tsx` (заглушки з заголовком)
- Test: `studio/tests/unit/devices.test.ts`, `studio/tests/unit/urls.test.ts`, `studio/tests/e2e/smoke.spec.ts`

**Interfaces:**
- Produces:
  - `type DeviceKind = 'mobile' | 'desktop'`
  - `const DEVICES: { mobile: {width: 390, height: 844}; mobileMin: {width: 360, height: 640}; desktop: {width: 1440, height: 900}; desktopMin: {width: 1024, height: 768} }`
  - `frameScale(device: DeviceKind, container: { width: number; height: number }): number`
  - `variantUrl(origin: string, decisionId: string, variantId: string, identity: string): string`
  - `<DeviceFrame device: DeviceKind; children: ReactNode; identity: string />` — рендерить вміст у натуральному розмірі пристрою, масштабований `transform: scale()`; кореневий елемент має `data-identity={identity}` і `data-device={device}`
  - `<QrCode value: string; size?: number />` — SVG через `qrcode.toString(value, { type: 'svg', margin: 1 })`
  - Маршрути: `/`, `/d/:decisionId`, `/v/:decisionId/:variantId`, `/flow/:scenarioId`, `/handoff`, `/audit`

- [ ] **Step 1: Ініціалізувати workspace і залежності**

`studio/package.json`: `"name": "nashi-studio"`, `"private": true`, `"type": "module"`, скрипти `dev` (`vite`), `build`
(`tsc -b && vite build`), `test` (`vitest run`), `e2e` (`playwright test`), `lint` (`eslint .`), `typecheck`
(`tsc --noEmit`), `tokens` (`tsx scripts/build-tokens.ts`), `handoff` (`tsx scripts/handoff.ts`). Залежності — з Tech Stack.
`vite.config.ts`: плагіни `@vitejs/plugin-react`, `@tailwindcss/vite`, `vite-plugin-static-copy` (ціль: `../contracts/fixtures/*` →
`fixtures`, щоб `/fixtures/audio/clip-01.wav` віддавався і в dev, і в білді); `server.fs.allow: ['..']`; Vitest:
`environment: 'jsdom'`, `include: ['tests/unit/**/*.test.{ts,tsx}']`. `playwright.config.ts`: `webServer`
`npm run build && npx vite preview --port 4174`, проєкти `mobile` (`devices['iPhone 13']`) і `desktop`
(`browserName: 'chromium', viewport: {width: 1440, height: 900}`). `robots.txt`: `User-agent: *` / `Disallow: /`;
`index.html` містить `<meta name="robots" content="noindex">` і `<html lang="uk">`.

- [ ] **Step 2: Написати падаючі тести**

```ts
// tests/unit/devices.test.ts
test('mobile frame scales to the narrower side', () => {
  expect(frameScale('mobile', { width: 300, height: 500 })).toBeCloseTo(0.5924, 4); // min(300/390, 500/844)
});
test('desktop frame scales by width', () => {
  expect(frameScale('desktop', { width: 720, height: 600 })).toBe(0.5);
});
test('never upscales', () => {
  expect(frameScale('mobile', { width: 2000, height: 2000 })).toBe(1);
});

// tests/unit/urls.test.ts
test('variant url keeps the dotted decision id and the identity', () => {
  expect(variantUrl('https://studio.example', 'map.pin', 'AVATAR_RING', 'atlas'))
    .toBe('https://studio.example/v/map.pin/AVATAR_RING?identity=atlas');
});
```
`tests/e2e/smoke.spec.ts`: на обох проєктах `/` має заголовок сторінки `Студія «Наші V2»` і `<meta name="robots"
content="noindex">`; `/v/map.pin/AVATAR_RING` рендериться без шапки студії (`data-testid="studio-chrome"` відсутній).

- [ ] **Step 3: Запустити — тести падають**

Run: `npm test -w studio` — Expected: FAIL «frameScale is not defined», «variantUrl is not defined».

- [ ] **Step 4: Реалізувати `devices.ts`, `urls.ts`, `DeviceFrame`, `QrCode`, роутер і заглушки сторінок**

`frameScale` = `Math.min(1, container.width / d.width, container.height / d.height)`. `DeviceFrame` вимірює контейнер
через `ResizeObserver`. Маршрут `/v/...` рендериться поза `StudioLayout`.

- [ ] **Step 5: Запустити — тести проходять**

Run: `npm test -w studio && npm run e2e -w studio -- smoke.spec.ts` — Expected: PASS (6 unit + 4 e2e).

- [ ] **Step 6: Commit**

```bash
git add package.json studio/
git commit -m "feat(studio): scaffold studio app with device frames and QR"
```

---

### Task S2: Спільні фікстури і геодані (`contracts/`)

Це спільний артефакт: технічна команда використовує ті самі фікстури для моків і тестів `web`. Формати — розділи 2 і 9
`02-contracts.md`.

**Files:**
- Create: `contracts/package.json` (якщо є — додати скрипти й devDependencies), `contracts/.gitignore` (`geo/source/`)
- Create: `contracts/scripts/lib/regions.mjs`, `translit.mjs`, `text.mjs`, `pin.mjs`, `links.mjs`, `audio.mjs`, `wav.mjs`, `select.mjs`, `shapes.mjs`, `fixtures.mjs`, `geo.mjs`
- Create: `contracts/scripts/build-fixtures.mjs`, `contracts/scripts/build-geo.mjs`, `contracts/scripts/fixture-extras.json`
- Create: `contracts/geo/README.md` (джерело, ліцензія CC BY 4.0, атрибуція geoBoundaries)
- Create (вихід): `contracts/fixtures/**`, `contracts/geo/regions.geojson`, `ukraine-outline.geojson`, `regions-meta.json`
- Test: `contracts/scripts/test/translit.test.mjs`, `text.test.mjs`, `pin.test.mjs`, `links.test.mjs`, `wav.test.mjs`, `build-fixtures.test.mjs`, `build-geo.test.mjs`, `fixtures-match-openapi.test.mjs`, `contracts/scripts/test/sample-dry-run.json`

**Interfaces:**
- Consumes: `nashi-backend/build/wiki-dry-run.json` з V1 (масив об'єктів з ключами `qid`, `name`, `wikiTitle`, `ensemble`,
  `biography`, `place {qid, labelUk, lat, lon, region}`, `imageUrl` (мініатюра Commons 500 px), `imageCredit {author,
  license, licenseUrl, filePageUrl}`, `categories`, `birthDate`, `deathDate`, `birthPlace`, `locationBasis`, `genres`
  (рядки українською), `works [{wikidataId, title, year, articleUrl}]`, `socialLinks [{platform, url}]`, `foundationYear`,
  `disbandYear`); geoBoundaries gbOpen UKR ADM1 GeoJSON (завантажити з geoboundaries.org у `contracts/geo/source/`).
- Produces:
  - `REGIONS: ReadonlyArray<{ code: Region; name: string; iso: string }>` (27 рядків; `iso` — ISO 3166-2:UA)
  - `transliterate(text: string): string` (КМУ 2010), `slugify(name: string, taken: Set<string>): string`
  - `fixtureId(qid: string): string` — перші 24 hex-символи SHA-256 від `"fixture:" + qid`
  - `regionOf(place: {qid: string; region: string | null}): Region | null`
  - `cleanBiography(text: string): string`, `firstSentence(text: string): string`, `summarize(text: string, max: number): string`, `taglineFrom(sentence: string): string | null`
  - `pinFor(artistId: string, place: {placeId: string; lat: number; lon: number; type: PlaceType; areaKm2?: number}, precision: PinPrecision): {lat: number; lon: number}` — формула розділу 4
  - `mapLinks(v1Links: {platform: string; url: string}[]): {platform: LinkPlatform; group: LinkGroup; url: string}[]`
  - `clipsFor(id: string, works: {title: string}[]): Clip[]`, `writeWav(samples: Int16Array, sampleRate: number): Buffer`, `synthClip(n: 1|2|3|4|5|6): Int16Array`
  - `buildFixtures(records: object[], regionsMeta: RegionMeta[], extras: object[]): FixtureSet` (чиста функція; CLI пише файли)
  - Файли розділу 9: `fixtures/map-pins.json`, `map-regions.json`, `map-places.json`, `filters.json`, `musicians/index.json`, `musicians/<slug>.json` (≥ 40), `fragments/<id>.json`, `places-search.json`, `audio/clip-01.wav` … `clip-06.wav`, `img/extra-1.svg` … `extra-3.svg`; `geo/regions.geojson`, `geo/ukraine-outline.geojson`, `geo/regions-meta.json`
  - Аудіо в фікстурах — URL від кореня: `/fixtures/audio/clip-0N.wav` (і студія, і `web` віддають `contracts/fixtures` під `/fixtures/`)

Правила відображення dry-run → контракти (те, що тест має закріпити):

| Контракт | З dry-run V1 |
|---|---|
| відбір | лише записи, де `categories` містить `MUSICIAN` або `BAND`, і є `place` з `lat/lon` |
| `id` | `fixtureId(qid)` |
| `slug` | `slugify(name)` (КМУ 2010, `-2`, `-3` при збігу) |
| `kind` | `BAND`, якщо `ensemble === true` або `categories` містить `BAND`, інакше `PERSON` |
| `region` | `regionOf(place)`: `Q1899` (Київ) → `KYIV_CITY`; `Q7525` (Севастополь) → `SEVASTOPOL_CITY`; інакше `place.region` |
| `placeId`, `placeName` | `place.qid`, `place.labelUk` |
| `PlaceType` | `CITY`, якщо з цього пункту ≥ 5 музикантів у наборі, інакше `TOWN` (dry-run не має типу; у продукті тип — з довідника `places`) |
| `origin.basis` | `BIRTH` → `BIRTH`; `FORMATION` → `FORMATION`; `RESIDENCE`, `WORK`, `null` → `ORIGIN` |
| `origin.precision` | `BIRTH`, `FORMATION` → `SETTLEMENT`; інакше `CITY` |
| `origin.lat/lon`, піни | `pinFor(id, place, precision)` |
| `summary` | `summarize(cleanBiography(biography), 300)` |
| `description` | `summarize(cleanBiography(biography), 2000)` |
| `tagline` | `taglineFrom(firstSentence(cleanBiography(biography)))` — текст після « — » без крапки |
| `textSource` | `{type: 'WIKIPEDIA', title: wikiTitle, url: <посилання WIKI з socialLinks>, license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/deed.uk'}` |
| `images` | `imageUrl` → `[{url: imageUrl, thumbUrl: imageUrl, alt: name, credit: {author, license, licenseUrl, sourceUrl: filePageUrl}}]`; без фото → `[]` |
| `dates` | `birth: birthDate`, `death: deathDate`, `activeFrom: foundationYear`, `activeTo: disbandYear` (`null`, якщо відсутні) |
| `genres` | `genres` як є (≤ 5) |
| `links` | `mapLinks(socialLinks)`: `WIKI`→`WIKIPEDIA`; `SPOTIFY`, `APPLE_MUSIC`, `DEEZER`, `SOUNDCLOUD`, `BANDCAMP`, `YOUTUBE`, `WEBSITE`, `INSTAGRAM`, `FACEBOOK`, `TIKTOK`, `TELEGRAM`, `X` — як є; `DISCOGS`, `LASTFM`, `ESU` → `OTHER`; `WIKIDATA`, `MUSICBRAINZ`, `COMMONS`, `WIKISOURCE`, `OPEN_LIBRARY`, `GOODREADS`, `CHTYVO`, `ARTSY`, `WIKIART` — відкидаються; `group` — за розділом 2 |
| `youtubeChannelUrl` | перше посилання `YOUTUBE` або `null` |
| `works` | `{title, year, type: 'OTHER', url: articleUrl}` |
| `hasAudio` | є посилання на `DEEZER`, `APPLE_MUSIC`, `SPOTIFY`, `SOUNDCLOUD`, `BANDCAMP` або `YOUTUBE` (реалістичне покриття ≈ 15–20 %) |
| `materials.clips` | `clipsFor(id, works)`: кількість `1 + h[0] % 3`; файл `clip-0{(h[1] + i) % 6 + 1}.wav`; `source` = `['UPLOAD','DEEZER','ITUNES','COMMONS'][h[2] % 4]`; `title` = `works[i].title` або `Фрагмент {i+1}`; `durationSec: 15` (h — байти SHA-256 від `id`) |
| `fragments/<id>.json` | перший кліп: `startSec: 0`, `durationSec: 15`, `fadeInMs: 300`, `fadeOutMs: 600`, `attribution` за джерелом (`Прев'ю: Deezer`, `Прев'ю: Apple Music`, `Запис: Wikimedia Commons`, `Завантажено на «Наші»`), `expiresAt` лише для `DEEZER` |
| `likesCount` | `h[3] * 37 + h[4]` (детерміновано, 0…9690) |
| `materials.videos` | `[]` |
| `musicians/<slug>.json` | ≥ 40 записів, відібраних `pickDetailSamples`: ≥ 10 `BAND`, ≥ 25 `PERSON`, ≥ 15 з фото, ≥ 8 без фото, ≥ 12 з аудіо, ≥ 12 без, ≥ 5 з `youtubeChannelUrl`, + 3 записи з `fixture-extras.json` (`textSource: null`, `credit: null`, фото `/fixtures/img/extra-N.svg`, `basis: ORIGIN`, `precision: CITY`) |
| `places-search.json` | усі різні пункти набору (≈ 1065), відсортовані за кількістю музикантів спадно (замінник населення) |

- [ ] **Step 1: Написати падаючі unit-тести бібліотеки**

```js
// test/translit.test.mjs
assert.equal(slugify('Океан Ельзи', new Set()), 'okean-elzy');
assert.equal(slugify('Соломія Крушельницька', new Set()), 'solomiia-krushelnytska');
assert.equal(slugify('Жадан і Собаки', new Set()), 'zhadan-i-sobaky');
assert.equal(slugify('Юрій Іздрик', new Set()), 'yurii-izdryk');
assert.equal(slugify('Згорани', new Set()), 'zghorany');           // «зг» → «zgh»
assert.equal(slugify('Їжак', new Set()), 'yizhak');                // Ї на початку слова → «yi»
assert.equal(slugify('«ДахаБраха»', new Set()), 'dakhabrakha');    // лапки відкидаються
assert.equal(slugify('The Hardkiss', new Set()), 'the-hardkiss');
assert.equal(slugify('Океан Ельзи', new Set(['okean-elzy'])), 'okean-elzy-2');

// test/text.test.mjs
const LYNIV = 'Оксана Ярославівна Линів (лат. Oksana Lyniv, нар. 6 січня 1978(19780106), Броди, Львівська область, УРСР) — українська диригентка. Перша жінка на посаді головного диригента Опери та філармонійного оркестру в місті Грац.';
const clean = cleanBiography(LYNIV);
assert.ok(!clean.includes('(19780106)'));
assert.equal(firstSentence(clean),
  'Оксана Ярославівна Линів (лат. Oksana Lyniv, нар. 6 січня 1978, Броди, Львівська область, УРСР) — українська диригентка.');
assert.equal(taglineFrom(firstSentence(clean)), 'українська диригентка');
assert.equal(summarize('Перше речення. Друге речення. Третє.', 30), 'Перше речення. Друге речення.');
const long = summarize('Слово '.repeat(80).trim(), 300);
assert.ok(long.length <= 300 && long.endsWith('…') && !long.endsWith(' …'));
assert.equal(summarize('', 300), '');

// test/pin.test.mjs
const kyiv = { placeId: 'Q1899', lat: 50.45, lon: 30.523611, type: 'CITY' };
assert.deepEqual(pinFor('a'.repeat(24), kyiv, 'EXACT'), { lat: 50.45, lon: 30.523611 });
assert.deepEqual(pinFor('6650f0c2a1b2c3d4e5f60718', kyiv, 'CITY'), pinFor('6650f0c2a1b2c3d4e5f60718', kyiv, 'CITY')); // детерміновано
// 2000 різних id: відстань ≤ 3000 м (+1 м допуску), середнє r/R ∈ [0.637, 0.697] (рівномірно по площі: E = 2/3)
// з areaKm2 = 839 (Київ): R = clamp(0.6·sqrt(839/π)·1000, 150, 8000) = 8000 → max ≤ 8001 м
// VILLAGE без площі: max ≤ 401 м

// test/links.test.mjs
assert.deepEqual(mapLinks([
  { platform: 'WIKI', url: 'https://uk.wikipedia.org/wiki/X' },
  { platform: 'MUSICBRAINZ', url: 'https://musicbrainz.org/artist/1' },
  { platform: 'SPOTIFY', url: 'https://open.spotify.com/artist/2' },
  { platform: 'DISCOGS', url: 'https://www.discogs.com/artist/3' },
]), [
  { platform: 'WIKIPEDIA', group: 'REFERENCE', url: 'https://uk.wikipedia.org/wiki/X' },
  { platform: 'SPOTIFY', group: 'MUSIC', url: 'https://open.spotify.com/artist/2' },
  { platform: 'OTHER', group: 'SOCIAL', url: 'https://www.discogs.com/artist/3' },
]);

// test/wav.test.mjs
const wav = writeWav(synthClip(1), 22050);
assert.equal(wav.length, 661544);                       // 44 + 22050·15·2
assert.equal(wav.toString('ascii', 0, 4), 'RIFF');
assert.equal(wav.toString('ascii', 8, 12), 'WAVE');
assert.equal(wav.readUInt16LE(20), 1);                  // PCM
assert.equal(wav.readUInt16LE(22), 1);                  // моно
assert.equal(wav.readUInt32LE(24), 22050);
assert.equal(wav.readUInt16LE(34), 16);
assert.equal(wav.readUInt32LE(40), 661500);
assert.notDeepEqual(synthClip(1), synthClip(2));        // шість різних звучань
```

- [ ] **Step 2: Запустити — падають**

Run: `npm test -w contracts` — Expected: FAIL «Cannot find module '../lib/translit.mjs'» тощо.

- [ ] **Step 3: Реалізувати `lib/*.mjs`**

- `transliterate` — таблиця КМУ 2010: є/ї/й/ю/я на початку слова → `ye/yi/y/yu/ya`, усередині → `ie/i/i/iu/ia`; г→h, ґ→g,
  х→kh, ц→ts, ч→ch, ш→sh, щ→shch, ж→zh, и→y, і→i; `ь`, апострофи `'’ʼ` і лапки відкидаються; «зг» → «zgh».
  `slugify` — нижній регістр, усе не `[a-z0-9]` → `-`, стиснення й обрізання дефісів.
- `cleanBiography` — видаляє `\(\d{8}\)`, стискає пробіли.
- `firstSentence` — алгоритм (тест його не визначає повністю):
  ```
  depth = 0
  для кожного символу c на позиції i:
    якщо c ∈ '(«' → depth++; якщо c ∈ ')»' → depth = max(0, depth-1)
    якщо depth == 0 і c ∈ '.!?…' і text[i+1] — пробіл і text[i+2] — велика літера (кирилиця або латиниця) або '«'
      → повернути text[0..i]
  повернути весь text
  ```
  `summarize` додає речення, поки довжина ≤ `max`; якщо перше речення довше — обрізає по останньому пробілу до `max-1` і
  додає `…`.
- `pinFor` — формула розділу 4 контрактів, `crypto.createHash('sha256')`, `readUInt32BE(0)` і `readUInt32BE(4)`.
- `synthClip(n)` — детермінована мелодія: мінорна гама від ноти `[220, 247, 262, 294, 330, 349][n-1]` Гц, 8 нот на
  такт, темп `[84, 96, 108, 120, 132, 144][n-1]` уд/хв, синус + 2-га гармоніка, огинаюча ADSR 10/80/0.6/120 мс, амплітуда
  0.4 від максимуму; 15 с; 22 050 Гц.

- [ ] **Step 4: Запустити unit-тести — проходять**

Run: `npm test -w contracts` — Expected: PASS.

- [ ] **Step 5: Тест збирання фікстур на малому наборі**

`test/sample-dry-run.json` — 8 записів: R1 Линів (`MUSICIAN`, Броди Q465104, `LVIV`, `BIRTH`, біографія з `(19780106)`,
фото); R2 музикант, Київ `Q1899`, `region: "KYIV"`, `BIRTH`; R3 музикант, Севастополь `Q7525`, `region: "CRIMEA"`;
R4 гурт `ensemble: true`, Львів `Q36036`, `FORMATION`, `foundationYear: 1994`, посилання `SPOTIFY`; R5 лише `WRITER`
(Полтава); R6 музикант без `place`; R7 `["WRITER","MUSICIAN"]`, Одеса; R8 `categories: ["BAND"]`, Харків, порожня
біографія.

```js
// test/build-fixtures.test.mjs
const set = buildFixtures(sample, regionsMeta, extras);
assert.equal(set.pins.pins.length, 6 + 3);                                   // R1–R4, R7, R8 + 3 extras
assert.equal(set.regions.length, 27);
assert.equal(set.regions.find(r => r.code === 'KYIV_CITY').count, 1);
assert.equal(set.regions.find(r => r.code === 'KYIV').count, 0);
assert.equal(set.regions.find(r => r.code === 'SEVASTOPOL_CITY').count, 1);
assert.equal(set.regions.find(r => r.code === 'CRIMEA').count, 0);
assert.ok(!set.pins.pins.some(p => p.name === R5.name || p.name === R6.name));
assert.equal(set.details[slugOf(R4)].kind, 'BAND');
assert.equal(set.details[slugOf(R4)].dates.activeFrom, 1994);
assert.equal(set.details[slugOf(R1)].origin.precision, 'SETTLEMENT');
assert.equal(set.details[slugOf(R8)].summary, '');
assert.equal(set.filters[0].key, 'region');
assert.equal(set.filters[0].options.length, 27);
for (const d of Object.values(set.details)) assertMusicianDetails(d);        // shapes.mjs: розділ 2.6
for (const f of Object.values(set.fragments)) assertFragment(f);             // інваріант 2.7: startSec + durationSec ≤ 15
assert.equal(set.pins.version, set.pins.version.match(/^[0-9a-f]{8}$/)[0]); // перші 8 hex SHA-256 від JSON пінів
```
Додатковий тест `full dry-run satisfies sample quotas` читає `../nashi-backend/build/wiki-dry-run.json` (шлях від
`nashi_v2`) і пропускається (`t.skip`), якщо файла немає; перевіряє квоти `pickDetailSamples` з таблиці вище і
`pins.length ≥ 3500`.

- [ ] **Step 6: Запустити — падає; реалізувати `select.mjs`, `fixtures.mjs`, `shapes.mjs`, CLI `build-fixtures.mjs`**

CLI: `node scripts/build-fixtures.mjs --dry-run <path> [--out fixtures]`; спершу видаляє `fixtures/` (крім `.gitkeep`),
потім пише файли з 2-пробільним відступом і `\n` у кінці (стабільні диффи). `pickDetailSamples` проходить записи,
відсортовані за `id`, і бере запис, якщо він закриває ще не виконану квоту, доки не виконано всі квоти і не набрано 40.

- [ ] **Step 7: Тест геоданих**

```js
// test/build-geo.test.mjs (пропускається, якщо немає geo/source/*.geojson)
const { regions, outline, meta } = buildGeo(sourceGeojson);
assert.equal(regions.features.length, 27);
assert.deepEqual(new Set(regions.features.map(f => f.properties.code)), new Set(REGIONS.map(r => r.code)));
assert.ok(Buffer.byteLength(JSON.stringify(regions)) <= 400 * 1024);
for (const m of meta) assert.ok(pointInRegion(m.center, regions, m.code));     // polylabel — усередині полігону
assert.ok(booleanPointInPolygon([34.10, 44.95], outline.features[0]));        // Сімферополь у контурі
assert.ok(!booleanPointInPolygon([28.86, 47.01], outline.features[0]));       // Кишинів поза контуром
const kyivCity = meta.find(m => m.code === 'KYIV_CITY'), kyivObl = meta.find(m => m.code === 'KYIV');
assert.ok(bboxInside(kyivCity.bbox, kyivObl.bbox));
```

- [ ] **Step 8: Реалізувати `geo.mjs` і CLI `build-geo.mjs`**

Ключ відповідності — `shapeISO` (ISO 3166-2:UA): UA-05 VINNYTSIA, UA-07 VOLYN, UA-09 LUHANSK, UA-12 DNIPROPETROVSK,
UA-14 DONETSK, UA-18 ZHYTOMYR, UA-21 ZAKARPATTIA, UA-23 ZAPORIZHZHIA, UA-26 IVANO_FRANKIVSK, UA-30 KYIV_CITY, UA-32 KYIV,
UA-35 KIROVOHRAD, UA-40 SEVASTOPOL_CITY, UA-43 CRIMEA, UA-46 LVIV, UA-48 MYKOLAIV, UA-51 ODESA, UA-53 POLTAVA,
UA-56 RIVNE, UA-59 SUMY, UA-61 TERNOPIL, UA-63 KHARKIV, UA-65 KHERSON, UA-68 KHMELNYTSKYI, UA-71 CHERKASY,
UA-74 CHERNIHIV, UA-77 CHERNIVTSI. Якщо `shapeISO` порожній — CLI друкує `shapeName` усіх невідповідних і завершується
кодом 1 (таблицю доповнює людина). Спрощення: `mapshaper -simplify 8% keep-shapes -o precision=0.0001`; контур —
`-dissolve`. `center` — `polylabel` найбільшого полігону з точністю 0.01°. Скачаний вихідний файл кладеться в окрему
`contracts/geo/source/` (не в теку зі скриптами) і не комітиться.

- [ ] **Step 9: Тест відповідності OpenAPI (міжкомандний)**

`test/fixtures-match-openapi.test.mjs`: якщо `contracts/openapi.yaml` існує (його пише технічна команда, план T,
задача 1) — кожен файл фікстур валідується проти відповідної схеми (`MapPinsResponse`, `RegionStat[]`, `PlaceStat[]`,
`FilterDefinition[]`, `MusicianPage`, `MusicianDetails`, `Fragment`, `PlaceSuggestion[]`) через `@apidevtools/swagger-parser`
+ `ajv` (draft 2020-12); інакше `t.skip('openapi.yaml ще не створено')`.

- [ ] **Step 10: Згенерувати фікстури й геодані, усі тести проходять**

Run: `npm run build:geo -w contracts -- --src geo/source/geoBoundaries-UKR-ADM1.geojson && npm run build:fixtures -w contracts -- --dry-run ../nashi-backend/build/wiki-dry-run.json && npm test -w contracts`
Expected: PASS; у `fixtures/musicians/` ≥ 43 файли; `map-pins.json` ≤ 600 КБ.

- [ ] **Step 11: Commit**

```bash
git add contracts/
git commit -m "feat(contracts): fixtures from V1 dry-run and Ukraine region geodata"
```

---

### Task S3: Деплой і CI студії

**Files:**
- Create: `.github/workflows/studio.yml`, `infra/nginx/studio.conf`, `studio/DEPLOY.md`

**Interfaces:**
- Consumes: скрипти S1 (`build`, `test`, `e2e`, `lint`, `typecheck`), `contracts` (`test`).
- Produces: студія доступна за `https://studio.<домен>/` з basic auth; кожен push у `main`, що змінює `studio/**` або
  `contracts/**`, перевіряє й розгортає.

- [ ] **Step 1: Workflow**

`studio.yml`: тригер `push` на `main` і `pull_request` з `paths: ['studio/**', 'contracts/**', '.github/workflows/studio.yml']`;
`concurrency: studio-deploy`; job `check` (Node 24, `npm ci`, `npm test -w contracts`, `npm run lint -w studio`,
`npm run typecheck -w studio`, `npm test -w studio`, `npx playwright install --with-deps chromium webkit`,
`npm run e2e -w studio`, артефакт `playwright-report` при падінні); job `deploy` лише для `main`: `npm run build -w studio`,
`rsync` `studio/dist/` → `${{ secrets.STUDIO_HOST }}:/var/www/nashi-studio/` (ключ — `secrets.STUDIO_SSH_KEY`).

- [ ] **Step 2: nginx**

`studio.conf`: `server_name studio.<домен>`; `root /var/www/nashi-studio`; `auth_basic "Nashi studio"`;
`auth_basic_user_file /etc/nginx/nashi-studio.htpasswd` (файл лише на сервері); `add_header X-Robots-Tag "noindex, nofollow" always`;
`location / { try_files $uri /index.html; }`; `location /assets/ { expires 1y; add_header Cache-Control "public, immutable"; }`;
`location /fixtures/audio/ { types { audio/wav wav; } }`. TLS — certbot, як на сервері V1.

- [ ] **Step 3: Перевірити**

Run: відкрити PR з дрібною зміною студії — Expected: job `check` зелений; після merge — `curl -sI https://studio.<домен>/`
повертає `401`, з логіном — `200` і заголовок `X-Robots-Tag: noindex, nofollow`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/studio.yml infra/nginx/studio.conf studio/DEPLOY.md
git commit -m "chore(studio): CI and static deploy behind basic auth"
```

---

### Task S4: Каталог рішень, реєстр варіантів, стан вибору, шерінг

**Files:**
- Create: `studio/src/decisions/catalog.ts`, `registry.ts`, `index.ts`
- Create: `studio/src/selection/types.ts`, `shareLink.ts`, `store.ts`, `effective.ts`, `decisionsFile.ts`, `webhook.ts`
- Create: `studio/decisions/decisions.json` (`{"schemaVersion":1,"generatedAt":"","studioCommit":"","decisions":{}}`)
- Modify: `studio/src/pages/OverviewPage.tsx`, `DecisionPage.tsx`, `VariantFullscreenPage.tsx`
- Create: `studio/src/shell/StudioToolbar.tsx`
- Test: `studio/tests/unit/catalog.test.ts`, `shareLink.test.ts`, `store.test.ts`, `effective.test.ts`, `decisionsFile.test.ts`, `webhook.test.ts`, `studio/tests/e2e/share-link.spec.ts`

**Interfaces:**
- Consumes: `DeviceFrame`, `QrCode`, `variantUrl` (S1).
- Produces:
  ```ts
  type DecisionId = 'identity' | 'map.basemap' | 'map.initial-view' | 'map.aggregation' | 'map.pin' | 'map.cluster'
    | 'map.origin-notice' | 'audio.mobile-trigger' | 'audio.desktop-hover' | 'audio.player' | 'musician.preview'
    | 'musician.page' | 'like.button' | 'filters.region' | 'submission.form' | 'nav.structure' | 'onboarding';
  interface DecisionDef { id: DecisionId; title: string; question: string;
    group: 'identity' | 'map' | 'audio' | 'musician' | 'like' | 'filters' | 'submission' | 'nav';
    techVariantIds: readonly string[]; defaultVariant: string | null; requirementRefs: string[] }
  const DECISIONS: readonly DecisionDef[];
  type ParamValue = string | number | boolean;
  interface VariantProps { device: DeviceKind; selection: EffectiveSelection; data: FixtureData }
  interface VariantDef { decisionId: DecisionId; variantId: string; title: string; summary: string;
    pros: string[]; cons: string[]; techId?: string; devices: DeviceKind[];
    spec: string;                       // markdown: розкладка, стани, рух, доступність — потрапляє в HANDOFF.md
    defaultParams?: Record<string, ParamValue>;
    Component: React.ComponentType<VariantProps> }
  function registerVariants(defs: VariantDef[]): void;           // кидає на дублікаті decisionId+variantId
  function getVariants(id: DecisionId): VariantDef[];
  function getVariant(id: DecisionId, variantId: string): VariantDef | undefined;
  type DraftSelection = Partial<Record<DecisionId, { variant: string; params?: Record<string, ParamValue> }>>;
  interface SelectionEntry { variant: string; status: 'PROPOSED' | 'APPROVED'; approvedBy?: string; approvedAt?: string;
    params?: Record<string, ParamValue>; note?: string }
  interface DecisionsFile { schemaVersion: 1; generatedAt: string; studioCommit: string;
    decisions: Partial<Record<DecisionId, SelectionEntry>> }
  type EffectiveSelection = Record<DecisionId, { variant: string | null; source: 'draft' | 'approved' | 'default'; params: Record<string, ParamValue> }>;
  function encodeSelection(draft: DraftSelection): string;
  function decodeSelection(param: string | null, isKnown: (d: string, v: string) => boolean): { draft: DraftSelection; rejected: string[] };
  const useSelectionStore: UseBoundStore<{ draft: DraftSelection; choose(id: DecisionId, variant: string, params?: Record<string, ParamValue>): void;
    clear(id: DecisionId): void; reset(): void; importDraft(d: DraftSelection): void }>;
  function effectiveSelection(approved: DecisionsFile, draft: DraftSelection): EffectiveSelection;
  function validateDecisionsFile(json: unknown, isKnown: (d: string, v: string) => boolean): string[];
  function draftToDecisionsFile(draft: DraftSelection, approved: DecisionsFile, meta: { generatedAt: string; studioCommit: string }): DecisionsFile;
  function sendSelectionToTeam(url: string | undefined, payload: DecisionsFile, fetchFn?: typeof fetch): Promise<'sent' | 'disabled' | 'failed'>;
  ```
  Ключ localStorage: `nashi-studio:draft:v1`. Формат `?sel=`: base64url (без `=`) від JSON `{"v":1,"s":DraftSelection}`.

- [ ] **Step 1: Написати падаючі тести**

```ts
// catalog.test.ts
test('catalog matches contracts section 6 literally', () => {
  expect(DECISIONS.map(d => d.id)).toEqual(['identity','map.basemap','map.initial-view','map.aggregation','map.pin',
    'map.cluster','map.origin-notice','audio.mobile-trigger','audio.desktop-hover','audio.player','musician.preview',
    'musician.page','like.button','filters.region','submission.form','nav.structure','onboarding']);
  expect(byId('audio.mobile-trigger').techVariantIds).toEqual(['TAP_PREVIEW_AND_PLAY','TAP_PREVIEW_PLAY_BUTTON','LONG_PRESS_PLAY']);
  expect(byId('audio.mobile-trigger').defaultVariant).toBe('TAP_PREVIEW_AND_PLAY');           // D-25
  expect(byId('map.aggregation').defaultVariant).toBe('REGION_PLACE_PIN');                   // D-12
  expect(byId('map.initial-view').defaultVariant).toBe('UKRAINE');                           // D-10
});

// shareLink.test.ts
const known = (d: string, v: string) => d === 'map.pin' && ['AVATAR_RING','DOT_LABEL'].includes(v);
test('round trip', () => {
  const s = encodeSelection({ 'map.pin': { variant: 'AVATAR_RING' } });
  expect(s).toMatch(/^[A-Za-z0-9_-]+$/);
  expect(decodeSelection(s, known)).toEqual({ draft: { 'map.pin': { variant: 'AVATAR_RING' } }, rejected: [] });
});
test('garbage is rejected as a whole', () => {
  expect(decodeSelection('%%%', known)).toEqual({ draft: {}, rejected: ['sel'] });
});
test('unknown decision or variant is dropped and listed', () => {
  const s = b64url('{"v":1,"s":{"map.pin":{"variant":"NOPE"},"foo":{"variant":"X"}}}');
  expect(decodeSelection(s, known)).toEqual({ draft: {}, rejected: ['map.pin=NOPE', 'foo'] });
});
test('other version and oversize are rejected', () => {
  expect(decodeSelection(b64url('{"v":2,"s":{}}'), known).rejected).toEqual(['sel:version']);
  expect(decodeSelection('A'.repeat(4001), known).rejected).toEqual(['sel:too-long']);
});
test('null param means nothing to import', () => {
  expect(decodeSelection(null, known)).toEqual({ draft: {}, rejected: [] });
});

// store.test.ts — localStorage, що кидає на кожен доступ (приватний режим Safari): choose() працює в пам'яті, без винятку.

// effective.test.ts
test('draft wins over approved, approved over default', () => {
  const approved = file({ 'map.pin': { variant: 'DOT_LABEL', status: 'APPROVED', approvedBy: 'Замовник', approvedAt: '2026-12-10' } });
  const eff = effectiveSelection(approved, { 'map.pin': { variant: 'AVATAR_RING' } });
  expect(eff['map.pin']).toEqual({ variant: 'AVATAR_RING', source: 'draft', params: {} });
  expect(effectiveSelection(approved, {})['map.pin'].source).toBe('approved');
  expect(effectiveSelection(file({}), {})['map.aggregation']).toEqual({ variant: 'REGION_PLACE_PIN', source: 'default', params: {} });
});

// decisionsFile.test.ts
expect(validateDecisionsFile({ schemaVersion: 2, decisions: {} }, known)).toContain('schemaVersion must be 1');
expect(validateDecisionsFile(file({ 'map.pin': { variant: 'AVATAR_RING', status: 'APPROVED' } }), known))
  .toContain('map.pin: approvedBy and approvedAt are required for APPROVED');
expect(validateDecisionsFile(file({ 'map.origin-notice': { variant: 'PIN_CARD_LABEL', status: 'PROPOSED' } }), anyKnown))
  .toContain('map.origin-notice: потрібен постійний варіант PERSISTENT_CHIP або LEGEND_BAR (D-14)');
expect(draftToDecisionsFile({ 'map.pin': { variant: 'DOT_LABEL' } }, approvedWithDotLabel, meta).decisions['map.pin']!.status)
  .toBe('APPROVED');                                                      // збіг із затвердженим не понижує статус

// webhook.test.ts
expect(await sendSelectionToTeam(undefined, f, fetchSpy)).toBe('disabled'); expect(fetchSpy).not.toHaveBeenCalled();
expect(await sendSelectionToTeam('https://hook', f, rejectingFetch)).toBe('failed');
```
`tests/e2e/share-link.spec.ts`: `/d/map.pin?sel=%%%` → сторінка рендериться, тост «Частину вибору з посилання не
розпізнано» видно; валідне посилання, створене кнопкою «Скопіювати посилання з вибором», відкрите в новому контексті
браузера, відтворює той самий вибір.

- [ ] **Step 2: Запустити — падають**

Run: `npm test -w studio` — Expected: FAIL «Cannot find module '../../src/decisions/catalog'».

- [ ] **Step 3: Реалізувати модулі**

`map.origin-notice` — особливе: `PIN_CARD_LABEL` реєструється як варіант-доповнення, а в постійних варіантах параметр
`withCardLabel: boolean` (за замовчуванням `true`); обрати `PIN_CARD_LABEL` окремо UI не дозволяє, `validateDecisionsFile` —
теж. `useSelectionStore` — Zustand `persist` з `createJSONStorage(() => safeLocalStorage())`, де `safeLocalStorage`
повертає in-memory сховище, якщо доступ до `localStorage` кидає.

- [ ] **Step 4: UI**

- `OverviewPage`: рішення згруповані за `group`, у кожного — бейдж «Затверджено» / «Запропоновано» / «Не обрано», поточний
  варіант, кількість варіантів, посилання на `/d/:id`. Лічильник «Затверджено N з 17».
- `DecisionPage`: питання рішення; варіанти поруч (desktop) або гортанням (mobile); у кожного — `DeviceFrame` mobile і
  desktop (перемикач), назва, `summary`, «За» / «Проти», кнопки «Обрати», «Відкрити на телефоні» (QR з `variantUrl`).
  Панель параметрів, якщо варіант має `defaultParams`.
- `StudioToolbar`: перемикач айдентики, «Скопіювати посилання з вибором», «Експортувати decisions.json» (завантаження
  файла з `draftToDecisionsFile`), «Надіслати команді» (лише якщо задано `VITE_DECISIONS_WEBHOOK`), «Скинути чернетку».
- `VariantFullscreenPage`: варіант на весь екран, параметр `identity` з URL, без шапки студії.
- При завантаженні будь-якої сторінки: `?sel=` → `decodeSelection` → `importDraft` → прибрати `sel` з адреси
  (`history.replaceState`) → тост, якщо `rejected` не порожній.

- [ ] **Step 5: Запустити — проходять**

Run: `npm test -w studio && npm run e2e -w studio -- share-link.spec.ts` — Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add studio/
git commit -m "feat(studio): decision catalog, variant registry, selection state and share links"
```

---

### Task S5: Токени дизайну, міст до Tailwind, контраст, тексти

**Files:**
- Create: `studio/src/tokens/required.ts`, `dtcg.ts`, `css.ts`, `contrast.ts`
- Create: `studio/scripts/build-tokens.ts`, `studio/src/styles/theme.css`
- Create: `studio/src/proto/copy/copy.uk.json`, `required.ts`, `t.ts`
- Create: `studio/src/identities/_template/tokens.json` (усі 76 токенів зі значеннями-заглушками нейтральної сірої шкали, що проходять контраст)
- Test: `studio/tests/unit/tokens.test.ts`, `contrast.test.ts`, `copy.test.ts`, `noHardcodedColors.test.ts`

**Interfaces:**
- Produces:
  - `REQUIRED_TOKEN_PATHS: readonly string[]` — рівно 76 шляхів розділу 5 (`font.size.xs, sm, md, lg, xl, 2xl, 3xl`; `space.1`…`space.12`)
  - `type FlatTokens = Record<string, { type: string; value: unknown }>`
  - `flattenTokens(tree: object): FlatTokens` — розгортає групи, успадковує `$type` від групи, розв'язує аліаси `{a.b}`
  - `missingTokens(flat: FlatTokens): string[]`
  - `tokensToCss(selector: string, flat: FlatTokens): string` — `--n-` + шлях через дефіс
  - `contrastRatio(a: string, b: string): number` (WCAG 2.x)
  - `checkContrast(flat: FlatTokens): { pair: string; ratio: number; min: number }[]` — пари й пороги з розділу 5
  - `REQUIRED_COPY_KEYS: readonly string[]`, `t(key: string, vars?: Record<string, unknown>): string`, `setCopyOverrides(o: Record<string, string>): void`
  - `theme.css`: Tailwind 4 `@theme inline` з `--color-*: initial;` (вимикає стандартну палітру) і відображенням на `--n-*`:
    утиліти `bg-bg`, `bg-surface`, `bg-surface-raised`, `text-text`, `text-muted`, `bg-accent`, `text-on-accent`, `bg-like`,
    `border-border`, `font-display`, `font-body`, `rounded-sm|md|lg|pill`, `shadow-sm|md|lg`

- [ ] **Step 1: Написати падаючі тести**

```ts
// tokens.test.ts
expect(REQUIRED_TOKEN_PATHS).toHaveLength(76);
expect(flattenTokens({ color: { $type: 'color', bg: { $value: '#ffffff' }, surface: { $value: '{color.bg}' } } }))
  .toEqual({ 'color.bg': { type: 'color', value: '#ffffff' }, 'color.surface': { type: 'color', value: '#ffffff' } });
expect(() => flattenTokens({ color: { $type: 'color', a: { $value: '{color.b}' }, b: { $value: '{color.a}' } } }))
  .toThrow('Circular alias: color.a → color.b → color.a');
const css = tokensToCss('[data-identity="demo"]', flattenTokens(template));
expect(css.startsWith('[data-identity="demo"]{')).toBe(true);
expect(css).toContain('--n-color-surface-raised:');
expect(tokensToCss(':root', { 'space.1': { type: 'dimension', value: { value: 4, unit: 'px' } } })).toContain('--n-space-1:4px;');
expect(tokensToCss(':root', { 'font.family.body': { type: 'fontFamily', value: ['Fixel Text', 'sans-serif'] } })).toContain('--n-font-family-body:"Fixel Text", sans-serif;');
expect(tokensToCss(':root', { 'motion.easing.standard': { type: 'cubicBezier', value: [0.2, 0, 0, 1] } })).toContain('cubic-bezier(0.2, 0, 0, 1)');
expect(tokensToCss(':root', { 'motion.duration.fast': { type: 'duration', value: { value: 150, unit: 'ms' } } })).toContain('--n-motion-duration-fast:150ms;');
expect(missingTokens(flattenTokens(template))).toEqual([]);

// contrast.test.ts
expect(contrastRatio('#000000', '#ffffff')).toBe(21);
expect(contrastRatio('#777777', '#ffffff')).toBeCloseTo(4.48, 2);
expect(checkContrast({ ...flattenTokens(template), 'color.text': { type: 'color', value: '#bbbbbb' } }))
  .toContainEqual(expect.objectContaining({ pair: 'color.text on color.bg', min: 4.5 }));

// copy.test.ts
for (const key of REQUIRED_COPY_KEYS) expect(copyUk).toHaveProperty([key]);
expect(t('map.originNotice')).toBe('Позначка — місце походження музиканта, а не те, де він зараз');
expect(t('musician.likesCount', { count: 1 })).toBe('1 лайк');
expect(t('musician.likesCount', { count: 3 })).toBe('3 лайки');
expect(t('musician.likesCount', { count: 5 })).toBe('5 лайків');
expect(t('musician.likesCount', { count: 11 })).toBe('11 лайків');
expect(t('musician.likesCount', { count: 21 })).toBe('21 лайк');
expect(t('no.such.key')).toBe('⟦no.such.key⟧');

// noHardcodedColors.test.ts — читає всі файли src/decisions/**, src/proto/components/**, src/flows/** і перевіряє відсутність
// /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/ та /\b(bg|text|border|fill|stroke|ring)-(red|blue|green|yellow|gray|slate|zinc|neutral|stone|orange|amber|lime|emerald|teal|cyan|sky|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b/
```
`REQUIRED_COPY_KEYS` — усі ключі розділу 8; `submit.field.<name>.label` і `.hint` для `name`, `kind`, `originPlaceId`,
`birthPlaceId`, `summary`, `genres`, `links`, `youtubeChannelUrl`, `wikipediaUrl`, `contact`, `images`; `submit.error.<code>`
для `VALIDATION_FAILED`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `RATE_LIMITED`, `INTERNAL`, `UNAVAILABLE`.

- [ ] **Step 2: Запустити — падають**

Run: `npm test -w studio` — Expected: FAIL.

- [ ] **Step 3: Реалізувати**

`contrastRatio`: відносна яскравість WCAG 2.x (канал ≤ 0.04045 → `c/12.92`, інакше `((c+0.055)/1.055)^2.4`;
`L = 0.2126R + 0.7152G + 0.0722B`), відношення `(L1+0.05)/(L2+0.05)`, округлення до 2 знаків. `t` — `intl-messageformat`
з локаллю `uk`; кеш скомпільованих повідомлень. `build-tokens.ts`: для кожної `src/identities/<id>/tokens.json` (крім
`_template`) — `flattenTokens` → `missingTokens` і `checkContrast` (будь-яка проблема → вихід з кодом 1 з переліком) →
`tokensToCss('[data-identity="<id>"]', …)`; усе в `src/identities/generated/identities.css`. Скрипт `predev` і `prebuild`
запускає `tokens`.

- [ ] **Step 4: Запустити — проходять**

Run: `npm test -w studio && npm run tokens -w studio` — Expected: PASS; `identities.css` згенеровано.

- [ ] **Step 5: Commit**

```bash
git add studio/
git commit -m "feat(studio): DTCG tokens pipeline, contrast checks and Ukrainian copy"
```

---

### Task S6: UX-аудит V1 (розділ `/audit`)

Контентна задача. Артефакт — структуровані знахідки в студії, на які посилаються варіанти. Аудит робиться на локальному
стеку V1 (`../nashi-backend/LOCAL.md`, фронтенд V1 на `http://localhost:4173`), ніколи на production.

**Files:**
- Create: `studio/src/audit/findings.ts`, `studio/scripts/capture-v1.ts`, `studio/public/audit/*.png`
- Modify: `studio/src/pages/AuditPage.tsx`
- Test: `studio/tests/unit/audit.test.ts`

**Interfaces:**
- Consumes: `DECISIONS` (S4).
- Produces: `interface AuditFinding { id: string /* 'A-01' */; area: 'map' | 'pin' | 'filters' | 'audio' | 'artist-page' | 'submission' | 'nav' | 'visual' | 'performance' | 'a11y';
  v1Ref: string /* маршрут або файл V1 */; observation: string; evidence: string[] /* /audit/*.png */;
  severity: 'high' | 'medium' | 'low'; keep: boolean; recommendation: string; affects: DecisionId[] }`;
  `const FINDINGS: AuditFinding[]`.

- [ ] **Step 1: Тест**

```ts
expect(FINDINGS.length).toBeGreaterThanOrEqual(15);
for (const f of FINDINGS) for (const d of f.affects) expect(DECISIONS.map(x => x.id)).toContain(d);
for (const id of DECISIONS.filter(d => ['map','audio','musician','submission','filters'].includes(d.group)).map(d => d.id))
  expect(FINDINGS.some(f => f.affects.includes(id))).toBe(true);
for (const f of FINDINGS) for (const e of f.evidence) expect(existsSync(`public${e}`)).toBe(true);
```

- [ ] **Step 2: Зняти скриншоти V1**

`capture-v1.ts` (Playwright, база `V1_URL`, за замовчуванням `http://localhost:4173`): `/` (mobile і desktop), `/?region=LVIV`,
`/?artist=<slug першого піна>`, `/artists/<slug>`, `/submit`, відкрита нижня шторка фільтрів на mobile, кластер у Києві
на zoom 8, список «У цій точці» на zoom 12 у Києві.

- [ ] **Step 3: Заповнити `FINDINGS` за чек-листом**

Обов'язково оцінити (кожен пункт → знахідка з `keep` і рекомендацією): стиль Frutiger Aero і його впізнаваність; карта
`dataviz-light` з українськими підписами; межі України й Крим; панель фільтрів ≥ 1024 px і нижня смуга < 1024 px;
чипи «Тип / Період / Жанр» з лічильниками (у фазі 1 — лише область); пошук на карті; бульбашки `supercluster` і їх
підписи «1,2 тис.»; стеки однакових координат і список «У цій точці» (сотні людей у Києві); `PinPreviewDrawer`;
глибоке посилання `?artist=`; відсутність звуку з карти (у V1 аудіо лише на сторінці і в радіо); сторінка артиста
(розділи, `ListenSection`, `WorksGallery`, атрибуція Вікіпедії); форма V1 (обов'язкові email, координати, учасники,
демо-треки; пошук міста через AI); фонове надсилання з віджетом прогресу; навігація (шапка, мобільне меню-«taskbar»);
продуктивність (усі піни одним запитом); доступність (aria-мітки маркерів, фокус); що з V1 не переходить у V2 (події,
новини, радіо, візуальні митці, письменники).

- [ ] **Step 4: Сторінка `/audit`**

Фільтр за `area` і `severity`, картки знахідок зі скриншотами, посилання з кожної знахідки на `/d/:decisionId`, і навпаки —
на `DecisionPage` блок «Що ми бачили у V1» зі знахідками, де `affects` містить це рішення.

- [ ] **Step 5: Тест проходить, чек-лист прийому**

Run: `npm test -w studio -- audit` — Expected: PASS. Прийом: Д1 і Д2 переглянули всі знахідки; кожна з `severity: 'high'` має
рекомендацію, на яку посилається хоча б один варіант (`spec` варіанту згадує `A-NN`).

- [ ] **Step 6: Commit**

```bash
git add studio/
git commit -m "feat(studio): V1 UX audit section"
```

---

### Task S7: Напрями айдентики (рішення `identity`)

Контентна задача. Чотири напрями: три нових і V1 як точка відліку. Робочі назви (дизайнер може змінити назви, не ID):

| ID | Назва | Концепція (бриф) |
|---|---|---|
| `v1-aero` | «V1 Aero» | Перенесення токенів V1 (`DESIGN_SYSTEM.md`: небо `#1f6fc5→#e8f7ff`, аква `#00a2e8`/`#0a6cc2`, трава `#5fbf2a`, текст `#1e2b3c`, заголовки `#003399`) у 76 токенів. Еталон для порівняння, не кандидат за замовчуванням. |
| `ornament` | «Сучасний орнамент» | Графічний етно: геометрія вишивки як система піктограм і рамок пінів; теплий світлий фон, червоний і чорний; виразний гротеск для заголовків. |
| `nichna-karta` | «Нічна карта звуку» | Темна карта, піни світяться, коли звучать; бурштиновий акцент (відсилка до «Luminous Amber» з V1); відчуття нічного ефіру. |
| `atlas` | «Редакційний атлас» | Світлий «паперовий» атлас: серифні заголовки, тонкі лінії, стримана палітра, акцент — синій друкарський; поважний тон для історичних постатей. |

**Files:**
- Create: для кожного ID — `studio/src/identities/<id>/tokens.json`, `meta.ts`, `copy.uk.json` (лише перекриття тону: `app.tagline`, `map.originNotice`, `submit.intro`, `submit.success`), `assets/logo.svg`, `assets/logo-mark.svg`, `assets/favicon.svg`, `assets/fonts/*.woff2`, `assets/fonts/LICENSE-<font>.txt`, `moodboard/*.webp` (≤ 8 зображень з джерелами в `meta.ts`)
- Create: `studio/src/identities/index.ts`, `studio/src/shell/IdentitySwitcher.tsx`
- Create: `studio/src/decisions/identity/index.ts`, `studio/src/decisions/identity/IdentityShowcase.tsx`
- Test: `studio/tests/unit/identities.test.ts`, `studio/tests/e2e/identity.spec.ts`

**Interfaces:**
- Consumes: `flattenTokens`, `missingTokens`, `checkContrast` (S5); `registerVariants` (S4).
- Produces: `interface IdentityMeta { id: string; title: string; concept: string; keywords: string[]; voice: { tone: string; examples: string[] };
  fonts: { family: string; role: 'display' | 'body'; license: 'OFL-1.1' | 'commercial'; source: string; files: string[] }[]; moodboardCredits: string[] }`;
  `IDENTITIES: IdentityMeta[]`; варіанти рішення `identity` з `variantId` = ID напряму.

- [ ] **Step 1: Падаючий тест**

```ts
for (const id of ['v1-aero', 'ornament', 'nichna-karta', 'atlas']) {
  const flat = flattenTokens(readJson(`src/identities/${id}/tokens.json`));
  expect(missingTokens(flat)).toEqual([]);
  expect(checkContrast(flat)).toEqual([]);
  const meta = IDENTITIES.find(m => m.id === id)!;
  for (const f of meta.fonts) {
    expect(existsSync(`src/identities/${id}/assets/fonts/LICENSE-${slug(f.family)}.txt`)).toBe(true);
    for (const file of f.files) {
      const font = fontkit.openSync(`src/identities/${id}/assets/fonts/${file}`);
      for (const cp of [0x0491, 0x0454, 0x0456, 0x0457, 0x02bc, 0x20b4])   // ґ є і ї ʼ ₴
        expect(font.hasGlyphForCodePoint(cp)).toBe(true);
    }
  }
  expect(existsSync(`src/identities/${id}/assets/logo.svg`)).toBe(true);
}
expect(getVariants('identity').map(v => v.variantId).sort()).toEqual(['atlas', 'nichna-karta', 'ornament', 'v1-aero']);
```

- [ ] **Step 2: Запустити — падає**

Run: `npm test -w studio -- identities` — Expected: FAIL «ENOENT … ornament/tokens.json».

- [ ] **Step 3: Створити напрями**

Для кожного: мудборд → логотип (повний і знак, читається в 32 px; варіант для темного фону всередині `logo.svg` не
потрібен — колір через `currentColor`) → палітра (усі кольорові токени, включно з картою, пінами, кластерами) →
типографіка (лише шрифти з кирилицею і вебліцензією; кандидати для перевірки: Fixel — MacPaw, OFL 1.1; Unbounded — OFL;
e-Ukraine — перевірити умови; KyivType — перевірити умови; для `atlas` — серифна з кирилицею під OFL) → форма, рух,
розміри → тон голосу (`voice` і `copy.uk.json`). `IdentityShowcase` показує: логотип, свотчі з числами контрасту,
специмен з панграмою «Чуєш їх, доцю, га? Кумедна ж ти, прощайся без ґольфів!», зразки піна, бульбашки, картки, кнопки
лайку і поля форми — усе на токенах.

- [ ] **Step 4: Перемикач айдентики**

`IdentitySwitcher` змінює `data-identity` на кореневому елементі кожного `DeviceFrame` і на `/v/...`; вибір зберігається
в `?identity=` і в чернетці (рішення `identity`). Шрифти підключаються `@font-face` у `identities/<id>/fonts.css` під
селектором напряму.

- [ ] **Step 5: e2e і чек-лист прийому**

`identity.spec.ts`: для кожного напряму на `mobile` і `desktop` — `toHaveScreenshot('identity-<id>-<device>.png')` і axe
без `serious`/`critical`. Прийом: на Демо 1 замовник бачить кожен напрям і на showcase, і на прототипі карти (S8) у
перемикачі; Д2 має для кожного напряму 3 речення обґрунтування в `concept`.

- [ ] **Step 6: Commit**

```bash
git add studio/
git commit -m "feat(studio): four identity directions with tokens, fonts and showcase"
```

---

### Task S8: Прототипний двигун карти

Логіка, спільна для всіх варіантів карти; суворий TDD. Компонент `ProtoMap` — тонка обгортка над `react-map-gl/maplibre`
з неконтрольованою камерою (D-16).

**Files:**
- Create: `studio/src/proto/data/types.ts`, `studio/src/proto/data/fixtures.ts`
- Create: `studio/src/proto/map/aggregation.ts`, `placeIndex.ts`, `pinIndex.ts`, `camera.ts`, `regionAt.ts`, `mapStyle.ts`, `overlay.ts`, `format.ts`, `ProtoMap.tsx`
- Create: `studio/src/decisions/map.basemap/templates/light-minimal.json`, `night.json`, `atlas-paper.json`, `offline.json`
- Test: `studio/tests/unit/aggregation.test.ts`, `placeIndex.test.ts`, `pinIndex.test.ts`, `camera.test.ts`, `regionAt.test.ts`, `mapStyle.test.ts`, `format.test.ts`, `studio/tests/e2e/map-engine.spec.ts`

**Interfaces:**
- Consumes: фікстури й геодані S2; `FlatTokens` (S5).
- Produces:
  ```ts
  // data/types.ts — дзеркало розділу 2 контрактів: Region, MusicianKind, MapPin, MapPinsResponse, RegionStat, PlaceStat,
  // FilterDefinition, MusicianListItem, MusicianPage, MusicianDetails, Fragment, PlaceSuggestion, SubmissionRequest, Problem
  interface FixtureData { pins: MapPin[]; regions: RegionStat[]; places: PlaceStat[]; filters: FilterDefinition[];
    placesSearch: PlaceSuggestion[]; regionsGeo: FeatureCollection; outlineGeo: FeatureCollection; regionsMeta: RegionMeta[];
    loadMusician(slug: string): Promise<MusicianDetails | null>; audioCoverage: 'realistic' | 'all' }
  type AggregationMode = 'REGION_PLACE_PIN' | 'PLACE_PIN' | 'CHOROPLETH_PLACE_PIN';
  interface Thresholds { regionMaxZoom: number; placeMaxZoom: number }        // за замовчуванням 7 і 10
  function aggregationLevel(zoom: number, mode: AggregationMode, t: Thresholds): 'region' | 'place' | 'pin';
  function regionItems(regions: RegionStat[], selected: ReadonlySet<Region>): RegionStat[];
  type PlaceItem = { kind: 'place'; placeId: string; name: string; count: number; lon: number; lat: number }
    | { kind: 'place-cluster'; id: number; count: number; lon: number; lat: number; topName: string };
  function createPlaceIndex(places: PlaceStat[]): PlaceIndex;                 // supercluster radius 50, maxZoom 12, sum count, topName = найбільший
  function placeItems(index: PlaceIndex, bbox: BBox, zoom: number): PlaceItem[];
  type PinItem = { kind: 'pin'; pin: MapPin } | { kind: 'pin-cluster'; id: number; count: number; lon: number; lat: number };
  function filterPins(pins: MapPin[], f: { regions: ReadonlySet<Region> }): MapPin[];
  function createPinIndex(pins: MapPin[]): PinIndex;                         // radius 40, maxZoom 15
  function pinItems(index: PinIndex, bbox: BBox, zoom: number): PinItem[];
  function expansionZoom(index: PinIndex | PlaceIndex, clusterId: number): number;
  function capItems<T>(items: T[], max?: number): { items: T[]; overflow: number };   // max = 300
  interface Insets { top?: number; right?: number; bottom?: number; left?: number }
  type CameraMove = { kind: 'fitBounds'; bounds: [[number, number], [number, number]]; padding: Required<Insets>; duration: number }
    | { kind: 'flyTo' | 'easeTo'; center: [number, number]; zoom: number; duration: number };
  function cameraForRegion(meta: RegionMeta, insets: Insets, reducedMotion: boolean): CameraMove;
  function cameraForPlace(p: { lon: number; lat: number }, reducedMotion: boolean): CameraMove;
  function cameraForUkraine(outlineBbox: BBox, insets: Insets, reducedMotion: boolean): CameraMove;
  function cameraForCluster(lon: number, lat: number, expZoom: number, reducedMotion: boolean): CameraMove;
  function regionAt(lon: number, lat: number, regions: FeatureCollection): Region | null;
  function renderMapStyle(template: object, tokens: FlatTokens, urls: { tilesUrl: string; glyphsUrl: string }): StyleSpecification;
  function overlayLayers(tokens: FlatTokens): LayerSpecification[];          // 'region-fill-active', 'region-line', 'ua-outline-line'
  function formatCount(n: number): string;
  function bubbleSize(count: number, maxCount: number, min?: number, max?: number): number;  // 32..72 px, логарифмічно
  <ProtoMap data mode thresholds selectedRegions insets basemapTemplate tokens initialView
    renderRegion renderPlace renderPin renderCluster onPinEnter onPinLeave onPinTap onPinLongPress playingId debugHandle? />
  ```
  `ProtoMap` у dev/test-збірці виставляє `window.__studioMap = { setZoom(z), flyTo(lon, lat, z), getZoom() }` для e2e.

- [ ] **Step 1: Падаючі тести логіки**

```ts
// aggregation.test.ts
const T = { regionMaxZoom: 7, placeMaxZoom: 10 };
expect(aggregationLevel(6.99, 'REGION_PLACE_PIN', T)).toBe('region');
expect(aggregationLevel(7, 'REGION_PLACE_PIN', T)).toBe('place');
expect(aggregationLevel(9.99, 'REGION_PLACE_PIN', T)).toBe('place');
expect(aggregationLevel(10, 'REGION_PLACE_PIN', T)).toBe('pin');
expect(aggregationLevel(5, 'PLACE_PIN', T)).toBe('place');
expect(aggregationLevel(6, 'CHOROPLETH_PLACE_PIN', T)).toBe('region');
expect(regionItems(regions27WithZeros, new Set()).every(r => r.count > 0)).toBe(true);
expect(regionItems(regions27, new Set(['LVIV'])).map(r => r.code)).toEqual(['LVIV']);

// placeIndex.test.ts
const P = [
  { placeId: 'Q36036', name: 'Львів', region: 'LVIV', type: 'CITY', lon: 24.0315, lat: 49.8419, count: 273 },
  { placeId: 'QV', name: 'Винники', region: 'LVIV', type: 'TOWN', lon: 24.13, lat: 49.81, count: 3 },
  { placeId: 'Q1899', name: 'Київ', region: 'KYIV_CITY', type: 'CITY', lon: 30.5236, lat: 50.45, count: 604 },
];
const idx = createPlaceIndex(P);
const z7 = placeItems(idx, UKRAINE_BBOX, 7);
expect(z7).toContainEqual(expect.objectContaining({ kind: 'place-cluster', count: 276, topName: 'Львів' }));
expect(z7).toContainEqual(expect.objectContaining({ kind: 'place', name: 'Київ', count: 604 }));
expect(placeItems(idx, UKRAINE_BBOX, 12).filter(i => i.kind === 'place')).toHaveLength(3);

// pinIndex.test.ts
expect(filterPins(pins, { regions: new Set(['KYIV_CITY']) }).every(p => p.region === 'KYIV_CITY')).toBe(true);
expect(filterPins(pins, { regions: new Set() })).toHaveLength(pins.length);
expect(capItems(Array.from({ length: 350 }, (_, i) => i))).toEqual({ items: Array.from({ length: 300 }, (_, i) => i), overflow: 50 });

// camera.test.ts
const lviv = { code: 'LVIV', name: 'Львівська область', center: [24.0, 49.7], bbox: [22.6, 48.7, 25.5, 50.6] };
expect(cameraForRegion(lviv, { bottom: 120 }, false)).toEqual({ kind: 'fitBounds', bounds: [[22.6, 48.7], [25.5, 50.6]],
  padding: { top: 40, right: 40, bottom: 160, left: 40 }, duration: 1200 });
expect(cameraForRegion(lviv, {}, true).duration).toBe(0);
expect(cameraForPlace({ lon: 24.03, lat: 49.84 }, false)).toEqual({ kind: 'flyTo', center: [24.03, 49.84], zoom: 12, duration: 1200 });
expect(cameraForCluster(30.5, 50.4, 19, false)).toEqual({ kind: 'easeTo', center: [30.5, 50.4], zoom: 17, duration: 600 });

// regionAt.test.ts (реальний contracts/geo/regions.geojson)
expect(regionAt(30.5234, 50.4501, geo)).toBe('KYIV_CITY');      // місто має пріоритет над областю
expect(regionAt(24.0297, 49.8397, geo)).toBe('LVIV');
expect(regionAt(34.10, 44.95, geo)).toBe('CRIMEA');
expect(regionAt(33.52, 44.60, geo)).toBe('SEVASTOPOL_CITY');
expect(regionAt(37.62, 55.75, geo)).toBeNull();

// mapStyle.test.ts
const style = renderMapStyle(template, tokens, { tilesUrl: 'https://tiles.example/planet', glyphsUrl: 'https://g/{fontstack}/{range}.pbf' });
expect(JSON.stringify(style)).not.toMatch(/\{\{/);
expect(() => renderMapStyle({ paint: '{{color.map.lnd}}' }, tokens, urls)).toThrow('Unknown token placeholder: color.map.lnd');
for (const tpl of ['light-minimal', 'night', 'atlas-paper']) {
  const s = loadTemplate(tpl);
  expect(symbolLayers(s).every(l => JSON.stringify(l.layout['text-field']).includes('name:uk'))).toBe(true);
  expect(visibleCountryBoundaryLayers(s)).toEqual([]);           // admin_level ≤ 2 приховано (D-15)
  expect(attributionOf(s)).toMatch(/OpenStreetMap/);
}

// format.test.ts
expect(formatCount(999)).toBe('999');
expect(formatCount(1234)).toBe('1,2 тис.');
expect(formatCount(4000)).toBe('4 тис.');
expect(bubbleSize(1, 1000)).toBe(32);
expect(bubbleSize(1000, 1000)).toBe(72);
expect(bubbleSize(Math.sqrt(1000), 1000)).toBeCloseTo(52, 0);
```

- [ ] **Step 2: Запустити — падають**

Run: `npm test -w studio` — Expected: FAIL.

- [ ] **Step 3: Реалізувати логіку**

`regionAt` перевіряє спершу `KYIV_CITY` і `SEVASTOPOL_CITY`, потім решту (`@turf/boolean-point-in-polygon`).
`bubbleSize = min + (max - min) · log10(max(count,1)) / log10(max(maxCount, 10))`. Шаблони стилів — похідні від
стилів OpenFreeMap `positron` і `dark` (BSD-3, атрибуція OSM обов'язкова): джерело `openmaptiles` з `url: "{{TILES_URL}}"`,
`glyphs: "{{GLYPHS_URL}}"`, кольори — плейсхолдери `{{color.map.*}}`, усі `text-field` —
`["coalesce", ["get", "name:uk"], ["get", "name"]]`, шари `boundary` з `admin_level ≤ 2` і `disputed` —
`visibility: none`. `offline.json` — лише фон `{{color.map.land}}` без тайлів: для детермінованих скриншот-тестів
(`VITE_STUDIO_OFFLINE_MAP=1`) і на випадок недоступності OpenFreeMap. URL за замовчуванням: тайли
`https://tiles.openfreemap.org/planet`, гліфи `https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf`.

- [ ] **Step 4: `ProtoMap` і `fixtures.ts`**

`fixtures.ts` імпортує JSON фікстур і геоданих відносними шляхами від `studio/src/proto/data/`
(`../../../../contracts/fixtures/...`), деталі музикантів — `import.meta.glob('../../../../contracts/fixtures/musicians/*.json')`
ліниво. `ProtoMap`: `maxBounds` = bbox контуру з полем 1°, `minZoom 5`, `maxZoom 17`; overlay-шари з `overlayLayers`
додаються після `style.load`; рівень агрегації перераховується на `moveend`; DOM-маркери — лише `capItems(...)`; клік
по області → `cameraForRegion`, по пункту → `cameraForPlace`, по кластеру → `cameraForCluster`; `prefers-reduced-motion`
читається через `matchMedia`.

- [ ] **Step 5: e2e двигуна**

`map-engine.spec.ts` (offline-стиль): `/v/map.aggregation/REGION_PLACE_PIN` на `mobile` — видно бульбашку області з числом,
що дорівнює `count` для `LVIV` з `map-regions.json`; `__studioMap.setZoom(8)` → видно бульбашку «Київ»;
`__studioMap.flyTo(30.52, 50.45, 13)` → у DOM ≥ 1 `[data-testid="pin"]` і ≤ 300 маркерів; тап по бульбашці області
змінює zoom > 7 протягом 1500 мс.

- [ ] **Step 6: Запустити все — проходить**

Run: `npm test -w studio && npm run e2e -w studio -- map-engine.spec.ts` — Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add studio/
git commit -m "feat(studio): prototype map engine with three-level aggregation"
```

---

### Task S9: Варіанти карти

Контентна задача на двигуні S8. Для кожного варіанту: компонент, реєстрація (`VariantDef` з `spec`), e2e-скриншот на
`mobile` і `desktop` в offline-стилі, axe, чек-лист прийому. Кожен варіант має стан «звук є» і «звуку немає» на піні (D-27).

**Files:**
- Create: `studio/src/decisions/map.basemap/{index.ts,light-minimal.tsx,night.tsx,atlas-paper.tsx}`
- Create: `studio/src/decisions/map.initial-view/{index.ts,UKRAINE.tsx,UKRAINE_WITH_NEAR_ME_PROMPT.tsx}`
- Create: `studio/src/decisions/map.aggregation/{index.ts,REGION_PLACE_PIN.tsx,PLACE_PIN.tsx,CHOROPLETH_PLACE_PIN.tsx}`
- Create: `studio/src/decisions/map.pin/{index.ts,AVATAR_RING.tsx,DROP_ICON.tsx,DOT_LABEL.tsx}`
- Create: `studio/src/decisions/map.cluster/{index.ts,BUBBLE_COUNT.tsx,BUBBLE_COUNT_LABEL.tsx}`
- Create: `studio/src/decisions/map.origin-notice/{index.ts,PERSISTENT_CHIP.tsx,LEGEND_BAR.tsx,PIN_CARD_LABEL.tsx}`
- Create: `studio/src/proto/map/nearMe.ts`, `studio/src/proto/components/OriginNotice.tsx`
- Test: `studio/tests/unit/nearMe.test.ts`, `studio/tests/e2e/map-variants.spec.ts`

**Interfaces:**
- Consumes: усе з S8; `t` (S5); `registerVariants` (S4).
- Produces: `nearMeRegion(pos: { lon: number; lat: number } | null, geo: FeatureCollection): { region: Region | null; reason: 'ok' | 'outside' | 'denied' }`;
  `<OriginNotice variant: 'PERSISTENT_CHIP' | 'LEGEND_BAR' />` з `data-testid="origin-notice"`; спільні рендерери
  `renderPin(variant)`, `renderCluster(variant)` для сценаріїв S16.

Специфікації варіантів (зміст `spec` кожного):
- `map.basemap`: `light-minimal` (мінімум доріг, приглушені підписи, акцент на пінах), `night` (темна, для напряму
  `nichna-karta`, але працює з будь-якими токенами), `atlas-paper` (паперова текстура лише кольором, тонкі лінії рельєфу
  відсутні — без растрів). Усі — підписи `name:uk`, власний кордон України.
- `map.initial-view`: `UKRAINE` — `fitBounds` контуру з відступами під UI; `UKRAINE_WITH_NEAR_ME_PROMPT` — те саме плюс
  плаваюча кнопка «Біля мене» і одноразова підказка над нею; дозвіл геолокації запитується лише по натисканню (D-10);
  відмова → тост `map.nearMeDenied`. У студії геолокація симулюється (перемикач у панелі: «Львів», «Київ», «Варшава»,
  «відмова»).
- `map.aggregation`: `REGION_PLACE_PIN` — 3 рівні D-12; `PLACE_PIN` — області пропущено; `CHOROPLETH_PLACE_PIN` —
  області заливкою інтенсивності (`color.map.region-fill-active` з прозорістю 0.15…0.6 за квантилями `count`) з числом
  у візуальному центрі.
- `map.pin`: `AVATAR_RING` — фото в колі `size.pin.md` з кільцем `color.pin.ring` / `ring-audio` (є звук) /
  `ring-playing` (звучить, з прогресом по колу); без фото — ініціал на `pin.fallback-bg`. `DROP_ICON` — краплеподібна
  позначка з іконкою ноти або ініціалом, вістря в точці піна. `DOT_LABEL` — крапка 12 px + ім'я з zoom ≥ 12; зона
  дотику 44×44 у всіх.
- `map.cluster`: `BUBBLE_COUNT` — коло `bubbleSize`, число `formatCount`; `BUBBLE_COUNT_LABEL` — число + назва
  (`topName` / назва області) під ним.
- `map.origin-notice`: `PERSISTENT_CHIP` — чип зверху карти «📍 Місце походження», тап розгортає повний текст
  `map.originNotice`; `LEGEND_BAR` — тонка смуга над нижньою шторкою з повним текстом; `PIN_CARD_LABEL` — доповнення
  (`withCardLabel`): рядок у картці-прев'ю (S11).

- [ ] **Step 1: Падаючий тест `nearMe`**

```ts
expect(nearMeRegion({ lon: 24.03, lat: 49.84 }, geo)).toEqual({ region: 'LVIV', reason: 'ok' });
expect(nearMeRegion({ lon: 21.01, lat: 52.23 }, geo)).toEqual({ region: null, reason: 'outside' });   // Варшава
expect(nearMeRegion(null, geo)).toEqual({ region: null, reason: 'denied' });
```

- [ ] **Step 2: Запустити — падає; реалізувати `nearMe.ts` — проходить**

Run: `npm test -w studio -- nearMe` — Expected: спершу FAIL, після реалізації PASS.

- [ ] **Step 3: Створити компоненти варіантів і зареєструвати**

Кожен `index.ts` викликає `registerVariants([...])` з `title`, `summary`, `pros` (≥ 2), `cons` (≥ 1), `techId` (= `variantId`
для технічних ID), `devices: ['mobile','desktop']`, `spec` (≥ 8 рядків: розкладка, стани, рух з токенів `motion.*`,
доступність — aria-мітка з `pin.ariaLabel` / `cluster.ariaLabel`, клавіатура: `Tab` по видимих пінах, `Enter` = тап).

- [ ] **Step 4: e2e і axe**

`map-variants.spec.ts`: для кожного варіанту з `getVariants` рішень `map.*` на `mobile` і `desktop` —
`toHaveScreenshot`; axe без `serious`/`critical`; для `map.pin` — `boundingBox()` елемента `[data-testid="pin-hit"]`
≥ 44×44 на `mobile`; для всіх варіантів з картою — `[data-testid="origin-notice"]` видимий.

- [ ] **Step 5: Запустити — проходить**

Run: `npm run e2e -w studio -- map-variants.spec.ts` — Expected: PASS.

- [ ] **Step 6: Чек-лист прийому (Д1)**

Кожен варіант перевірено на реальних iPhone і Android через QR; стан «звуку немає» на піні помітно відрізняється; на
Києві при zoom 13 піни не злипаються в одну точку (розкид за формулою розділу 4); переліт до області не ховає її під
шторкою.

- [ ] **Step 7: Commit**

```bash
git add studio/
git commit -m "feat(studio): map variants — basemap, initial view, aggregation, pins, clusters, origin notice"
```

---

### Task S10: Аудіо-двигун і варіанти `audio.*`

**Files:**
- Create: `studio/src/proto/audio/engine.ts`, `platform.ts`, `hoverIntent.ts`, `longPress.ts`, `mobileTrigger.ts`, `playController.ts`, `fragments.ts`, `useAudio.ts`, `SoundToggle.tsx`
- Create: `studio/src/decisions/audio.mobile-trigger/{index.ts,TAP_PREVIEW_AND_PLAY.tsx,TAP_PREVIEW_PLAY_BUTTON.tsx,LONG_PRESS_PLAY.tsx}`
- Create: `studio/src/decisions/audio.desktop-hover/{index.ts,RING_PROGRESS.tsx,EQUALIZER_BADGE.tsx}`
- Create: `studio/src/decisions/audio.player/{index.ts,MINI_BAR.tsx,IN_CARD.tsx}`
- Modify: `studio/src/shell/StudioToolbar.tsx` (перемикачі «Симулювати iPhone», «Покриття аудіо: реалістичне / усі»)
- Test: `studio/tests/unit/audioEngine.test.ts`, `playController.test.ts`, `hoverIntent.test.ts`, `longPress.test.ts`, `mobileTrigger.test.ts`, `fragments.test.ts`, `studio/tests/e2e/audio.spec.ts`

**Interfaces:**
- Consumes: `FixtureData`, `MapPin`, `Fragment` (S8); `ProtoMap` події (S8); `t` (S5).
- Produces:
  ```ts
  interface AudioLike { src: string; currentTime: number; volume: number; readonly paused: boolean;
    play(): Promise<void>; pause(): void; addEventListener(t: 'ended' | 'error', f: () => void): void }
  interface PlatformCaps { volumeWritable: boolean }
  function detectPlatform(probe: AudioLike): PlatformCaps;          // volume = 0.5 і читання назад
  type EngineStatus = 'idle' | 'playing' | 'fading' | 'blocked' | 'error';
  interface EngineState { status: EngineStatus; musicianId: string | null; clipId: string | null; startedAt: number | null; endsAt: number | null }
  interface AudioEngine { play(musicianId: string, f: Fragment): Promise<void>; stop(opts?: { fade: boolean }): void;
    unlock(): void; getState(): EngineState; subscribe(fn: (s: EngineState) => void): () => void }
  function createAudioEngine(deps: { audio: AudioLike; caps: PlatformCaps; now: () => number;
    setTimer: (fn: () => void, ms: number) => number; clearTimer: (id: number) => void }): AudioEngine;
  interface PlayController { request(musicianId: string): Promise<void>; cancel(): void }
  function createPlayController(engine: AudioEngine, getFragment: (id: string, exclude?: string) => Promise<Fragment | null>): PlayController;
  interface HoverIntent { enter(id: string): void; leave(id: string): void }
  function createHoverIntent(onFire: (id: string) => void, onLeaveFired: (id: string) => void, delayMs: number,
    timers: { setTimer: typeof setTimeout; clearTimer: typeof clearTimeout }): HoverIntent;        // delayMs = 250
  type PressResult = 'tap' | 'long' | 'cancel';
  function createLongPress(ms: number, tolerancePx: number, onResult: (r: PressResult) => void): { down(x: number, y: number, t: number): void; move(x: number, y: number): void; up(t: number): void };  // 400, 10
  type MobileMode = 'TAP_PREVIEW_AND_PLAY' | 'TAP_PREVIEW_PLAY_BUTTON' | 'LONG_PRESS_PLAY';
  interface TriggerState { cardFor: string | null; playingFor: string | null }
  type TriggerEvent = { type: 'TAP' | 'LONG_PRESS'; id: string; hasAudio: boolean } | { type: 'PLAY_BUTTON' } | { type: 'CLOSE_CARD' } | { type: 'FRAGMENT_ENDED' };
  function mobileTriggerReducer(mode: MobileMode, s: TriggerState, e: TriggerEvent): TriggerState;
  function getFixtureFragment(musicianId: string, details: MusicianDetails | null, exclude?: string): Fragment | null;
  ```

- [ ] **Step 1: Падаючі тести двигуна (fake timers, `FakeAudio`)**

```ts
// audioEngine.test.ts — F = { clipId:'c1', url:'/fixtures/audio/clip-01.wav', startSec: 7.5, durationSec: 15, fadeInMs: 300, fadeOutMs: 600, … }
test('starts at startSec with fade-in where volume is writable', async () => {
  await engine.play('m1', F);
  expect(audio.src).toBe('/fixtures/audio/clip-01.wav'); expect(audio.currentTime).toBe(7.5);
  expect(audio.volume).toBe(0); vi.advanceTimersByTime(300); expect(audio.volume).toBe(1);
});
test('stops by itself after durationSec with fade-out', async () => {
  await engine.play('m1', F);
  vi.advanceTimersByTime(14_400); expect(engine.getState().status).toBe('fading');
  vi.advanceTimersByTime(600); expect(audio.paused).toBe(true); expect(engine.getState().status).toBe('idle');
});
test('autoplay rejection becomes blocked, not an unhandled rejection', async () => {
  audio.play = () => Promise.reject(new DOMException('', 'NotAllowedError'));
  await expect(engine.play('m1', F)).resolves.toBeUndefined();
  expect(engine.getState()).toMatchObject({ status: 'blocked', musicianId: 'm1' });
});
test('one sound at a time: a new play replaces the old one', async () => {
  await engine.play('m1', F); await engine.play('m2', { ...F, clipId: 'c2', url: '/fixtures/audio/clip-02.wav' });
  expect(audio.src).toBe('/fixtures/audio/clip-02.wav'); expect(engine.getState().musicianId).toBe('m2');
  expect(audio.playCalls).toBe(2); expect(audio.activeSources).toBe(1);
});
test('iOS: no volume writes, hard stop', async () => {
  const e = engineWith({ volumeWritable: false });
  await e.play('m1', F); expect(audio.volumeWrites).toBe(0);
  e.stop({ fade: true }); expect(audio.paused).toBe(true); expect(e.getState().status).toBe('idle');
});
test('ended event before the scheduled stop returns to idle', async () => {
  await engine.play('m1', F); audio.emit('ended'); expect(engine.getState().status).toBe('idle');
});

// playController.test.ts — швидке перемикання: request('A') (фрагмент приходить через 100 мс), request('B') (через 20 мс)
// → engine.play викликано рівно один раз, з 'B'; request('C') з getFragment → null → engine.stop(), стан idle (204 = немає звуку)

// hoverIntent.test.ts
// enter('A'), leave('A') на 200 мс → onFire не викликано; enter('A') → на 250 мс onFire('A');
// enter('A'), enter('B') на 100 мс → onFire лише ('B') на 350 мс; після спрацювання leave('B') → onLeaveFired('B')

// longPress.test.ts
// down(0,0,0), up(399) → 'tap'; down(0,0,0), up(400) → 'long'; down(0,0,0), move(11,0) → 'cancel'

// mobileTrigger.test.ts
const s0 = { cardFor: null, playingFor: null };
expect(mobileTriggerReducer('TAP_PREVIEW_AND_PLAY', s0, { type: 'TAP', id: 'A', hasAudio: true })).toEqual({ cardFor: 'A', playingFor: 'A' });
expect(mobileTriggerReducer('TAP_PREVIEW_AND_PLAY', { cardFor: 'A', playingFor: 'A' }, { type: 'TAP', id: 'A', hasAudio: true })).toEqual({ cardFor: 'A', playingFor: null });
expect(mobileTriggerReducer('TAP_PREVIEW_AND_PLAY', { cardFor: 'A', playingFor: 'A' }, { type: 'TAP', id: 'B', hasAudio: true })).toEqual({ cardFor: 'B', playingFor: 'B' });
expect(mobileTriggerReducer('TAP_PREVIEW_AND_PLAY', s0, { type: 'TAP', id: 'A', hasAudio: false })).toEqual({ cardFor: 'A', playingFor: null });
expect(mobileTriggerReducer('TAP_PREVIEW_PLAY_BUTTON', s0, { type: 'TAP', id: 'A', hasAudio: true })).toEqual({ cardFor: 'A', playingFor: null });
expect(mobileTriggerReducer('TAP_PREVIEW_PLAY_BUTTON', { cardFor: 'A', playingFor: null }, { type: 'PLAY_BUTTON' })).toEqual({ cardFor: 'A', playingFor: 'A' });
expect(mobileTriggerReducer('LONG_PRESS_PLAY', s0, { type: 'LONG_PRESS', id: 'A', hasAudio: true })).toEqual({ cardFor: null, playingFor: 'A' });
expect(mobileTriggerReducer('LONG_PRESS_PLAY', s0, { type: 'TAP', id: 'A', hasAudio: true })).toEqual({ cardFor: 'A', playingFor: null });
for (const m of MODES) expect(mobileTriggerReducer(m, { cardFor: 'A', playingFor: 'A' }, { type: 'CLOSE_CARD' })).toEqual(s0);   // D-22
for (const m of MODES) expect(mobileTriggerReducer(m, { cardFor: 'A', playingFor: 'A' }, { type: 'FRAGMENT_ENDED' })).toEqual({ cardFor: 'A', playingFor: null });

// fragments.test.ts — 2 кліпи, exclude 'c1' → 'c2'; 1 кліп, exclude 'c1' → 'c1'; без кліпів → null; coverage 'all' → кліп
// для будь-якого музиканта (синтетичний clip-0N за id)
```

- [ ] **Step 2: Запустити — падають**

Run: `npm test -w studio -- audio playController hoverIntent longPress mobileTrigger fragments` — Expected: FAIL.

- [ ] **Step 3: Реалізувати**

Двигун тримає **один** `AudioLike` на весь застосунок; кожен `play` отримує зростаючий `token`, і відкладені
колбеки з чужим токеном ігноруються. Fade — кроки по 30 мс лінійно; при `volumeWritable = false` гучність не
змінюється (D-24). `unlock()` викликається з обробника першого `pointerdown`/`keydown` на сторінці і з `SoundToggle`
(D-23): він робить `play()` беззвучного 0.1-секундного фрагмента синтетичного файла і одразу `pause()`. «Симулювати
iPhone» у панелі студії примусово ставить `volumeWritable: false` і блокує `play()` до першого тапу — щоб показувати
поведінку на desktop-демо.

- [ ] **Step 4: Варіанти**

- `audio.mobile-trigger` — три варіанти як живі прототипи на `ProtoMap` з `mobileTriggerReducer` (лише `devices: ['mobile']`);
  у `spec` — переваги й ризики (див. «Відповіді на питання замовника», п. 3). Параметр `LONG_PRESS_PLAY.longPressMs = 400`.
- `audio.desktop-hover` — `RING_PROGRESS` (кільце піна заповнюється по колу за `durationSec`), `EQUALIZER_BADGE` (значок
  еквалайзера на піні + підсвічування); обидва з hover-intent 250 мс і підказкою `map.soundBlockedHint`, якщо стан
  `blocked`; `SoundToggle` у кутку карти.
- `audio.player` — `MINI_BAR` (смуга над нижньою навігацією: ім'я, назва кліпу, атрибуція джерела, ▶/■, «Інший
  фрагмент» → `exclude`), `IN_CARD` (керування лише в картці-прев'ю).
- Стан «фрагмента немає»: пін без аудіо-кільця, у картці — `musician.noAudio` замість ▶; перевіряється перемикачем
  «Покриття аудіо».

- [ ] **Step 5: e2e**

`audio.spec.ts` (`mobile`, WebKit): у варіанті `TAP_PREVIEW_AND_PLAY` тап по піну з `hasAudio` → картка відкрита і
`[data-testid="playing-indicator"]` видимий протягом 2 с; тап по іншому піну → індикатор перейшов; закриття картки →
індикатора немає. `desktop`: наведення 100 мс і відведення → звук не стартував; наведення 400 мс до першого кліку →
видно `map.soundBlockedHint`; після кліку по `SoundToggle` → наведення запускає індикатор. Жодних помилок у консолі
(`page.on('pageerror')`).

- [ ] **Step 6: Запустити — проходить; перевірка на реальних пристроях**

Run: `npm test -w studio && npm run e2e -w studio -- audio.spec.ts` — Expected: PASS. Ручна перевірка через QR на
iPhone (Safari) і Android (Chrome): одночасно не звучить два фрагменти; на iPhone старт/стоп різкі (очікувано).

- [ ] **Step 7: Commit**

```bash
git add studio/
git commit -m "feat(studio): audio engine with browser constraints and audio variants"
```

---

### Task S11: Картка-прев'ю і сторінка музиканта

**Files:**
- Create: `studio/src/proto/musician/lifeDates.ts`, `linkGroups.ts`, `TextSourceNote.tsx`, `PhotoCredit.tsx`
- Create: `studio/src/decisions/musician.preview/{index.ts,BOTTOM_SHEET.tsx,FLOATING_CARD.tsx}`
- Create: `studio/src/decisions/musician.page/{index.ts,HERO.tsx,EDITORIAL.tsx,COMPACT.tsx}`
- Create: `studio/src/proto/components/Sheet.tsx`, `Card.tsx`
- Test: `studio/tests/unit/lifeDates.test.ts`, `linkGroups.test.ts`, `studio/tests/e2e/musician.spec.ts`

**Interfaces:**
- Consumes: `MusicianDetails` (S8), аудіо (S10), `OriginNotice` (S9), `t` (S5); кнопку лайку підключає S12 через `<LikeSlot musician />`.
- Produces:
  - `formatLifeDates(d: { birth: string | null; death: string | null; activeFrom: number | null; activeTo: number | null }, kind: MusicianKind): string`
  - `groupLinks(links: { platform: LinkPlatform; group: LinkGroup; url: string }[]): { group: LinkGroup; links: { platform: LinkPlatform; url: string; label: string }[] }[]`
  - `<TextSourceNote source />` — «Текст: стаття „…“ з Вікіпедії, CC BY-SA 4.0» з посиланнями; `<PhotoCredit credit />` — «Фото: автор, ліцензія, Wikimedia Commons»
  - `data-testid` блоків сторінки (контракт з e2e і зі сценаріями S16): `musician-name`, `musician-summary`,
    `musician-images` або `musician-image-fallback`, `musician-materials`, `musician-music-links`, `musician-youtube`,
    `musician-origin`, `like-button`, `likes-count`, `origin-notice`, `text-source` (якщо `textSource`), `photo-credit` (якщо `credit`)

- [ ] **Step 1: Падаючі тести**

```ts
expect(formatLifeDates({ birth: '1872-09-23', death: '1952-11-16', activeFrom: null, activeTo: null }, 'PERSON')).toBe('23 вересня 1872 — 16 листопада 1952');
expect(formatLifeDates({ birth: '1872', death: null, activeFrom: null, activeTo: null }, 'PERSON')).toBe('нар. 1872');
expect(formatLifeDates({ birth: '1872-09', death: null, activeFrom: null, activeTo: null }, 'PERSON')).toBe('нар. вересень 1872');
expect(formatLifeDates({ birth: null, death: null, activeFrom: 1994, activeTo: null }, 'BAND')).toBe('з 1994');
expect(formatLifeDates({ birth: null, death: null, activeFrom: 1994, activeTo: 2010 }, 'BAND')).toBe('1994–2010');
expect(formatLifeDates({ birth: null, death: null, activeFrom: null, activeTo: null }, 'BAND')).toBe('');
expect(groupLinks([
  { platform: 'INSTAGRAM', group: 'SOCIAL', url: 'i' }, { platform: 'YOUTUBE', group: 'VIDEO', url: 'y' },
  { platform: 'SOUNDCLOUD', group: 'MUSIC', url: 's' }, { platform: 'SPOTIFY', group: 'MUSIC', url: 'p' },
  { platform: 'WIKIPEDIA', group: 'REFERENCE', url: 'w' },
]).map(g => [g.group, g.links.map(l => l.platform)])).toEqual([
  ['MUSIC', ['SPOTIFY', 'SOUNDCLOUD']], ['VIDEO', ['YOUTUBE']], ['REFERENCE', ['WIKIPEDIA']], ['SOCIAL', ['INSTAGRAM']],
]);   // порядок MUSIC: SPOTIFY, APPLE_MUSIC, YOUTUBE_MUSIC, DEEZER, SOUNDCLOUD, BANDCAMP
expect(groupLinks([{ platform: 'X', group: 'SOCIAL', url: 'javascript:alert(1)' }])).toEqual([]);   // лише http(s)
```

- [ ] **Step 2: Запустити — падають; реалізувати — проходять**

Run: `npm test -w studio -- lifeDates linkGroups` — Expected: FAIL, потім PASS. Місяці — у родовому відмінку для
повної дати (`вересня`) і в називному для «рік-місяць» (`вересень`).

- [ ] **Step 3: Варіанти**

- `musician.preview`: `BOTTOM_SHEET` — шторка висотою `size.sheet.peek` (фото, ім'я, місце, ▶ або `musician.noAudio`,
  кількість лайків, «Детальніше»), тягнеться вгору до 90 % висоти з коротким описом і посиланнями; закриття — свайп
  вниз, тап по карті, `Esc`. `FLOATING_CARD` — картка над нижнім краєм на mobile і колонка 400 px праворуч на desktop
  (переліт до піна зміщує центр, щоб пін не опинився під карткою — `Insets`). Обидва показують `PIN_CARD_LABEL`, якщо
  обрано `withCardLabel`.
- `musician.page`: усі блоки замовника — ім'я або назва; короткий опис (`summary`, «Читати далі» розгортає
  `description`); зображення (галерея; без фото — ініціал на токенах); музичні матеріали (кліпи з ▶ через двигун S10,
  твори, відео); посилання на музику (`groupLinks`, група `MUSIC` першою, з `musician.listenOn`); YouTube-канал
  (`musician.youtubeChannel`, окремим помітним блоком, якщо є); місце походження (`musician.originLabel.<basis>`,
  назва пункту й області, міні-карта з піном, `musician.precisionNote.CITY` для точності `CITY`, `OriginNotice`);
  кнопка лайку і кількість лайків (S12); атрибуція (`TextSourceNote`, `PhotoCredit`). Варіанти: `HERO` (велике фото на
  всю ширину, ім'я поверх), `EDITORIAL` (журнальна верстка, текст першим), `COMPACT` (усе на одному екрані телефона,
  блоки акордеоном). Desktop — дві колонки для `HERO` і `EDITORIAL`.

- [ ] **Step 4: e2e**

`musician.spec.ts`: для кожного варіанту `musician.page` × 4 фікстури (з фото й аудіо; без фото й без аудіо; гурт з
`youtubeChannelUrl`; запис від заявника з `textSource: null`) × `mobile`/`desktop` — скриншот; присутні всі обов'язкові
`data-testid` (крім `musician-youtube` / `text-source` / `photo-credit`, коли даних немає — тоді вони **відсутні**); axe.
Для `musician.preview` — відкриття з піна і закриття трьома способами.

- [ ] **Step 5: Запустити — проходить**

Run: `npm run e2e -w studio -- musician.spec.ts` — Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add studio/
git commit -m "feat(studio): musician preview and page variants with attribution"
```

---

### Task S12: Лайки — батчер і варіанти `like.button`

**Files:**
- Create: `studio/src/proto/likes/likeBatcher.ts`, `fixtureLikeServer.ts`, `useLikes.ts`
- Create: `studio/src/decisions/like.button/{index.ts,HEART_BURST.tsx,APPLAUSE.tsx,CUSTOM_SYMBOL.tsx}`, `studio/src/proto/components/LikeSlot.tsx`
- Test: `studio/tests/unit/likeBatcher.test.ts`, `studio/tests/e2e/likes.spec.ts`

**Interfaces:**
- Consumes: `MusicianDetails.likesCount` (S8); `t('musician.likesCount')` (S5).
- Produces:
  ```ts
  type SendResult = { ok: true; likesCount: number } | { ok: false; retryAfterSec?: number };
  interface LikeBatcher { tap(): void; flush(reason: 'timer' | 'max' | 'pagehide' | 'retry'): Promise<void>;
    displayed(): number; subscribe(fn: (n: number) => void): () => void; dispose(): void }
  function createLikeBatcher(o: { initial: number; send: (count: number, opts: { keepalive: boolean }) => Promise<SendResult>;
    flushMs?: number /* 600 */; batchMax?: number /* 50 */; setTimer: typeof setTimeout; clearTimer: typeof clearTimeout }): LikeBatcher;
  function createFixtureLikeServer(o?: { failWith429Every?: number; retryAfterSec?: number }): (id: string) => LikeBatcher['flush'] extends never ? never : (count: number) => Promise<SendResult>;
  <LikeSlot musician: MusicianDetails variant: 'HEART_BURST' | 'APPLAUSE' | 'CUSTOM_SYMBOL' />
  ```

- [ ] **Step 1: Падаючі тести (fake timers)**

```ts
test('three taps make one request after 600 ms', async () => {
  b.tap(); b.tap(); b.tap(); expect(b.displayed()).toBe(1003);
  vi.advanceTimersByTime(599); expect(send).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(1); expect(send).toHaveBeenCalledWith(3, { keepalive: false });
});
test('120 rapid taps → 50, 50, then 20', async () => {
  for (let i = 0; i < 120; i++) b.tap();
  await vi.advanceTimersByTimeAsync(600);
  expect(send.mock.calls.map(c => c[0])).toEqual([50, 50, 20]);
});
test('429 keeps the taps and retries after Retry-After with new taps added', async () => {
  send.mockResolvedValueOnce({ ok: false, retryAfterSec: 2 }).mockResolvedValue({ ok: true, likesCount: 1025 });
  for (let i = 0; i < 20; i++) b.tap();
  await vi.advanceTimersByTimeAsync(600); b.tap(); b.tap(); b.tap(); b.tap(); b.tap();
  await vi.advanceTimersByTimeAsync(2000);
  expect(send.mock.calls.map(c => c[0])).toEqual([20, 25]);
  expect(b.displayed()).toBe(1025);
});
test('pagehide flushes with keepalive', async () => {
  b.tap(); await b.flush('pagehide'); expect(send).toHaveBeenCalledWith(1, { keepalive: true });
});
test('displayed counter never decreases, even if the server answers lower', async () => {
  send.mockResolvedValue({ ok: true, likesCount: 900 });     // інший клієнт бачив стару копію
  const seen: number[] = []; b.subscribe(n => seen.push(n));
  b.tap(); await vi.advanceTimersByTimeAsync(600);
  expect(seen.every((n, i) => i === 0 || n >= seen[i - 1])).toBe(true);
});
```
Формула показу: `displayed = max(попереднє показане, останній likesCount сервера + невідправлені + у дорозі)`.

- [ ] **Step 2: Запустити — падають; реалізувати — проходять**

Run: `npm test -w studio -- likeBatcher` — Expected: FAIL, потім PASS. `useLikes` підписує батчер на `pagehide`
(`window.addEventListener('pagehide', …)`) і `visibilitychange` → `hidden`.

- [ ] **Step 3: Варіанти `like.button`**

`HEART_BURST` (серце + «+1», що злітає, частинки на кожен тап), `APPLAUSE` (оплески з хвилею), `CUSTOM_SYMBOL` (символ
айдентики, напр. орнаментальне серце). Спільні вимоги в `spec`: обробка на `pointerdown` (без затримки 300 мс,
`touch-action: manipulation`), анімація не блокує наступний тап, вібрація `navigator.vibrate(10)` де доступна,
`aria-label` з `musician.likeAria`, лічильник з `aria-live="polite"` оновлюється не частіше разу на 1 с для читачів
екрана, `prefers-reduced-motion` — без частинок.

- [ ] **Step 4: e2e**

`likes.spec.ts` (`mobile`): 30 тапів за 1 с → лічильник показує `початкове + 30`; перехоплення мережевого моку
(`fixtureLikeServer` пише в `window.__likeRequests`) — сума `count` дорівнює 30; з `failWith429Every: 1` перший запит
відхилено, сума після повтору — та сама 30.

- [ ] **Step 5: Запустити — проходить; commit**

Run: `npm run e2e -w studio -- likes.spec.ts` — Expected: PASS.
```bash
git add studio/
git commit -m "feat(studio): like batching prototype and like button variants"
```

---

### Task S13: Фільтр за областю і рендер розширюваних фільтрів

**Files:**
- Create: `studio/src/proto/filters/filterParams.ts`, `FilterRenderer.tsx`, `studio/src/proto/data/filters.extended.json` (майбутні критерії: `kind` `SINGLE_SELECT`, `hasAudio` `BOOLEAN`, `period` `RANGE`)
- Create: `studio/src/decisions/filters.region/{index.ts,SHEET_LIST.tsx,CHIPS_SCROLL.tsx,MAP_TAP_REGION.tsx}`
- Test: `studio/tests/unit/filterParams.test.ts`, `studio/tests/unit/FilterRenderer.test.tsx`, `studio/tests/e2e/filters.spec.ts`

**Interfaces:**
- Consumes: `FilterDefinition` (S8), `filterPins`, `cameraForRegion`, `regionAt` (S8).
- Produces:
  - `type FilterState = Record<string, string[]>`
  - `parseFilterParams(search: URLSearchParams, defs: FilterDefinition[]): FilterState`
  - `writeFilterParams(state: FilterState, defs: FilterDefinition[]): string` — `?region=LVIV,KYIV_CITY`; порядок ключів — порядок `defs`; порожні не пишуться
  - `<FilterRenderer defs state onChange />` — рендерить за `type` (`MULTI_SELECT`, `SINGLE_SELECT`, `RANGE`, `BOOLEAN`); невідомий `type` пропускає з `console.warn`

- [ ] **Step 1: Падаючі тести**

```ts
expect(parseFilterParams(new URLSearchParams('region=LVIV,KYIV_CITY,NOPE'), defs)).toEqual({ region: ['LVIV', 'KYIV_CITY'] });
expect(parseFilterParams(new URLSearchParams('foo=1'), defs)).toEqual({});
expect(writeFilterParams({ region: ['LVIV', 'KYIV_CITY'] }, defs)).toBe('?region=LVIV,KYIV_CITY');
expect(writeFilterParams({ region: [] }, defs)).toBe('');
// FilterRenderer.test.tsx: defs з невідомим type 'GEO_POLYGON' рендерить решту і не падає; filters.extended.json рендерить 4 контроли
```

- [ ] **Step 2: Запустити — падають; реалізувати — проходять**

Run: `npm test -w studio -- filterParams FilterRenderer` — Expected: FAIL, потім PASS.

- [ ] **Step 3: Варіанти**

`SHEET_LIST` — кнопка «Фільтри (n)» → нижня шторка (desktop — панель зліва 360 px) зі списком 27 регіонів, лічильники,
пошук по назві, «Скинути», «Показати N»; `CHIPS_SCROLL` — горизонтальні чипи над картою: обрані, потім топ-8 за
кількістю, «Усі області» відкриває повний список; `MAP_TAP_REGION` — на рівні областей тап по полігону обирає/знімає
область (`region-fill-active`), список дублюється в шторці для доступності. Усі: після вибору — `cameraForRegion` до
об'єднаного bbox, «Знайдено: N» (`map.resultsCount`), скидання повертає до всієї України. Перемикач студії «Показати
майбутні фільтри» підставляє `filters.extended.json` — UI має вмістити 4 критерії без переробки (D-08 / питання 8).

- [ ] **Step 4: e2e і commit**

`filters.spec.ts`: для кожного варіанту на `mobile`/`desktop` вибір «Львівська область» → у DOM лише піни/бульбашки
`LVIV`, адреса містить `region=LVIV`, лічильник = `count` з `map-regions.json`; скидання → `region` зник з адреси.
Run: `npm run e2e -w studio -- filters.spec.ts` — Expected: PASS.
```bash
git add studio/
git commit -m "feat(studio): region filter variants and extensible filter renderer"
```

---

### Task S14: Форма пропозиції музиканта

**Files:**
- Create: `studio/src/proto/submission/schema.ts`, `platform.ts`, `placeSearch.ts`, `image.ts`, `fixtureSubmit.ts`
- Create: `studio/src/decisions/submission.form/{index.ts,SINGLE_PAGE.tsx,WIZARD.tsx}`, `studio/src/proto/components/Field.tsx`, `PlaceAutocomplete.tsx`, `ImagePicker.tsx`
- Test: `studio/tests/unit/submissionSchema.test.ts`, `platform.test.ts`, `placeSearch.test.ts`, `image.test.ts`, `studio/tests/e2e/submission.spec.ts`

**Interfaces:**
- Consumes: `PlaceSuggestion[]` (S8), `t` (S5).
- Produces:
  - `submissionSchema: z.ZodType<SubmissionDraft>` — правила розділу 2.10 (повідомлення українською з `copy.uk.json`)
  - `interface SubmissionDraft extends Omit<SubmissionRequest, 'faxNumber'> { images: File[]; faxNumber: string }`
  - `detectPlatform(url: string): { platform: LinkPlatform; group: LinkGroup } | null`
  - `searchPlaces(q: string, list: PlaceSuggestion[], limit?: number): PlaceSuggestion[]`
  - `targetSize(w: number, h: number, maxSide: number): { w: number; h: number }`; `downscaleImage(file: File, maxSide = 2048): Promise<Blob>` (canvas, JPEG 0.85)
  - `type SubmitScenario = 'success' | 'VALIDATION_FAILED' | 'PAYLOAD_TOO_LARGE' | 'UNSUPPORTED_MEDIA_TYPE' | 'RATE_LIMITED' | 'INTERNAL' | 'offline'`
  - `fixtureSubmit(draft: SubmissionDraft, scenario: SubmitScenario, onProgress: (p: number) => void): Promise<{ id: string } | Problem>`

- [ ] **Step 1: Падаючі тести**

```ts
const ok = { name: 'Гурт', kind: 'BAND', originPlaceId: 'Q36036', birthPlaceId: null, summary: 'а'.repeat(30),
  genres: [], links: ['https://open.spotify.com/artist/1'], youtubeChannelUrl: null, wikipediaUrl: null,
  contact: '', consent: true, faxNumber: '', images: [jpeg(1)] };
expect(submissionSchema.safeParse(ok).success).toBe(true);
expect(errorsOf({ ...ok, summary: 'а'.repeat(29) })).toEqual({ summary: 'Мінімум 30 символів' });
expect(errorsOf({ ...ok, links: ['https://example.com'] })).toEqual({ links: 'Додайте хоча б одне посилання на музику або YouTube' });
expect(errorsOf({ ...ok, links: ['https://example.com'], youtubeChannelUrl: 'https://www.youtube.com/@band' })).toEqual({});
expect(errorsOf({ ...ok, birthPlaceId: 'Q465104' })).toEqual({ birthPlaceId: 'Місце народження — лише для людини, не для гурту' });
expect(errorsOf({ ...ok, images: [] })).toEqual({ images: 'Додайте від 1 до 5 фото' });
expect(errorsOf({ ...ok, images: Array(6).fill(jpeg(1)) })).toEqual({ images: 'Додайте від 1 до 5 фото' });
expect(errorsOf({ ...ok, images: [jpeg(11)] })).toEqual({ 'images[0]': 'Файл більший за 10 МБ' });
expect(errorsOf({ ...ok, images: [gif()] })).toEqual({ 'images[0]': 'Підходять лише JPEG, PNG або WebP' });
expect(errorsOf({ ...ok, youtubeChannelUrl: 'https://vimeo.com/x' })).toEqual({ youtubeChannelUrl: 'Потрібне посилання на YouTube' });
expect(errorsOf({ ...ok, consent: false })).toEqual({ consent: 'Потрібна згода' });

expect(detectPlatform('https://open.spotify.com/artist/x')).toEqual({ platform: 'SPOTIFY', group: 'MUSIC' });
expect(detectPlatform('https://music.youtube.com/channel/x')).toEqual({ platform: 'YOUTUBE_MUSIC', group: 'MUSIC' });
expect(detectPlatform('https://youtu.be/x')).toEqual({ platform: 'YOUTUBE', group: 'VIDEO' });
expect(detectPlatform('https://uk.wikipedia.org/wiki/X')).toEqual({ platform: 'WIKIPEDIA', group: 'REFERENCE' });
expect(detectPlatform('https://instagram.com/x')).toEqual({ platform: 'INSTAGRAM', group: 'SOCIAL' });
expect(detectPlatform('https://example.com')).toEqual({ platform: 'WEBSITE', group: 'REFERENCE' });
expect(detectPlatform('ftp://x')).toBeNull();

expect(searchPlaces('бро', places)[0].name).toBe('Броди');
expect(searchPlaces("кам’ян", places).map(p => p.name)).toContain("Кам'янець-Подільський");  // апострофи ’ ʼ ' рівнозначні
expect(searchPlaces('б', places)).toEqual([]);                                                 // < 2 символів
expect(searchPlaces('франківськ', places).map(p => p.name)).toContain('Івано-Франківськ');      // префікс будь-якого слова

expect(targetSize(4032, 3024, 2048)).toEqual({ w: 2048, h: 1536 });
expect(targetSize(1000, 800, 2048)).toEqual({ w: 1000, h: 800 });
```

- [ ] **Step 2: Запустити — падають; реалізувати — проходять**

Run: `npm test -w studio -- submissionSchema platform placeSearch image` — Expected: FAIL, потім PASS.

- [ ] **Step 3: Варіанти**

Поля — розділ 2.10 у такому порядку: ім'я/назва; тип (людина/гурт — сегментований перемикач); місто походження
(`PlaceAutocomplete`, показує область); точне місце народження (лише для людини, необов'язкове); короткий опис
(лічильник символів 30–1000); жанри (до 5 чипів); посилання (список з автодетектом платформи й іконкою; підказка, що
потрібне хоча б одне на музику або YouTube); YouTube-канал; стаття Вікіпедії; фото (1–5, превью, видалення, порядок
перетягуванням, показ «було 4,1 МБ → стало 0,6 МБ» після `downscaleImage`; `accept="image/jpeg,image/png,image/webp"`);
контакт (необов'язковий, пояснення навіщо); згода; приховане поле-пастка `faxNumber` (`tabIndex=-1`, `aria-hidden`,
`autocomplete="off"`, поза екраном). `SINGLE_PAGE` — одна сторінка з групами; `WIZARD` — 6 кроків: «Хто» → «Звідки» →
«Про музику» → «Посилання» → «Фото» → «Перевірка і згода», прогрес, «Назад» без втрати даних. Стани обох:
валідація на `blur` і при надсиланні з фокусом на перше помилкове поле; надсилання з прогресом; успіх
(`submit.success`, «Запропонувати ще одного»); помилки за кодами (`submit.error.<code>`) з повтором без втрати
введеного; офлайн. Перемикач сценарію відповіді (`SubmitScenario`) — у панелі студії.

- [ ] **Step 4: e2e і commit**

`submission.spec.ts`: для обох варіантів на `mobile`/`desktop`: порожнє надсилання → фокус на «Ім'я», видно ≥ 5 помилок;
заповнення валідними даними (фото — `tests/e2e/fixtures/photo.jpg`) → успіх; сценарій `PAYLOAD_TOO_LARGE` → повідомлення
і введені дані збережені; поле `faxNumber` не досяжне з `Tab`; axe.
Run: `npm run e2e -w studio -- submission.spec.ts` — Expected: PASS.
```bash
git add studio/
git commit -m "feat(studio): musician suggestion form variants"
```

---

### Task S15: Навігація, онбординг, системні стани

**Files:**
- Create: `studio/src/decisions/nav.structure/{index.ts,MAP_ONLY_SHEETS.tsx,TAB_BAR.tsx,TOP_BAR_MENU.tsx}`
- Create: `studio/src/decisions/onboarding/{index.ts,NONE.tsx,FIRST_VISIT_TIPS.tsx}`, `studio/src/proto/onboarding.ts`
- Create: `studio/src/pages/StatesGallery.tsx` (маршрут `/states`)
- Test: `studio/tests/unit/onboarding.test.ts`, `studio/tests/e2e/nav-states.spec.ts`

**Interfaces:**
- Consumes: усі прототипні компоненти S8–S14.
- Produces: `shouldShowOnboarding(storage: Pick<Storage, 'getItem'> | null, now: Date): boolean`, `markOnboardingSeen(storage: Pick<Storage, 'setItem'> | null): void` (ключ `nashi:onboarding:v1`); варіанти рішень `nav.structure` і `onboarding`; галерея станів.

- [ ] **Step 1: Падаючий тест і реалізація**

```ts
expect(shouldShowOnboarding(memStorage({}), now)).toBe(true);
expect(shouldShowOnboarding(memStorage({ 'nashi:onboarding:v1': '1' }), now)).toBe(false);
expect(shouldShowOnboarding(null, now)).toBe(false);          // сховище недоступне — не дратуємо щоразу
```
Run: `npm test -w studio -- onboarding` — Expected: FAIL, потім PASS.

- [ ] **Step 2: Варіанти**

- `nav.structure`: `MAP_ONLY_SHEETS` — карта на весь екран, усе інше — шторки (пошук/список, фільтри, «Запропонувати»);
  `TAB_BAR` — нижня панель «Карта / Список / Запропонувати / Про проєкт»; `TOP_BAR_MENU` — верхня смуга з логотипом і
  меню. Desktop для кожного: як розміщуються карта, панель фільтрів/списку, картка-прев'ю і сторінка музиканта (окрема
  сторінка чи бічна панель — описати в `spec`).
- `onboarding`: `NONE`; `FIRST_VISIT_TIPS` — 3 підказки: що показує карта (походження — D-14), як послухати (за обраним
  `audio.mobile-trigger`/hover), як запропонувати музиканта; пропуск одним тапом.
- `/states`: завантаження (скелетони бульбашок, без стрибків розкладки), порожній список за фільтром, тайли карти не
  завантажились (лише контур і області з `offline.json`), помилка API, офлайн, 404 музиканта, `429` лайків (тихий
  повтор — нічого не показуємо), заблокований звук.

- [ ] **Step 3: e2e і commit**

`nav-states.spec.ts`: скриншоти кожного варіанту і кожного стану на `mobile`/`desktop`; axe; онбординг з'являється при
першому відкритті й не з'являється після перезавантаження.
Run: `npm run e2e -w studio -- nav-states.spec.ts` — Expected: PASS.
```bash
git add studio/
git commit -m "feat(studio): navigation, onboarding and system state variants"
```

---

### Task S16: Наскрізні сценарії і повнота реєстру

**Files:**
- Create: `studio/src/flows/scenarios.ts`, `FlowPlayer.tsx`, `map-to-musician.tsx`, `suggest-musician.tsx`
- Modify: `studio/src/pages/FlowPage.tsx`
- Test: `studio/tests/unit/registryComplete.test.ts`, `studio/tests/unit/scenarios.test.ts`, `studio/tests/e2e/flows.spec.ts`, `studio/tests/e2e/origin-notice.spec.ts`, `studio/tests/e2e/identity-matrix.spec.ts`

**Interfaces:**
- Consumes: `effectiveSelection` (S4), усі варіанти S7–S15.
- Produces:
  ```ts
  interface FlowStep { id: string; title: string; expectation: string;
    render: (sel: EffectiveSelection, device: DeviceKind, data: FixtureData) => ReactNode;
    autoAction?: (api: FlowApi) => Promise<void> }          // напр. переліт камери для демо
  interface Scenario { id: 'map-to-musician' | 'suggest-musician'; title: string; steps: FlowStep[] }
  const SCENARIOS: Scenario[];
  ```
  `/flow/:scenarioId?device=mobile|desktop` — програвач з «Далі/Назад», прогресом, перемикачем пристрою; екрани
  збираються з **поточного** ефективного вибору (комбінатор).

Кроки `map-to-musician`: 1 Україна з кількістю (рівень `region` або `place` за `map.aggregation`) → 2 тап по Львівській
області → `fitBounds` → 3 населені пункти з кількістю → 4 тап по «Львів» → `flyTo` zoom 12 → 5 окремі піни → 6 взаємодія
з піном за `audio.mobile-trigger` (mobile) або наведення (desktop), звучить фрагмент → 7 картка-прев'ю → 8 сторінка
музиканта → 9 лайк × N → 10 повернення на карту в той самий стан. `suggest-musician`: 1 вхід з навігації → кроки форми
за `submission.form` → фото → надсилання → успіх; гілка «помилка 413 → виправлення → успіх».

- [ ] **Step 1: Падаючі тести**

```ts
// registryComplete.test.ts
for (const d of DECISIONS) {
  const ids = getVariants(d.id).map(v => v.variantId);
  expect(ids.length).toBeGreaterThanOrEqual(2);
  for (const tech of d.techVariantIds) expect(ids).toContain(tech);
  for (const v of getVariants(d.id)) { expect(v.spec.split('\n').length).toBeGreaterThanOrEqual(8); expect(v.pros.length).toBeGreaterThanOrEqual(2); }
}
// scenarios.test.ts — map-to-musician має 10 кроків, suggest-musician ≥ 6; кожен крок рендериться з дефолтним вибором без винятку
```

- [ ] **Step 2: Запустити — падають; реалізувати сценарії — проходять**

Run: `npm test -w studio -- registryComplete scenarios` — Expected: FAIL (поки бракує варіантів/кроків), потім PASS.

- [ ] **Step 3: e2e-матриці**

- `flows.spec.ts`: обидва сценарії на `mobile` і `desktop` з трьома наборами вибору (дефолтний; «альтернативний A»:
  `PLACE_PIN`, `DROP_ICON`, `TAP_PREVIEW_PLAY_BUTTON`, `FLOATING_CARD`, `EDITORIAL`, `CHIPS_SCROLL`, `WIZARD`, `TAB_BAR`;
  «альтернативний B»: `CHOROPLETH_PLACE_PIN`, `DOT_LABEL`, `LONG_PRESS_PLAY`, `BOTTOM_SHEET`, `COMPACT`, `MAP_TAP_REGION`,
  `SINGLE_PAGE`, `TOP_BAR_MENU`), пройти всі кроки, скриншот кожного кроку.
- `origin-notice.spec.ts`: на кожному кроці будь-якого сценарію, де є карта, і на кожному варіанті рішень `map.*`,
  `audio.*`, `musician.preview`, `filters.region`, `nav.structure` — `[data-testid="origin-notice"]` видимий (D-14).
- `identity-matrix.spec.ts` (нічний запуск і перед кожним демо, шардинг 4): кожен варіант × 4 айдентики × 2 пристрої —
  рендер без помилок консолі й axe без `serious`/`critical` (axe ловить і контраст тексту).

- [ ] **Step 4: Запустити — проходить; commit**

Run: `npm run e2e -w studio -- flows.spec.ts origin-notice.spec.ts` — Expected: PASS.
```bash
git add studio/
git commit -m "feat(studio): end-to-end scenarios composed from current selection"
```

---

### Task S17: Генератор handoff і сторінка `/handoff`

**Files:**
- Create: `studio/scripts/handoff.ts`, `studio/src/handoff/build.ts`, `studio/src/handoff/template.md.ts`, `studio/tests/e2e/handoff-screens.spec.ts`
- Modify: `studio/src/pages/HandoffPage.tsx`
- Test: `studio/tests/unit/handoff.test.ts`

**Interfaces:**
- Consumes: `validateDecisionsFile`, `getVariant`, `DECISIONS` (S4); `flattenTokens`, `missingTokens`, `checkContrast`, `tokensToCss` (S5); `REQUIRED_COPY_KEYS`, `copy.uk.json` (S5); `renderMapStyle` (S8); `IDENTITIES` (S7).
- Produces:
  ```ts
  interface HandoffResult { ok: boolean; errors: string[]; files: string[] }
  function buildHandoff(o: { decisions: unknown; root: string /* studio/ */; outDir: string; screens: boolean; studioCommit: string; now: Date }): Promise<HandoffResult>;
  ```
  CLI `npm run handoff -w studio [-- --no-screens]` → `studio/handoff/` за розділом 7 контрактів; при `ok: false` друкує
  `errors` і завершується з кодом 1. Запис атомарний: усе пишеться в `handoff.tmp/`, потім перейменовується.

- [ ] **Step 1: Падаючі тести (тимчасова тека)**

```ts
test('approved variant missing from the registry fails the whole run', async () => {
  const r = await buildHandoff({ ...base, decisions: file({ 'map.pin': approved('UNKNOWN') }) });
  expect(r.ok).toBe(false); expect(r.errors).toContain('map.pin=UNKNOWN: варіанта немає в реєстрі');
  expect(existsSync(join(out, 'decisions.json'))).toBe(false);          // нічого не записано частково
});
test('identity with a missing token fails', async () => {
  const r = await buildHandoff({ ...base, root: rootWithBrokenIdentity /* без color.like */ });
  expect(r.errors).toContain('identity atlas: бракує токенів color.like');
});
test('missing copy key fails', async () => {
  const r = await buildHandoff({ ...base, root: rootWithoutCopyKey('map.originNotice') });
  expect(r.errors).toContain('copy.uk.json: бракує ключа map.originNotice');
});
test('successful run writes the contract set', async () => {
  const r = await buildHandoff({ ...base, decisions: allApproved, screens: false });
  expect(r.ok).toBe(true);
  expect(r.files.sort()).toEqual(['HANDOFF.md', 'copy.uk.json', 'decisions.json', 'map-style.json', 'tokens.css', 'tokens.json',
    ...assetFiles].sort());
  const css = read('tokens.css'); expect(css.startsWith(':root{')).toBe(true);
  expect(css.match(/--n-[a-z0-9-]+:/g)).toHaveLength(76);
  const style = read('map-style.json'); expect(style).toContain('{{TILES_URL}}'); expect(style).toContain('{{GLYPHS_URL}}'); expect(style).not.toMatch(/\{\{color\./);
  const md = read('HANDOFF.md'); for (const d of DECISIONS) expect(md).toContain(`## ${d.id}`);
  expect(JSON.parse(read('decisions.json')).schemaVersion).toBe(1);
});
test('PROPOSED decisions are carried over but flagged', async () => {
  const r = await buildHandoff({ ...base, decisions: withProposed('like.button', 'APPLAUSE'), screens: false });
  expect(read('HANDOFF.md')).toContain('⚠️ like.button — НЕ ЗАТВЕРДЖЕНО');
});
```

- [ ] **Step 2: Запустити — падають; реалізувати — проходять**

Run: `npm test -w studio -- handoff` — Expected: FAIL, потім PASS.

Зміст: `tokens.json` — DTCG обраного напряму (рішення `identity`), `tokens.css` — `tokensToCss(':root', …)`;
`map-style.json` — `renderMapStyle(шаблон обраного map.basemap, токени, {tilesUrl: '{{TILES_URL}}', glyphsUrl: '{{GLYPHS_URL}}'})`;
`copy.uk.json` — тексти за замовчуванням, перекриті `identities/<id>/copy.uk.json`; `assets/` — логотипи, favicon, іконки,
шрифти з ліцензіями; `HANDOFF.md` — для кожного з 17 рішень: `## <id>`, назва, обраний варіант і `techId`, `params`,
`summary`, `spec`, посилання на скриншоти, знахідки аудиту (`A-NN`), примітка `note`; на початку — таблиця статусів і
перелік відхилень від `01-decisions.md` (якщо вибір змінює рішення за замовчуванням, напр. пороги зуму).

- [ ] **Step 3: Скриншоти**

`handoff-screens.spec.ts` (запускається генератором, якщо не `--no-screens`): для кожного **обраного** варіанту —
`handoff/screens/<decision-id>/<variant-id>--mobile.png` і `--desktop.png` (offline-стиль карти, обрана айдентика), а
також кожен крок обох сценаріїв у `handoff/screens/flows/<scenario>/<NN>-<step>--<device>.png`.

- [ ] **Step 4: Сторінка `/handoff`**

Таблиця 17 рішень зі статусом, варіантом, параметрами; блок «Що бракує до передачі» (незатверджені рішення, помилки
`validateDecisionsFile`, проблеми токенів/текстів — та сама логіка, що в `buildHandoff`, без запису файлів); команда
для генерації.

- [ ] **Step 5: Повний прогін і commit**

Run: `npm run handoff -w studio` — Expected: код 0, `studio/handoff/` містить усі файли розділу 7.
```bash
git add studio/
git commit -m "feat(studio): handoff package generator"
```

---

### Task S18: Погодження з замовником, фіксація, передача технічній команді

Процесна задача; артефакти — `studio/decisions/decisions.json`, `studio/handoff/`, протоколи демо в
`studio/decisions/sessions/<дата>.md`.

**Files:**
- Create: `studio/decisions/sessions/TEMPLATE.md`, `studio/decisions/README.md` (як змінюється файл рішень)
- Modify: `studio/decisions/decisions.json` (після кожного демо)

**Interfaces:**
- Consumes: усю студію; `draftToDecisionsFile` (S4); `buildHandoff` (S17).
- Produces: затверджений `decisions.json` (усі 17 — `APPROVED`); `studio/handoff/` у `main`; зустріч передачі з технічною
  командою; протоколи сесій.

- [ ] **Step 1: Підготувати кожне демо (Демо 1–3, фінальне)**

За 2 робочі дні — посилання на студію і список рішень, які треба ухвалити на цьому демо (з календаря вище); у
`sessions/<дата>.md` — порядок показу. Показ — на телефоні замовника через QR (`/v/...`) і на великому екрані.

- [ ] **Step 2: Фіксація на демо**

Замовник обирає в студії (чернетка) → «Скопіювати посилання з вибором» або «Експортувати decisions.json» → Д1 відкриває
PR, що змінює `studio/decisions/decisions.json`: `status: "APPROVED"`, `approvedBy` (роль, напр. «Замовник»),
`approvedAt`, `note` з посиланням на протокол сесії; CI запускає `validateDecisionsFile`. Merge PR = рішення
зафіксоване. Рішення, не ухвалене на своєму демо, отримує рішення за замовчуванням з `01-decisions.md` і
`status: "PROPOSED"` з приміткою «прийнято за замовчуванням».

- [ ] **Step 3: Синхронізація з технічною командою (щотижня, 30 хв)**

Д1 + І1 + лід технічної команди: зміни контрактів пропонуються лише PR у `docs/02-contracts.md` (+ `contracts/openapi.yaml`
технічною командою); дизайн повідомляє про варіанти поза технічними ID розділу 6 до їх затвердження, щоб технічна
команда встигла оцінити.

- [ ] **Step 4: Передача**

Run: `npm run handoff -w studio && git add studio/handoff studio/decisions && git commit -m "docs(studio): handoff v1"` —
Expected: код 0, усі 17 рішень `APPROVED`. Зустріч передачі (60 хв): огляд `HANDOFF.md`, демонстрація сценаріїв,
відповіді на питання; технічна команда виконує `npm run design:sync -w web`. Після передачі зміни — лише через новий
PR у `decisions.json` з оцінкою впливу від технічної команди (контроль змін).

---

## Ризики

| Ризик | Наслідок | Як зменшуємо |
|---|---|---|
| Ліцензії шрифтів і фото | Не можна використати в продукті | Лише OFL або куплені шрифти, файл ліцензії поруч і тест (S7); фото — Commons з атрибуцією (S11) |
| OpenFreeMap недоступний або обмежує запити | Карта без підкладки на демо | `offline.json` як запасний стиль, URL тайлів — конфігурація; для демо можна підставити ключ MapTiler V1 через `VITE_TILES_URL` |
| geoBoundaries має інший поділ (Крим/Севастополь, зміни кордонів) | Невірні області | Тест 27 кодів і точок (S2); запасний контур — `nashi-frontend/src/assets/ukraine-border.json` з V1 |
| Поведінка звуку на реальних телефонах відрізняється від емуляції | Хибний вибір `audio.mobile-trigger` | Перевірка через QR на iPhone (Safari) і Android (Chrome) у S10 і на Демо 2 |
| Замовник не встигає ухвалювати рішення | Зсув handoff | Список рішень на кожне демо заздалегідь; без рішення — дефолт з `01-decisions.md` (S18) |
| Розростання кількості варіантів | Не встигаємо за 5 тижнів | Не більше 4 варіантів на рішення; нові — лише замість наявних |
| Зміни після handoff | Переробки в продукті | Контроль змін через PR з оцінкою технічної команди (S18) |
| Розходження з контрактами технічної команди | Прототипи не відповідають API | `02-contracts.md` — єдине джерело; тест фікстур проти `openapi.yaml` (S2) |
| Тексти Вікіпедії (CC BY-SA) і фото в публічному доступі | Порушення ліцензій | Студія закрита (basic auth, noindex), атрибуція показується в прототипах |
| Скриншот-тести нестабільні через мережу | Червоний CI | Offline-стиль карти і локальні фікстури в усіх скриншот-тестах (S8) |

## Відповіді на питання замовника до дизайн-команди

1. **Терміни і вартість.** ≈ 5 тижнів командою Д1+Д2+І1+І2; 66 лд + 15 % резерву ≈ 76 лд (таблиця вище). Вартість —
   Σ(лд × ставка ролі): дизайнери 27.5 лд, дизайн-інженери 38.5 лд (+ резерв). Контрольні точки — Демо 1–3, фінальне
   погодження, handoff.
2. **Формат фіксації рішень.** Файл `studio/decisions/decisions.json` (схема — розділ 7 `02-contracts.md`): по кожному з
   17 рішень — обраний варіант, статус `PROPOSED`/`APPROVED`, хто і коли затвердив, параметри, примітка; змінюється лише
   PR дизайн-ліда з посиланням на протокол сесії. Для технічної команди — пакет `studio/handoff/` (токени DTCG і CSS,
   стиль карти, тексти, ресурси, скриншоти, `HANDOFF.md`). Замовник фіксує вибір у студії сам — посиланням із вибором
   або експортом файла.
3. **Запуск аудіофрагмента на mobile без hover** — три варіанти (живі прототипи в S10):

   | Варіант | Як працює | За | Проти |
   |---|---|---|---|
   | `TAP_PREVIEW_AND_PLAY` (дефолт D-25) | Тап по піну — картка і одразу звук; повторний тап — стоп; тап по іншому — перемикання | Найшвидший шлях до «почути»; тап — легальний жест для автоплею | Звук може здивувати в тиші; тап «просто подивитися» теж вмикає звук |
   | `TAP_PREVIEW_PLAY_BUTTON` | Тап — картка; звук — кнопкою ▶ у картці | Повний контроль, жодних несподіванок | На один тап більше; слабше відчуття «карти, що звучить» |
   | `LONG_PRESS_PLAY` | Утримання 400 мс — звук; тап — картка | Розділяє «подивитися» і «послухати» | Жест неочевидний, потребує онбордингу; конфлікт з жестами карти |

4. **Що не залежить від backend.** Усе: студія працює на фікстурах з реальних даних V1 і на зафіксованих контрактах
   (`02-contracts.md`). Від технічної команди залежать лише перевірки «наживо» після запуску staging (реальні затримки
   API, справжні прев'ю Deezer/Apple після юридичного підтвердження — D-20) і остаточні тексти Telegram-повідомлень.
