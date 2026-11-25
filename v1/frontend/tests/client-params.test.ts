// Array filters must reach the backend as repeated keys, not collapse to the last item.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildQuery } from '../src/lib/api/query.ts';

test('repeats the key for each array item and skips empty values', () => {
  assert.equal(
    buildQuery({ types: ['ARTIST', 'EVENT'], region: 'LVIV', empty: '' }),
    'types=ARTIST&types=EVENT&region=LVIV',
  );
});

test('skips undefined and null, keeps scalars and zero/false', () => {
  assert.equal(buildQuery({ a: undefined, b: null, c: 0, d: false, e: 'x y' }), 'c=0&d=false&e=x+y');
});

test('skips empty items inside arrays', () => {
  assert.equal(buildQuery({ t: ['A', '', null, 'B'] }), 't=A&t=B');
});
