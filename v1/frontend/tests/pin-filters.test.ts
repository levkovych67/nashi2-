import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EMPTY_FILTERS, pinKinds, periodOf, normalizeName, filterPins, facetCounts, topGenres, type PinFilters } from '../src/features/map/utils/pinFilters.ts';

type P = { id: string; name: string; type: string; categories?: string[]; region?: string; year?: number; genres?: string[] };
const f = (o: Partial<PinFilters>): PinFilters => ({ ...EMPTY_FILTERS, ...o });
const ids = (ps: P[]) => ps.map((p) => p.id);

const pins: P[] = [
  { id: 'a', name: 'Мар\'яна', type: 'ARTIST', categories: ['MUSICIAN'], region: 'Київ', year: 1950, genres: ['rock'] },
  { id: 'b', name: 'Гурт', type: 'ARTIST', categories: ['BAND'], region: 'Львівська', year: 2000, genres: ['pop', 'rock'] },
  { id: 'c', name: 'Без категорії', type: 'ARTIST', region: 'Київ', year: 1850 },
  { id: 'd', name: 'Фестиваль', type: 'EVENT', region: 'Київ', year: 2000, genres: ['rock'] },
];

test('pinKinds: event, categories, fallback', () => {
  assert.deepEqual(pinKinds(pins[3]), ['EVENT']);
  assert.deepEqual(pinKinds(pins[1]), ['BAND']);
  assert.deepEqual(pinKinds(pins[2]), ['MUSICIAN']);
  assert.deepEqual(pinKinds({ type: 'ARTIST', categories: [] }), ['MUSICIAN']);
});

test('periodOf bounds', () => {
  assert.equal(periodOf(1899), 'before-1900');
  assert.equal(periodOf(1900), '1900-1945');
  assert.equal(periodOf(1945), '1900-1945');
  assert.equal(periodOf(1946), '1946-1990');
  assert.equal(periodOf(1990), '1946-1990');
  assert.equal(periodOf(1991), 'from-1991');
  assert.equal(periodOf(null), null);
  assert.equal(periodOf(undefined), null);
});

test('normalizeName', () => {
  assert.equal(normalizeName("Мар'яна"), normalizeName('Марʼяна'));
  assert.equal(normalizeName("Мар'яна"), normalizeName('МАР’ЯНА'));
  assert.equal(normalizeName('Ле\u0301ся'), 'леся');
  assert.equal(normalizeName('  a   b '), 'a b');
});

test('empty filters keep everything', () => {
  assert.deepEqual(ids(filterPins(pins, EMPTY_FILTERS)), ['a', 'b', 'c', 'd']);
});

test('AND across filters, OR within one', () => {
  assert.deepEqual(ids(filterPins(pins, f({ kinds: ['MUSICIAN', 'BAND'] }))), ['a', 'b', 'c']);
  assert.deepEqual(ids(filterPins(pins, f({ kinds: ['MUSICIAN', 'BAND'], regions: ['Київ'] }))), ['a', 'c']);
  assert.deepEqual(ids(filterPins(pins, f({ kinds: ['MUSICIAN', 'BAND'], regions: ['Київ'], genres: ['rock'] }))), ['a']);
  assert.deepEqual(ids(filterPins(pins, f({ genres: ['pop', 'rock'] }))), ['a', 'b', 'd']);
});

test('q matches normalized substring', () => {
  assert.deepEqual(ids(filterPins(pins, f({ q: 'МАРʼЯ' }))), ['a']);
});

test('events ignore period and genre', () => {
  assert.deepEqual(ids(filterPins(pins, f({ periods: ['from-1991'] }))), ['b', 'd']);
  assert.deepEqual(ids(filterPins(pins, f({ genres: ['pop'] }))), ['b', 'd']);
  assert.deepEqual(ids(filterPins(pins, f({ kinds: ['EVENT'], periods: ['before-1900'], genres: ['pop'] }))), ['d']);
});

test('facets exclude their own filter', () => {
  const c = facetCounts(pins, f({ kinds: ['BAND'], regions: ['Київ'] }));
  // kinds counted with region filter only
  assert.equal(c.kinds.MUSICIAN, 2);
  assert.equal(c.kinds.BAND, 0);
  assert.equal(c.kinds.EVENT, 1);
  // regions counted with kind filter only
  assert.equal(c.regions['Львівська'], 1);
  assert.equal(c.regions['Київ'], undefined);
  // periods: kind+region applied -> nothing matches BAND in Київ
  assert.equal(c.periods['from-1991'], 0);
  const c2 = facetCounts(pins, EMPTY_FILTERS);
  assert.equal(c2.periods['before-1900'], 1);
  assert.equal(c2.genres.rock, 2);
  assert.equal(c2.regions['Київ'], 3);
});

test('topGenres keeps selected', () => {
  const counts = { a: 10, b: 9, c: 8, d: 1 };
  assert.deepEqual(topGenres(counts, [], 2), ['a', 'b']);
  assert.deepEqual(topGenres(counts, ['d'], 2), ['a', 'b', 'd']);
  assert.deepEqual(topGenres(counts, ['d', 'zzz'], 2).includes('zzz'), true);
});

test('filterPinsHandles30kPinsQuickly', () => {
  const kinds = ['MUSICIAN', 'BAND', 'VISUAL_ARTIST', 'WRITER'];
  const big: P[] = Array.from({ length: 30000 }, (_, i) => ({
    id: `p${i}`, name: `Артист Номер ${i}`, type: i % 50 === 0 ? 'EVENT' : 'ARTIST',
    categories: [kinds[i % 4]], region: `r${i % 25}`, year: 1800 + (i % 220), genres: [`g${i % 40}`, `g${(i + 7) % 40}`],
  }));
  const filt = f({ q: 'номер 1', kinds: ['MUSICIAN', 'BAND'], regions: ['r1', 'r2', 'r3'], periods: ['1946-1990', 'from-1991'], genres: ['g1', 'g2'] });
  filterPins(big, filt); // warm up
  const t0 = performance.now();
  const out = filterPins(big, filt);
  const ms = performance.now() - t0;
  console.log(`30k filterPins: ${ms.toFixed(2)} ms, ${out.length} hits`);
  // Generous on purpose: CI runs this before every deploy, on shared runners 2–3× slower than a laptop.
  assert.ok(ms < 200, `took ${ms} ms`);
});
