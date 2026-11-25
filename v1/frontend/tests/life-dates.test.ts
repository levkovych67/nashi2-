import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatPartialDate, lifeSpan, CATEGORY_LABEL, locationBasisNote } from '../src/features/artists/utils/lifeDates.ts';

test('formatPartialDate', () => {
  assert.equal(formatPartialDate('1814-03-09'), '9 березня 1814');
  assert.equal(formatPartialDate('1814-03'), 'березень 1814');
  assert.equal(formatPartialDate('1814'), '1814');
  assert.equal(formatPartialDate(null), null);
  assert.equal(formatPartialDate(undefined), null);
  assert.equal(formatPartialDate(''), null);
  assert.equal(formatPartialDate('1900-01-02'), '2 січня 1900');
  assert.equal(formatPartialDate('1900-02-15'), '15 лютого 1900');
  assert.equal(formatPartialDate('1900-05-01'), '1 травня 1900');
  assert.equal(formatPartialDate('1900-12-31'), '31 грудня 1900');
  const gen = ['січня','лютого','березня','квітня','травня','червня','липня','серпня','вересня','жовтня','листопада','грудня'];
  gen.forEach((m, i) => assert.equal(formatPartialDate(`2000-${String(i + 1).padStart(2, '0')}-10`), `10 ${m} 2000`));
});

test('lifeSpan', () => {
  assert.equal(lifeSpan({ categories: ['WRITER'], birthDate: '1814-03-09', deathDate: '1861-03-10' }), '1814–1861');
  assert.equal(lifeSpan({ birthDate: '1975-05' }), 'нар. 1975');
  assert.equal(lifeSpan({ deathDate: '1861' }), 'пом. 1861');
  assert.equal(lifeSpan({ categories: ['MUSICIAN'] }), null);
  assert.equal(lifeSpan({ categories: ['BAND'], foundationYear: 1994, disbandYear: 2010 }), '1994–2010');
  assert.equal(lifeSpan({ categories: ['BAND'], foundationYear: 1994 }), 'з 1994');
  assert.equal(lifeSpan({ categories: ['BAND'] }), null);
});

test('labels and location notes', () => {
  assert.equal(CATEGORY_LABEL.BAND, 'Гурт');
  assert.equal(CATEGORY_LABEL.VISUAL_ARTIST, 'Художник');
  assert.equal(locationBasisNote('BIRTH', 'Київ'), null);
  assert.equal(locationBasisNote(undefined, 'Київ'), null);
  assert.equal(locationBasisNote('RESIDENCE', 'Київ'), 'На карті: Київ — місце життя');
  assert.equal(locationBasisNote('WORK', 'Львів'), 'На карті: Львів — місце роботи');
  assert.equal(locationBasisNote('FORMATION', 'Харків'), 'На карті: Харків — місце заснування');
});
