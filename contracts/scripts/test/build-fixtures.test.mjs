import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildFixtures } from '../lib/fixtures.mjs';
import { REGIONS, regionOf } from '../lib/regions.mjs';
import { fixtureId, pinFor } from '../lib/pin.mjs';
import { slugify } from '../lib/translit.mjs';
import { cleanBiography, firstSentence, summarize, taglineFrom } from '../lib/text.mjs';
import { mapLinks } from '../lib/links.mjs';
import { synthClip, writeWav } from '../lib/wav.mjs';

test('Ukrainian slugs and collisions are stable', () => {
  const taken = new Set();
  for (const [name, expected] of [
    ['Океан Ельзи','okean-elzy'],['Океан Ельзи','okean-elzy-2'],
    ['Соломія Крушельницька','solomiia-krushelnytska'],['Згорани','zghorany'],
    ['Юрій Іздрик','yurii-izdryk'],['Їжак','yizhak'],['«ДахаБраха»','dakhabrakha'],
    ['The Hardkiss','the-hardkiss']
  ]) assert.equal(slugify(name, taken), expected);
});
test('biographies retain punctuation inside parentheses and summarize on sentence boundaries', () => {
  const text = cleanBiography('Оксана Линів (лат. Oksana Lyniv, нар. 1978(19780106)) — українська диригентка. Перша жінка.');
  assert.equal(firstSentence(text), 'Оксана Линів (лат. Oksana Lyniv, нар. 1978) — українська диригентка.');
  assert.equal(taglineFrom(firstSentence(text)), 'українська диригентка');
  assert.equal(summarize('Перше речення. Друге речення. Третє.', 30), 'Перше речення. Друге речення.');
  const truncated = summarize('Слово '.repeat(80).trim(), 300);
  assert.ok(truncated.length <= 300 && truncated.endsWith('…') && !truncated.endsWith(' …'));
  assert.equal(summarize('',300), '');
});
test('pin formula distributes artists within settlement radius', () => {
  const place = {placeId:'Q1899',lat:50.45,lon:30.523611,type:'CITY'};
  assert.deepEqual(pinFor(fixtureId('Q1'), place, 'EXACT'), {lat:place.lat,lon:place.lon});
  for (const [extras,radius] of [[{},3000],[{areaKm2:839},8000],[{type:'VILLAGE'},400]]) {
    let sum = 0;
    for (let i = 0; i < 2000; i++) {
      const pin = pinFor(fixtureId('Q'+i), {...place,...extras}, 'SETTLEMENT');
      const d = Math.hypot((pin.lat-place.lat)*111320,(pin.lon-place.lon)*111320*Math.cos(place.lat*Math.PI/180));
      assert.ok(d <= radius + 0.1);
      sum += d / radius;
    }
    assert.ok(sum / 2000 > .637 && sum / 2000 < .697);
  }
});
test('only supported public HTTP links survive, with the V2 platform group', () => {
  const items = ['WIKI','MUSICBRAINZ','SPOTIFY','DISCOGS','YOUTUBE','INSTAGRAM'].map(platform => ({platform,url:'https://example.org'}));
  assert.equal(mapLinks([{platform:'LASTFM',url:'https://www.last.fm/music/Абордаж'}])[0].url, new URL('https://www.last.fm/music/Абордаж').href);
  items.push({platform:'WEBSITE',url:'javascript:alert(1)'});
  assert.deepEqual(mapLinks(items).map(x=>[x.platform,x.group]),[['WIKIPEDIA','REFERENCE'],['SPOTIFY','MUSIC'],['OTHER','SOCIAL'],['YOUTUBE','VIDEO'],['INSTAGRAM','SOCIAL']]);
});
test('six distinct generated files are 15-second PCM 22050 Hz mono WAVs', () => {
  const heads = new Set();
  for(let n=1; n<=6; n++) {
    const wav = writeWav(synthClip(n), 22050);
    assert.equal(wav.length, 661544);
    assert.equal(wav.toString('ascii',0,4),'RIFF');
    assert.equal(wav.toString('ascii',8,12),'WAVE');
    assert.equal(wav.readUInt16LE(20),1);
    assert.equal(wav.readUInt16LE(22),1);
    assert.equal(wav.readUInt32LE(24),22050);
    assert.equal(wav.readUInt16LE(34),16);
    heads.add(wav.subarray(44,244).toString('hex'));
  }
  assert.equal(heads.size,6);
});
const json = path => JSON.parse(readFileSync(new URL(path,import.meta.url),'utf8'));
const r = json('./sample-dry-run.json');
const extras = json('../fixture-extras.json');
const mockMeta = REGIONS.map(({code,name})=>({code,name,center:[30,49],bbox:[22,44,40,53]}));
test('only supported origin evidence contributes to pins, places, regions and filters', () => {
  const invalid = ['RESIDENCE','WORK',null].map((locationBasis,i)=>({...r[0],qid:'missing-origin-'+i,locationBasis}));
  invalid.push({...r[0],qid:'unknown-region',place:{...r[0].place,region:null}});
  const set = buildFixtures([...r,...invalid],mockMeta,extras);
  assert.equal(set.pins.pins.length, 9);
  assert.equal(set.regions.length,27);
  const counts = Object.fromEntries(set.regions.map(row=>[row.code,row.count]));
  assert.equal(counts.KYIV_CITY,1);
  assert.equal(counts.KYIV,0);
  assert.equal(counts.SEVASTOPOL_CITY,1);
  assert.equal(counts.CRIMEA,0);
  assert.equal(regionOf({qid:'Q1899',region:null}), 'KYIV_CITY');
  assert.deepEqual(set.filters[0].options.map(o=>[o.value,o.count]),set.regions.map(o=>[o.code,o.count]));
  assert.equal(Object.values(counts).reduce((n,v)=>n+v,0),9);
  assert.match(set.pins.version,/^[a-f\d]{8}$/);
  const artist = set.details['oksana-lyniv'];
  assert.equal(artist.origin.precision,'SETTLEMENT');
  assert.equal(artist.images[0].credit.author,'Mykola Swarnyk');
  assert.equal(artist.textSource.license,'CC BY-SA 4.0');
  assert.equal(set.details['okean-elzy'].dates.activeFrom,'1994');
  assert.equal(set.details['pustyi-hurt'].summary,'');
  const fragment = set.fragments[set.details['okean-elzy'].id];
  assert.equal(fragment.source,'UPLOAD');
  assert.match(fragment.attribution,/синтетичн/i);
  assert.equal(fragment.startSec + fragment.durationSec,15);
  assert.equal(Object.values(set.details).filter(x=>x.textSource===null).length,3);
  assert.deepEqual(set.pins,buildFixtures([...r].reverse(),mockMeta,extras).pins);
});

test('birth and formation evidence must match the artist kind', () => {
  const person = json('./sample-dry-run.json')[0];
  const band = {...person, categories:['BAND'], qid:'Q123'};
  const wrongPerson = {...person, locationBasis:'FORMATION'};
  const set = buildFixtures([wrongPerson, band], mockMeta, []);
  assert.equal(set.pins.pins.length,0);
  assert.equal(set.regions.reduce((total, region)=>total+region.count,0),0);
});
