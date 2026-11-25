// Works go to the gallery (pictures) or to the list of writings; titles and credits read the same everywhere.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { galleryWorks, workCredit, workTitle, writtenWorks } from '../src/features/artists/utils/works.ts';

const painting = { title: 'На острові Валаам', year: 1873, imageUrl: 'https://upload.wikimedia.org/a.jpg' };
const fullOnly = { title: 'Ескіз', fullImageUrl: 'https://upload.wikimedia.org/b.jpg' };
const book = { title: 'Кобзар', year: 1840, articleUrl: 'https://uk.wikipedia.org/wiki/Кобзар' };

test('a missing or blank title reads «Без назви»', () => {
  assert.equal(workTitle({ title: 'Лісова пісня' }), 'Лісова пісня');
  assert.equal(workTitle({}), 'Без назви');
  assert.equal(workTitle({ title: '  ' }), 'Без назви');
});

test('the credit line names the author and license, then Commons', () => {
  assert.equal(
    workCredit({ imageCredit: { author: 'Архип Куїнджі', license: 'Public domain' } }),
    'Автор: Архип Куїнджі · Public domain · Wikimedia Commons',
  );
  assert.equal(workCredit({ imageCredit: { license: 'CC BY-SA 4.0' } }), 'CC BY-SA 4.0 · Wikimedia Commons');
  assert.equal(workCredit({}), null);
});

test('the gallery is for visual artists and shows works with an image', () => {
  assert.deepEqual(galleryWorks({ categories: ['VISUAL_ARTIST'], works: [painting, book, fullOnly] }), [painting, fullOnly]);
  assert.deepEqual(galleryWorks({ categories: ['WRITER'], works: [painting] }), []);
  assert.deepEqual(galleryWorks({ categories: ['VISUAL_ARTIST'] }), []);
});

test('writings are for writers; a writer who also paints keeps the pictures in the gallery', () => {
  assert.deepEqual(writtenWorks({ categories: ['WRITER'], works: [book, painting] }), [book, painting]);
  assert.deepEqual(writtenWorks({ categories: ['WRITER', 'VISUAL_ARTIST'], works: [book, painting, fullOnly] }), [book]);
  assert.deepEqual(writtenWorks({ categories: ['MUSICIAN'], works: [book] }), []);
});
