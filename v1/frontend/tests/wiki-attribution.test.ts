// Artists imported from Wikipedia must credit the article and the photo, as their licenses require.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getWikiAttribution } from '../src/features/artists/utils/wikiAttribution.ts';

const OKEAN_URL = 'https://uk.wikipedia.org/wiki/%D0%9E%D0%BA%D0%B5%D0%B0%D0%BD_%D0%95%D0%BB%D1%8C%D0%B7%D0%B8';

test('artists added through the form get no attribution', () => {
  assert.equal(getWikiAttribution({ name: 'Гурт' }), null);
  assert.equal(getWikiAttribution({ source: 'WIKIPEDIA' }), null);
});

test('the article title is read from its link', () => {
  const attribution = getWikiAttribution({ source: 'WIKIPEDIA', wikiUrl: OKEAN_URL });

  assert.equal(attribution?.articleTitle, 'Океан Ельзи');
  assert.equal(attribution?.articleUrl, OKEAN_URL);
  assert.equal(attribution?.image, undefined);
});

test('the photo credit names the author and the license', () => {
  const attribution = getWikiAttribution({
    source: 'WIKIPEDIA',
    wikiUrl: OKEAN_URL,
    imageCredit: {
      author: 'Alexander Khanin',
      license: 'CC BY-SA 4.0',
      filePageUrl: 'https://commons.wikimedia.org/wiki/File:OkeanElzi25.jpg',
    },
  });

  assert.deepEqual(attribution?.image, {
    text: 'Фото: Alexander Khanin, CC BY-SA 4.0, Wikimedia Commons',
    url: 'https://commons.wikimedia.org/wiki/File:OkeanElzi25.jpg',
  });
});

test('a photo without a known author still credits the license', () => {
  const attribution = getWikiAttribution({
    source: 'WIKIPEDIA',
    wikiUrl: OKEAN_URL,
    imageCredit: { license: 'CC BY-SA 4.0' },
  });

  assert.equal(attribution?.image?.text, 'Фото: CC BY-SA 4.0, Wikimedia Commons');
  assert.equal(attribution?.image?.url, undefined);
});

test('links that are not http(s) addresses are not passed on', () => {
  assert.equal(getWikiAttribution({ source: 'WIKIPEDIA', wikiUrl: 'javascript:alert(1)' }), null);

  const attribution = getWikiAttribution({
    source: 'WIKIPEDIA',
    wikiUrl: OKEAN_URL,
    imageCredit: { license: 'CC BY-SA 4.0', filePageUrl: 'javascript:alert(1)' },
  });
  assert.equal(attribution?.image?.text, 'Фото: CC BY-SA 4.0, Wikimedia Commons');
  assert.equal(attribution?.image?.url, undefined);
});
