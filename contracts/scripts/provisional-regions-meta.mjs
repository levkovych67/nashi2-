// Provisional generator for contracts/geo/regions-meta.json.
// The design team's build-geo.mjs owns this file and overwrites it later; same format.
// Source: geoBoundaries gbOpen UKR ADM1 (https://www.geoboundaries.org/), downloaded into contracts/geo/source/.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import polylabel from 'polylabel';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const API = 'https://www.geoboundaries.org/api/current/gbOpen/UKR/ADM1/';
const SRC_DIR = here('../geo/source/');
const SRC = SRC_DIR + 'adm1.geojson';
const OUT = here('../geo/regions-meta.json');

// Contract order (docs/02-contracts.md section 2) -> ISO 3166-2 suffix, Ukrainian name (= Java Region.uaName()).
const REGIONS = [
  ['CRIMEA', 'UA-43', 'Автономна Республіка Крим'],
  ['VINNYTSIA', 'UA-05', 'Вінницька область'],
  ['VOLYN', 'UA-07', 'Волинська область'],
  ['DNIPROPETROVSK', 'UA-12', 'Дніпропетровська область'],
  ['DONETSK', 'UA-14', 'Донецька область'],
  ['ZHYTOMYR', 'UA-18', 'Житомирська область'],
  ['ZAKARPATTIA', 'UA-21', 'Закарпатська область'],
  ['ZAPORIZHZHIA', 'UA-23', 'Запорізька область'],
  ['IVANO_FRANKIVSK', 'UA-26', 'Івано-Франківська область'],
  ['KYIV', 'UA-32', 'Київська область'],
  ['KIROVOHRAD', 'UA-35', 'Кіровоградська область'],
  ['LUHANSK', 'UA-09', 'Луганська область'],
  ['LVIV', 'UA-46', 'Львівська область'],
  ['MYKOLAIV', 'UA-48', 'Миколаївська область'],
  ['ODESA', 'UA-51', 'Одеська область'],
  ['POLTAVA', 'UA-53', 'Полтавська область'],
  ['RIVNE', 'UA-56', 'Рівненська область'],
  ['SUMY', 'UA-59', 'Сумська область'],
  ['TERNOPIL', 'UA-61', 'Тернопільська область'],
  ['KHARKIV', 'UA-63', 'Харківська область'],
  ['KHERSON', 'UA-65', 'Херсонська область'],
  ['KHMELNYTSKYI', 'UA-68', 'Хмельницька область'],
  ['CHERKASY', 'UA-71', 'Черкаська область'],
  ['CHERNIVTSI', 'UA-77', 'Чернівецька область'],
  ['CHERNIHIV', 'UA-74', 'Чернігівська область'],
  ['KYIV_CITY', 'UA-30', 'Київ'],
  ['SEVASTOPOL_CITY', 'UA-40', 'Севастополь'],
];
// Fallback by shapeName when shapeISO is missing.
const NAME_FALLBACK = { 'UA-30': 'Kyiv', 'UA-40': 'Sevastopol', 'UA-43': 'Autonomous Republic of Crimea' };

const round = (x) => Math.round(x * 1e4) / 1e4;
const area = (ring) => {
  let a = 0;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) a += (ring[j][0] + ring[i][0]) * (ring[j][1] - ring[i][1]);
  return Math.abs(a / 2);
};

async function ensureSource() {
  const meta = await (await fetch(API)).json();
  console.log('source license (boundaryLicense):', meta.boundaryLicense, '| source:', meta.boundarySource);
  if (existsSync(SRC)) return;
  const url = meta.gjDownloadURL ?? meta.simplifiedGeometryGeoJSON;
  console.log('downloading', url, '(build', meta.buildDate + ')');
  const res = await fetch(url);
  if (!res.ok) throw new Error(`download failed: ${res.status}`);
  mkdirSync(SRC_DIR, { recursive: true });
  writeFileSync(SRC, Buffer.from(await res.arrayBuffer()));
}

await ensureSource();
const features = JSON.parse(readFileSync(SRC, 'utf8')).features;

const out = REGIONS.map(([code, iso, name]) => {
  let f = features.find((x) => x.properties.shapeISO === iso);
  if (!f) {
    const n = NAME_FALLBACK[iso] ?? name;
    f = features.find((x) => !x.properties.shapeISO && x.properties.shapeName?.startsWith(n.replace(' Oblast', '')));
    if (f) console.log(`matched ${code} by shapeName "${f.properties.shapeName}"`);
  }
  if (!f) throw new Error(`no feature for ${code} (${iso})`);
  const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
  let w = Infinity, s = Infinity, e = -Infinity, n = -Infinity;
  for (const poly of polys) for (const ring of poly) for (const [x, y] of ring) {
    if (x < w) w = x; if (x > e) e = x; if (y < s) s = y; if (y > n) n = y;
  }
  const largest = polys.reduce((a, b) => (area(b[0]) > area(a[0]) ? b : a));
  const [cx, cy] = polylabel(largest, 0.01);
  return { code, name, center: [round(cx), round(cy)], bbox: [round(w), round(s), round(e), round(n)] };
});

writeFileSync(OUT, JSON.stringify(out, null, 2) + '\n');
console.log(`wrote ${out.length} regions to ${OUT}`);
