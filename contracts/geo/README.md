# Адміністративні межі України

Студія й продукт використовують один набір із 27 одиниць — 24 області, Автономну Республіку Крим, Київ і Севастополь. Геометрія представляє адміністративні межі 2017 року, включно з морськими ділянками. Координати задано у WGS 84 у порядку довгота, широта.

| Файл | Вміст |
| --- | --- |
| `regions.geojson` | FeatureCollection із 27 Polygon або MultiPolygon. Поля `code` і `name` відповідають API V2. |
| `ukraine-outline.geojson` | FeatureCollection з одним полігоном після об’єднання всіх адміністративних одиниць, включно з Кримом і Севастополем. |
| `regions-meta.json` | Масив у порядку переліку Region. Поле `center` задає положення підпису всередині регіону, а `bbox` містить західну, південну, східну й північну межі. |

Київ має ISO UA-30 і код `KYIV_CITY`, Київська область — UA-32 і `KYIV`. Севастополь має UA-40 і `SEVASTOPOL_CITY`, АР Крим — UA-43 і `CRIMEA`.

## Джерело і ліцензія

© OpenStreetMap contributors. Геодані OpenStreetMap зібрано Wambacher і опубліковано в geoBoundaries gbOpen UKR ADM1. Ідентифікатор набору — `UKR-ADM1-14850775`, дата збірки — 12 грудня 2023 року, ревізія — `9469f09`. Вихідні геодані й три похідні файли в цій теці поширюються за Open Data Commons Open Database License 1.0. [Метадані версії](https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/9469f09/releaseData/gbOpen/UKR/ADM1/geoBoundaries-UKR-ADM1-metaData.json) · [Ліцензія ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) · [Права OpenStreetMap](https://www.openstreetmap.org/copyright) · [geoBoundaries](https://www.geoboundaries.org/)

Покажіть на карті атрибуцію [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright) · [geoBoundaries](https://www.geoboundaries.org/).

## Відтворення

З кореня `nashi_v2/` після `npm ci` завантажте повнорозмірний GeoJSON у локальну теку джерел. Його SHA-256 — `b2f9f4ad2e9be5888523219c0bb46a24332ead16b46305409098048fd361a2d6`.

```sh
mkdir -p contracts/geo/source
curl -fL https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/9469f09/releaseData/gbOpen/UKR/ADM1/geoBoundaries-UKR-ADM1.geojson -o contracts/geo/source/geoBoundaries-UKR-ADM1.geojson
shasum -a 256 contracts/geo/source/geoBoundaries-UKR-ADM1.geojson
npm run build:geo -w contracts
node --test contracts/scripts/test/build-geo.test.mjs
```

Генератор перевіряє ISO-коди, спрощує спільну топологію через mapshaper (`8% keep-shapes`), округлює до 0,0001° та об’єднує області для національного контуру. Для підпису polylabel знаходить внутрішню точку найбільшого полігона з точністю 0,01°. Перевірки опублікованих файлів працюють офлайн. За наявності вихідного файлу тест також повторює генерацію й порівнює результат.
