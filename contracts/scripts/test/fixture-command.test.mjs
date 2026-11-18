import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, readFile, readdir, writeFile, rm } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { REGIONS } from '../lib/regions.mjs';
const sample = fileURLToPath(new URL('./sample-dry-run.json', import.meta.url));
const script = fileURLToPath(new URL('../build-fixtures.mjs', import.meta.url));

test('CLI emits complete linked fixtures and reproducible regenerated data', async t => {
  const root = await mkdtemp(join(tmpdir(),'nashi-v2-test-fixtures-'));
  t.after(()=>rm(root,{recursive:true,force:true}));
  const meta = join(root,'regions.json');
  const out = join(root,'generated');
  await writeFile(meta, JSON.stringify(REGIONS.map(({code,name})=>({code,name,center:[30,49],bbox:[22,44,40,53]}))));
  const build = () => execFileSync(process.execPath,[script,'--dry-run',sample,'--regions-meta',meta,'--out',out],{stdio:'pipe'});
  const first = build();
  assert.deepEqual(build(),first);
  const read = async path => JSON.parse(await readFile(join(out,path),'utf8'));
  const pins = await read('map-pins.json');
  const slugs = await read('profile-slugs.json');
  for (const slug of slugs) assert.equal((await read('musicians/' + slug + '.json')).slug,slug);
  for (const pin of pins.pins.filter(p=>p.hasAudio)) {
    const {url} = await read('fragments/' + pin.id + '.json');
    assert.equal((await readFile(join(out,url.slice('/fixtures/'.length)))).length,661544);
  }
  assert.equal((await readdir(join(out,'img'))).length,3);
  await writeFile(meta,'[]');
  assert.throws(build);
  assert.deepEqual(await read('map-pins.json'),pins);
});
