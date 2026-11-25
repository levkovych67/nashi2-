import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { REGIONS } from '../lib/regions.mjs';
const base = new URL('../../fixtures/', import.meta.url);
const read = path => JSON.parse(readFileSync(new URL(path,base),'utf8'));
const digest = value => createHash('sha256').update(JSON.stringify(value)).digest('hex').slice(0,8);
const {version,pins} = read('map-pins.json');
const regions = read('map-regions.json');
const places = read('map-places.json');
const filters = read('filters.json');
const index = read('musicians/index.json');
const slugs = read('profile-slugs.json');
const details = slugs.map(slug=>read('musicians/'+slug+'.json'));
const pinBySlug = new Map(pins.map(pin=>[pin.slug,pin]));
const stats = (rows,key) => {
  const map = new Map();
  for (const row of rows) map.set(row[key],(map.get(row[key]) ?? 0) + 1);
  return map;
};
test('published map aggregates, region filters, and places-search agree with every pin', () => {
  assert.equal(version,digest(pins));
  assert.ok(pins.length >= 3500);
  assert.equal(new Set(pins.map(p=>p.id)).size,pins.length);
  assert.equal(pinBySlug.size,pins.length);
  assert.deepEqual(regions.map(r=>r.code),REGIONS.map(r=>r.code));
  const regionCounts = stats(pins,'region');
  const placeCounts = stats(pins,'placeId');
  for (const r of regions) {
    assert.equal(r.count,regionCounts.get(r.code) ?? 0);
    assert.ok(r.center.length===2 && r.bbox.length===4 && [...r.center,...r.bbox].every(Number.isFinite));
    assert.ok(r.center[0]>=r.bbox[0] && r.center[0]<=r.bbox[2] && r.center[1]>=r.bbox[1] && r.center[1]<=r.bbox[3]);
  }
  assert.equal(places.length,placeCounts.size);
  for (const p of places) assert.equal(p.count,placeCounts.get(p.placeId));
  const search = read('places-search.json');
  assert.deepEqual(search.map(p=>p.placeId),places.map(p=>p.placeId));
  for (const p of search) assert.equal(p.regionName,REGIONS.find(r=>r.code===p.region)?.name);
  assert.deepEqual(filters,[{key:'region',label:'Область',type:'MULTI_SELECT',options:regions.map(r=>({value:r.code,label:r.name,count:r.count}))}]);
  assert.equal(index.totalItems,pins.length);
  assert.ok(index.page===0 && index.size===20 && index.items.length===20 && index.totalPages===Math.ceil(pins.length/20));
  for (const item of index.items) assert.ok(slugs.includes(item.slug));
});
test('committed selection covers agreed sample quotas and links attribution to each original', () => {
  assert.deepEqual(readdirSync(new URL('musicians/',base)).sort(),['index.json',...slugs.map(s=>s+'.json')].sort());
  const wiki = details.filter(d=>d.textSource);
  for (const [minimum,count] of [
    [40,wiki.length],[10,wiki.filter(d=>d.kind==='BAND').length],[25,wiki.filter(d=>d.kind==='PERSON').length],
    [15,wiki.filter(d=>d.images.length).length],[8,wiki.filter(d=>!d.images.length).length],
    [12,wiki.filter(d=>d.hasAudio).length],[12,wiki.filter(d=>!d.hasAudio).length],[5,wiki.filter(d=>d.youtubeChannelUrl).length]
  ]) assert.ok(count>=minimum, 'sample count '+ count +' < '+ minimum);
  assert.equal(details.length-wiki.length,3);
  for (const d of details) {
    const pin = pinBySlug.get(d.slug);
    assert.equal(pin.id,d.id);
    assert.equal(d.hasAudio,pin.hasAudio);
    assert.equal(d.origin.lat,pin.lat);
    assert.equal(d.origin.lon,pin.lon);
    assert.ok(d.summary.length <= 300 && d.description.length <= 2000);
    assert.ok(d.genres.length <= 5);
    for (const date of Object.values(d.dates)) assert.ok(date===null || /^\d{4}(-\d{2}){0,2}$/.test(date));
    if (d.textSource) {
      assert.equal(d.textSource.type,'WIKIPEDIA');
      assert.equal(d.textSource.license,'CC BY-SA 4.0');
      assert.ok(URL.canParse(d.textSource.url));
      assert.ok(['FORMATION','BIRTH'].includes(d.origin.basis));
      for (const img of d.images) assert.ok(img.credit.author && img.credit.license && URL.canParse(img.credit.sourceUrl));
    } else {
      assert.equal(d.images[0].credit,null);
      assert.equal(d.origin.basis,'ORIGIN');
    }
  }
});
test('all advertised fragments resolve to local WAV files with truthful source and duration', () => {
  const playable = pins.filter(p=>p.hasAudio);
  assert.deepEqual(readdirSync(new URL('fragments/',base)).sort(),playable.map(p=>p.id+'.json').sort());
  for (const pin of playable) {
    const f = read('fragments/'+pin.id+'.json');
    assert.match(f.url,/^\/fixtures\/audio\/clip-0[1-6]\.wav$/);
    assert.equal(f.source,'UPLOAD');
    assert.match(f.attribution,/Синтетичний/);
    assert.ok(f.startSec >= 0 && f.durationSec > 0 && f.startSec + f.durationSec <= 15);
    assert.ok(f.fadeInMs===300 && f.fadeOutMs===600);
  }
});
test('regeneration from an available V1 snapshot reproduces the published pins and profiles', async t => {
  const source = process.env.NASHI_DRY_RUN;
  if (!source) return t.skip('Задайте NASHI_DRY_RUN для звірки з локальним знімком V1.');
  const {buildFixtures} = await import('../lib/fixtures.mjs');
  const jsonFile = path => JSON.parse(readFileSync(path,'utf8'));
  const extras = jsonFile(new URL('../fixture-extras.json',import.meta.url));
  const meta = jsonFile(new URL('../../geo/regions-meta.json',import.meta.url));
  const generated = buildFixtures(jsonFile(source),meta,extras);
  assert.deepEqual(generated.pins,{version,pins});
  assert.deepEqual(generated.regions,regions);
  assert.deepEqual(generated.places,places);
  assert.deepEqual(generated.index,index);
  for (const detail of details) assert.deepEqual(generated.details[detail.slug],detail);
});
