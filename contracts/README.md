# contracts/

Спільні контракти двох команд. Нормативний опис — `docs/02-contracts.md`; зміна контракту = зміна того файлу і
`openapi.yaml` в одному коміті.

- `openapi.yaml` — OpenAPI 3.1 для `/api/v2`. Імена схем і `operationId` фіксовані: їх використовують `openapi-typescript`
  (web), Java-record-и (backend) і контракт-тест.
- `test-vectors/links.json` — `{url, platform}`: еталон визначення `LinkPlatform` за URL (backend і web).
- `test-vectors/names.json` — `{input, normalized, slug}`: еталон нормалізації імен і транслітерації (заповнюється в T7).
- `tests/` — тести самого контракту (`npm test -w contracts`).
- `fixtures/`, `geo/`, `scripts/` — створює дизайн-команда (див. `docs/02-contracts.md`, розділ 9).

Перевірка: `npm test -w contracts` та `npm run lint:api` (з кореня).
