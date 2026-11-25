import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseFilters, writeFilters, activeFilterCount } from '../src/features/map/utils/filterParams.ts';
import { EMPTY_FILTERS } from '../src/features/map/utils/pinFilters.ts';

test('empty params give empty filters', () => {
  assert.deepEqual(parseFilters(new URLSearchParams()), EMPTY_FILTERS);
});

test('round trip', () => {
  const f = { q: 'леся', kinds: ['BAND', 'EVENT'] as const, regions: ['Київ', 'Львівська'], periods: ['1900-1945'] as const, genres: ['rock', 'pop'] };
  const filters = { ...f, kinds: [...f.kinds], periods: [...f.periods] };
  const sp = writeFilters(new URLSearchParams(), filters);
  assert.deepEqual(parseFilters(new URLSearchParams(sp.toString())), filters);
});

test('unknown values dropped', () => {
  const sp = new URLSearchParams('type=BAND,NOPE&period=bad,from-1991&region=,Київ,');
  const f = parseFilters(sp);
  assert.deepEqual(f.kinds, ['BAND']);
  assert.deepEqual(f.periods, ['from-1991']);
  assert.deepEqual(f.regions, ['Київ']);
});

test('write keeps unrelated params and removes empty ones', () => {
  const sp = new URLSearchParams('artist=lesya&q=x&type=BAND');
  const out = writeFilters(sp, EMPTY_FILTERS);
  assert.equal(out.get('artist'), 'lesya');
  assert.equal(out.has('q'), false);
  assert.equal(out.has('type'), false);
  assert.equal(sp.get('q'), 'x'); // input not mutated
});

test('activeFilterCount', () => {
  assert.equal(activeFilterCount(EMPTY_FILTERS), 0);
  assert.equal(activeFilterCount({ q: 'a', kinds: ['BAND', 'EVENT'], regions: [], periods: ['from-1991'], genres: ['x'] }), 5);
  assert.equal(activeFilterCount({ ...EMPTY_FILTERS, q: '  ' }), 0);
});
