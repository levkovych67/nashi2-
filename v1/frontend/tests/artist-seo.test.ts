// Search engines get a short description and schema.org data: MusicGroup for bands, Person for everyone else.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { artistJsonLd, seoDescription } from '../src/features/artists/utils/artistSeo.ts';

const LONG_BIO =
  'Ле́ся Украї́нка (справжнє ім’я Лари́са Петрі́вна Ко́сач-Кві́тка; 25 лютого 1871, Новоград-Волинський —\n' +
  '1 серпня 1913, Сурамі) — українська письменниця, перекладачка, культурна діячка. Писала в найрізноманітніших жанрах.';

test('the description is the first 160 characters of the biography, on one line', () => {
  const description = seoDescription({ biography: LONG_BIO });
  assert.equal(description?.length, 160);
  assert.ok(LONG_BIO.replace(/\s+/g, ' ').startsWith(description!));
  assert.ok(!description!.includes('\n'));
});

test('the description falls back to the press release and is undefined without text', () => {
  assert.equal(seoDescription({ pressRelease: '  Новий альбом  ' }), 'Новий альбом');
  assert.equal(seoDescription({ biography: '   ' }), undefined);
  assert.equal(seoDescription({}), undefined);
});

test('a band is a MusicGroup with genres, years and place', () => {
  assert.deepEqual(
    artistJsonLd({
      name: 'Океан Ельзи',
      categories: ['BAND'],
      genres: ['рок'],
      foundationYear: 1994,
      city: 'Львів',
      imageUrls: ['https://upload.wikimedia.org/okean.jpg'],
      wikiUrl: 'https://uk.wikipedia.org/wiki/Океан_Ельзи',
      socialLinks: [
        { platform: 'WIKI', url: 'https://uk.wikipedia.org/wiki/Океан_Ельзи' },
        { platform: 'SPOTIFY', url: 'https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS' },
        { platform: 'OTHER', url: 'javascript:alert(1)' },
      ],
    }),
    {
      '@context': 'https://schema.org',
      '@type': 'MusicGroup',
      name: 'Океан Ельзи',
      image: 'https://upload.wikimedia.org/okean.jpg',
      sameAs: ['https://uk.wikipedia.org/wiki/Океан_Ельзи', 'https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS'],
      genre: ['рок'],
      foundingDate: '1994',
      location: { '@type': 'Place', name: 'Львів' },
    },
  );
});

test('a person gets a job title from the category, dates and birth place', () => {
  assert.deepEqual(
    artistJsonLd({
      name: 'Леся Українка',
      categories: ['WRITER'],
      birthDate: '1871-02-25',
      deathDate: '1913-08-01',
      birthPlace: 'Новоград-Волинський',
      biography: 'Українська письменниця.',
    }),
    {
      '@context': 'https://schema.org',
      '@type': 'Person',
      name: 'Леся Українка',
      description: 'Українська письменниця.',
      jobTitle: 'Письменник',
      birthDate: '1871-02-25',
      deathDate: '1913-08-01',
      birthPlace: { '@type': 'Place', name: 'Новоград-Волинський' },
    },
  );
});

test('a person of several kinds lists every job title; no kind means no job title', () => {
  assert.deepEqual(artistJsonLd({ name: 'Т', categories: ['WRITER', 'VISUAL_ARTIST'] }).jobTitle, ['Письменник', 'Художник']);
  assert.equal(artistJsonLd({ name: 'Т' })['@type'], 'Person');
  assert.equal('jobTitle' in artistJsonLd({ name: 'Т' }), false);
});
