import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const meta = JSON.parse(readFileSync(here('../geo/regions-meta.json'), 'utf8'));
const spec = parse(readFileSync(here('../openapi.yaml'), 'utf8'));
const byCode = Object.fromEntries(meta.map((m) => [m.code, m]));

test('regions-meta has 27 entries in Region enum order', () => {
  assert.equal(meta.length, 27);
  assert.deepEqual(meta.map((m) => m.code), spec.components.schemas.Region.enum);
});

test('every entry has a name, [lon,lat] center and [w,s,e,n] bbox', () => {
  for (const m of meta) {
    assert.ok(typeof m.name === 'string' && m.name.trim().length > 0, `${m.code} name`);
    assert.equal(m.center.length, 2, `${m.code} center`);
    assert.equal(m.bbox.length, 4, `${m.code} bbox`);
    const [w, s, e, n] = m.bbox;
    assert.ok(w < e && s < n, `${m.code} bbox ordering`);
  }
});

test('each center lies inside its bbox', () => {
  for (const m of meta) {
    const [x, y] = m.center;
    const [w, s, e, n] = m.bbox;
    assert.ok(x >= w && x <= e && y >= s && y <= n, `${m.code} center outside bbox`);
  }
});

test('KYIV_CITY bbox is inside KYIV bbox', () => {
  const [w, s, e, n] = byCode.KYIV_CITY.bbox;
  const [W, S, E, N] = byCode.KYIV.bbox;
  assert.ok(w >= W && s >= S && e <= E && n <= N);
});
