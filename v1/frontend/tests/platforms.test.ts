import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  PLATFORM_INFO,
  READING_PLATFORMS,
  groupLinks,
  hasListenSection,
  hasWritingsSection,
  linksForLinksSection,
  linksSectionExclude,
} from '../src/features/artists/utils/platforms.ts';

test('labels and groups', () => {
  assert.deepEqual(PLATFORM_INFO.YOUTUBE, { label: 'YouTube', group: 'listen' });
  assert.deepEqual(PLATFORM_INFO.WIKISOURCE, { label: 'Вікіджерела', group: 'about' });
  assert.deepEqual(PLATFORM_INFO.ESU, { label: 'Енциклопедія сучасної України', group: 'about' });
  assert.deepEqual(PLATFORM_INFO.CHTYVO, { label: 'Чтиво', group: 'about' });
  assert.deepEqual(PLATFORM_INFO.WIKIART, { label: 'WikiArt', group: 'about' });
  assert.deepEqual(PLATFORM_INFO.X, { label: 'X', group: 'social' });
  assert.deepEqual(PLATFORM_INFO.WEBSITE, { label: 'Офіційний сайт', group: 'other' });
  assert.deepEqual(PLATFORM_INFO.OTHER, { label: 'Посилання', group: 'other' });
});

test('groupLinks groups, keeps order, dedupes, drops unsafe', () => {
  const g = groupLinks([
    { platform: 'SPOTIFY', url: 'https://open.spotify.com/artist/1' },
    { platform: 'YOUTUBE', url: 'https://youtube.com/@a' },
    { platform: 'SPOTIFY', url: 'https://open.spotify.com/artist/1' },
    { platform: 'INSTAGRAM', url: 'javascript:alert(1)' },
    { platform: 'FACEBOOK' },
    { platform: 'FOO', url: 'https://www.example.org/x' },
    { platform: 'INSTAGRAM', url: 'http://instagram.com/a' },
  ]);
  assert.deepEqual(g.listen.map((l) => l.platform), ['SPOTIFY', 'YOUTUBE']);
  assert.equal(g.listen[0].label, 'Spotify');
  assert.deepEqual(g.social.map((l) => l.url), ['http://instagram.com/a']);
  assert.deepEqual(g.other, [{ platform: 'FOO', label: 'www.example.org', url: 'https://www.example.org/x' }]);
  assert.deepEqual(g.about, []);
});

test('a platform named like an Object.prototype member is an unknown platform', () => {
  const g = groupLinks([
    { platform: 'constructor', url: 'https://example.org/a' },
    { platform: 'toString', url: 'https://example.org/b' },
  ]);
  assert.deepEqual(
    g.other.map((l) => [l.platform, l.label]),
    [
      ['constructor', 'example.org'],
      ['toString', 'example.org'],
    ],
  );
});

test('the reading platforms, Wikisource first', () => {
  assert.deepEqual(READING_PLATFORMS, ['WIKISOURCE', 'CHTYVO', 'GOODREADS', 'OPEN_LIBRARY']);
  for (const platform of READING_PLATFORMS) assert.equal(PLATFORM_INFO[platform].group, 'about');
});

test('«Посилання» leaves out what «Слухати», «Твори» and «Роботи» already show', () => {
  const none = { hasListen: false, hasWritings: false, hasGallery: false };
  assert.deepEqual(linksSectionExclude(none), { groups: [], platforms: [] });
  assert.deepEqual(linksSectionExclude({ ...none, hasListen: true }), { groups: ['listen'], platforms: [] });
  assert.deepEqual(linksSectionExclude({ ...none, hasWritings: true }), { groups: [], platforms: READING_PLATFORMS });
  assert.deepEqual(linksSectionExclude({ ...none, hasGallery: true }), { groups: [], platforms: ['COMMONS'] });
  assert.deepEqual(linksSectionExclude({ hasListen: true, hasWritings: true, hasGallery: true }), {
    groups: ['listen'],
    platforms: [...READING_PLATFORMS, 'COMMONS'],
  });
});

test('the sections that show listen and reading links follow the categories', () => {
  assert.equal(hasListenSection(['MUSICIAN']), true);
  assert.equal(hasListenSection(['BAND']), true);
  assert.equal(hasListenSection(['VISUAL_ARTIST', 'WRITER']), false);
  assert.equal(hasListenSection(undefined), false);
  assert.equal(hasWritingsSection(['WRITER']), true);
  assert.equal(hasWritingsSection(['MUSICIAN']), false);
  assert.equal(hasWritingsSection(undefined), false);
});

const MIXED = [
  { platform: 'YOUTUBE', url: 'https://youtube.com/@a' },
  { platform: 'SPOTIFY', url: 'https://open.spotify.com/artist/1' },
  { platform: 'WIKISOURCE', url: 'https://uk.wikisource.org/wiki/A' },
  { platform: 'GOODREADS', url: 'https://www.goodreads.com/author/show/1' },
  { platform: 'DISCOGS', url: 'https://www.discogs.com/artist/1' },
  { platform: 'INSTAGRAM', url: 'https://instagram.com/a' },
  { platform: 'WIKI', url: 'https://uk.wikipedia.org/wiki/A' },
];
const platformsOf = (g: Record<string, { platform: string }[]>) =>
  Object.fromEntries(Object.entries(g).map(([group, links]) => [group, links.map((l) => l.platform)]));

test('linksForLinksSection drops the excluded groups and platforms and keeps the rest', () => {
  const both = linksForLinksSection(MIXED, linksSectionExclude({ hasListen: true, hasWritings: true, hasGallery: false }));
  assert.deepEqual(platformsOf(both), { listen: [], about: ['DISCOGS'], social: ['INSTAGRAM'], other: ['WIKI'] });

  const listenOnly = linksForLinksSection(MIXED, linksSectionExclude({ hasListen: true, hasWritings: false, hasGallery: false }));
  assert.deepEqual(platformsOf(listenOnly).about, ['WIKISOURCE', 'GOODREADS', 'DISCOGS']);
  assert.deepEqual(platformsOf(listenOnly).listen, []);
});

test('linksForLinksSection without exclusions is groupLinks', () => {
  assert.deepEqual(linksForLinksSection(MIXED), groupLinks(MIXED));
  assert.deepEqual(linksForLinksSection(MIXED, { groups: [], platforms: [] }), groupLinks(MIXED));
});
