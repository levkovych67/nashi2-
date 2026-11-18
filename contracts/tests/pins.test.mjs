import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { pinFor, radiusMeters } from '../scripts/lib/pin.mjs';

const vectors = JSON.parse(readFileSync(fileURLToPath(new URL('../test-vectors/pins.json', import.meta.url)), 'utf8'));

test('pins.json has the 8 agreed vectors', () => {
  assert.equal(vectors.length, 8);
});

for (const v of vectors) {
  test(`pin vector: ${v.name}`, () => {
    const actual = pinFor(v.artistId, v.place, v.precision);
    assert.ok(Math.abs(actual.lat - v.expected.lat) < 1e-9, `lat ${actual.lat} vs ${v.expected.lat}`);
    assert.ok(Math.abs(actual.lon - v.expected.lon) < 1e-9, `lon ${actual.lon} vs ${v.expected.lon}`);
  });
}

test('radius rules', () => {
  assert.equal(radiusMeters('CITY', null), 3000);
  assert.equal(radiusMeters('TOWN', null), 1200);
  assert.equal(radiusMeters('VILLAGE', null), 400);
  assert.equal(radiusMeters('OTHER', null), 400);
  assert.equal(radiusMeters('CITY', 839), 8000);
  assert.equal(radiusMeters('VILLAGE', 0.01), 150);
  assert.ok(Math.abs(radiusMeters('CITY', 50) - 2393.65) < 0.01);
});
