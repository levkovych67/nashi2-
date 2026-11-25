import { test } from 'node:test';
import assert from 'node:assert/strict';
import { genreChips, pinKindLabels, pinSummary, pinYearLabel, sortByName } from '../src/features/map/utils/pinLabels.ts';
import { regionsBounds } from '../src/features/map/utils/ukraineRegions.ts';

test('kind labels: categories, legacy artist, event, unknown category', () => {
  assert.deepEqual(pinKindLabels({ type: 'ARTIST', categories: ['WRITER', 'MUSICIAN'] }), ['Письменник', 'Музикант']);
  assert.deepEqual(pinKindLabels({ type: 'ARTIST' }), ['Музикант']);
  assert.deepEqual(pinKindLabels({ type: 'EVENT', categories: ['BAND'] }), ['Подія']);
  assert.deepEqual(pinKindLabels({ type: 'ARTIST', categories: ['ACTOR', 'BAND'] }), ['Гурт']);
});

test('year label: band foundation, person birth, none for events', () => {
  assert.equal(pinYearLabel({ type: 'ARTIST', categories: ['BAND'], year: 1988 }), 'з 1988');
  assert.equal(pinYearLabel({ type: 'ARTIST', categories: ['VISUAL_ARTIST'], year: 1842 }), 'нар. 1842');
  assert.equal(pinYearLabel({ type: 'ARTIST', categories: ['WRITER'] }), null);
  assert.equal(pinYearLabel({ type: 'EVENT', year: 2026 }), null);
});

test('summary joins kinds, city and year, skipping the missing parts', () => {
  assert.equal(
    pinSummary({ type: 'ARTIST', categories: ['WRITER', 'MUSICIAN'], city: 'Харків', year: 1974 }),
    'Письменник, Музикант · Харків · нар. 1974',
  );
  assert.equal(pinSummary({ type: 'EVENT', city: 'Львів' }), 'Подія · Львів');
  assert.equal(pinSummary({ type: 'ARTIST', categories: ['BAND'] }), 'Гурт');
});

test('genre chips: the chosen ones first, even with a low count', () => {
  const counts = { рок: 50, поп: 40, джаз: 30, фолк: 1 };
  assert.deepEqual(genreChips(counts, ['фолк'], 2), ['фолк', 'рок', 'поп']);
  assert.deepEqual(genreChips(counts, ['поп', 'рок'], 3), ['поп', 'рок', 'джаз', 'фолк']);
  assert.deepEqual(genreChips(counts, [], 2), ['рок', 'поп']);
});

test('genre chips: n of the unchosen genres follow the chosen ones, even when the chosen are the most common', () => {
  const counts = { a: 10, b: 9, c: 8, d: 7, e: 6 };
  assert.deepEqual(genreChips(counts, ['a'], 2), ['a', 'b', 'c']);
  assert.deepEqual(genreChips(counts, ['b', 'a'], 2), ['b', 'a', 'c', 'd']);
  assert.deepEqual(genreChips(counts, ['e', 'zzz'], 2), ['e', 'zzz', 'a', 'b']);
});

test('sortByName follows the Ukrainian alphabet and keeps the input', () => {
  const pins = [{ name: 'Їжак' }, { name: 'Іван' }, { name: 'Ґанок' }, { name: 'Гурт' }, { name: 'Євген' }, {}];
  assert.deepEqual(
    sortByName(pins).map((p) => p.name ?? ''),
    ['', 'Ґанок', 'Гурт', 'Євген', 'Іван', 'Їжак'].sort((a, b) => a.localeCompare(b, 'uk')),
  );
  assert.equal(pins[0].name, 'Їжак');
});

test('regionsBounds: union of known regions, null without any', () => {
  assert.deepEqual(regionsBounds(['LVIV']), [[22.5, 49.0], [25.5, 50.7]]);
  const both = regionsBounds(['LVIV', 'ODESA', 'UNKNOWN']);
  assert.deepEqual(both, [[22.5, 45.2], [32.0, 50.7]]);
  assert.equal(regionsBounds(['UNKNOWN']), null);
  assert.equal(regionsBounds([]), null);
});
