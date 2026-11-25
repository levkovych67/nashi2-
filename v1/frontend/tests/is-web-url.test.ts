// Links from the API go into href only when they are plain web addresses.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isWebUrl } from '../src/lib/utils/isWebUrl.ts';

test('http and https addresses pass, in any case', () => {
  assert.equal(isWebUrl('https://uk.wikipedia.org/wiki/A'), true);
  assert.equal(isWebUrl('http://example.org'), true);
  assert.equal(isWebUrl('HTTPS://commons.wikimedia.org/wiki/File:A.jpg'), true);
});

test('script, data and relative addresses, and nothing at all, do not', () => {
  assert.equal(isWebUrl('javascript:alert(1)'), false);
  assert.equal(isWebUrl(' javascript:alert(1)'), false);
  assert.equal(isWebUrl('data:text/html,<script>alert(1)</script>'), false);
  assert.equal(isWebUrl('//example.org/a'), false);
  assert.equal(isWebUrl('/wiki/A'), false);
  assert.equal(isWebUrl('ftp://example.org'), false);
  assert.equal(isWebUrl(''), false);
  assert.equal(isWebUrl(undefined), false);
  assert.equal(isWebUrl(null), false);
});
