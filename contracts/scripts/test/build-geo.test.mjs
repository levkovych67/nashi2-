import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import booleanPointInPolygon from '@turf/boolean-point-in-polygon';
import { buildGeo } from '../lib/geo.mjs';
import { REGIONS } from '../lib/regions.mjs';

const geoDir = new URL('../../geo/', import.meta.url);
const input = new URL('source/geoBoundaries-UKR-ADM1.geojson', geoDir);
const json = async (url) => JSON.parse(await readFile(url, 'utf8'));

test('published borders cover all 27 regions in contract order, with internal label positions', async () => {
  const regions = await json(new URL('regions.geojson', geoDir));
  const meta = await json(new URL('regions-meta.json', geoDir));
  const expected = REGIONS.map(({ code, name }) => ({ code, name }));
  assert.deepEqual(regions.features.map((f) => f.properties), expected);
  assert.deepEqual(meta.map(({ code, name }) => ({ code, name })), expected);
  assert.ok((await readFile(new URL('regions.geojson', geoDir))).length <= 400 * 1024);
  for (const [i, { code, center, bbox }] of meta.entries()) {
    assert.ok(booleanPointInPolygon(center, regions.features[i]), code);
    assert.ok(bbox[0] <= center[0] && center[0] <= bbox[2], code);
    assert.ok(bbox[1] <= center[1] && center[1] <= bbox[3], code);
  }
  const city = meta.find((m) => m.code === 'KYIV_CITY').bbox;
  const oblast = meta.find((m) => m.code === 'KYIV').bbox;
  assert.ok(city[0] >= oblast[0] && city[1] >= oblast[1] && city[2] <= oblast[2] && city[3] <= oblast[3]);
});

test('national border includes Crimea and both special-status cities, without regional boundary seams', async () => {
  const outline = await json(new URL('ukraine-outline.geojson', geoDir));
  const regions = await json(new URL('regions.geojson', geoDir));
  assert.equal(outline.type, 'FeatureCollection');
  assert.equal(outline.features.length, 1);
  for (const [code, point] of [
    ['KYIV_CITY', [30.5234, 50.4501]],
    ['KYIV', [30.115, 49.798]],
    ['SEVASTOPOL_CITY', [33.52, 44.60]],
    ['CRIMEA', [34.10, 44.95]],
    ['LVIV', [24.0297, 49.8397]],
  ]) {
    assert.ok(booleanPointInPolygon(point, outline.features[0]), code);
    assert.deepEqual(regions.features.filter((f) => booleanPointInPolygon(point, f)).map((f) => f.properties.code), [code]);
  }
  assert.equal(booleanPointInPolygon([28.86, 47.01], outline.features[0]), false);
  // Distinct polygons in a dissolved national outline cannot share interior border segments.
  const seen = new Set();
  const geometry = outline.features[0].geometry;
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
  for (const polygon of polygons) for (const ring of polygon) {
    for (let i = 1; i < ring.length; i++) {
      const key = [String(ring[i - 1]), String(ring[i])].sort().join('|');
      assert.ok(!seen.has(key), `Duplicate outline segment: ${key}`);
      seen.add(key);
    }
  }
});

test('builder reports missing ISO identifiers before creating output', async () => {
  const feature = (shapeName) => ({ type: 'Feature', properties: { shapeName }, geometry: null });
  await assert.rejects(
    buildGeo({ type: 'FeatureCollection', features: [feature('A-region'), feature('B-region')] }),
    (error) => /A-region/.test(error.message) && /B-region/.test(error.message),
  );
});

test('rebuilding the pinned full-resolution source reproduces all published files', { skip: !existsSync(input) }, async () => {
  const { regions, outline, meta } = await buildGeo(await json(input));
  assert.deepEqual(regions, await json(new URL('regions.geojson', geoDir)));
  assert.deepEqual(outline, await json(new URL('ukraine-outline.geojson', geoDir)));
  assert.deepEqual(meta, await json(new URL('regions-meta.json', geoDir)));
});
