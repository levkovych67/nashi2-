import { readFileSync, writeFileSync, mkdirSync, mkdtempSync, readdirSync, existsSync, renameSync, rmSync, openSync, closeSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { buildFixtures } from './lib/fixtures.mjs';
import { synthClip, writeWav } from './lib/wav.mjs';

const scripts = dirname(fileURLToPath(import.meta.url));
const {values} = parseArgs({
  options:{'dry-run':{type:'string'},'regions-meta':{type:'string'},out:{type:'string'}}
});
if (!values['dry-run']) throw new Error('Usage: node scripts/build-fixtures.mjs --dry-run <V1.json> [--regions-meta <regions-meta.json>] [--out <fixtures>]');
const readJson = path => JSON.parse(readFileSync(path,'utf8'));
const records = readJson(values['dry-run']);
const meta = readJson(values['regions-meta'] ?? join(scripts,'../geo/regions-meta.json'));
const extras = readJson(join(scripts,'fixture-extras.json'));
const set = buildFixtures(records,meta,extras);
const out = resolve(values.out ?? join(scripts,'../fixtures'));
const marker = '.nashi-fixtures';
const names = ['map-pins.json','map-regions.json','map-places.json','filters.json','places-search.json','profile-slugs.json'];
const contents = [set.pins,set.regions,set.places,set.filters,set.placeSearch,Object.keys(set.details)];
const palettes = [['#412343','#fec198'],['#233c5c','#d5edb2'],['#194a43','#fadcbc']];
function cover(index) {
  const [bg,fg] = palettes[index];
  const ornaments = [
    '<path d="M60 262L186 60l87 183 58-103 89 172H60Z"/>',
    '<g fill="none" stroke="currentColor" stroke-width="18"><circle cx="139" cy="200" r="66"/><circle cx="240" cy="200" r="66"/><circle cx="341" cy="200" r="66"/></g>',
    '<path d="M40 150c70-85 150 85 220 0s110-18 180 0v33c-70-18-110-85-180 0S110 98 40 183Zm0 70c70-85 150 85 220 0s110-18 180 0v33c-70-18-110-85-180 0S110 168 40 253Z"/>'
  ];
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 400"><rect width="480" height="400" fill="' + bg + '"/><g fill="' + fg + '" color="' + fg + '">' + ornaments[index] + '</g></svg>\n';
}
function writeSet(stage) {
  for (const dir of ['musicians','fragments','audio','img']) mkdirSync(join(stage,dir));
  const writeJson = (path,value) => writeFileSync(join(stage,path),JSON.stringify(value,null,2)+'\n');
  names.forEach((name,i) => writeJson(name,contents[i]));
  writeJson('musicians/index.json',set.index);
  for (const [slug,profile] of Object.entries(set.details)) writeJson('musicians/'+slug+'.json',profile);
  for (const [id,fragment] of Object.entries(set.fragments)) writeJson('fragments/'+id+'.json',fragment);
  for (let i=1; i<=6; i++) writeFileSync(join(stage,'audio','clip-0'+i+'.wav'),writeWav(synthClip(i),22050));
  for (let i=1; i<=extras.length; i++) writeFileSync(join(stage,'img','extra-'+i+'.svg'),cover(i-1));
  writeFileSync(join(stage,marker),'Built by contracts/scripts/build-fixtures.mjs\n');
}
function generate() {
  mkdirSync(dirname(out),{recursive:true});
  const lock = out + '.lock';
  const fd = openSync(lock,'wx');
  let stage;
  let previous;
  try {
    if (existsSync(out) && !existsSync(join(out,marker)) && readdirSync(out).some(name=>name!=='.gitkeep')) {
      throw new Error('Output directory contains existing files outside this generator: ' + out);
    }
    stage = mkdtempSync(out + '.stage-');
    writeSet(stage);
    if (existsSync(out)) {
      previous = stage + '-previous';
      renameSync(out,previous);
    }
    try {
      renameSync(stage,out);
    } catch (error) {
      if (previous) renameSync(previous,out);
      throw error;
    }
    if (previous) rmSync(previous,{recursive:true,force:true});
  } finally {
    if (stage) rmSync(stage,{recursive:true,force:true});
    closeSync(fd);
    rmSync(lock);
  }
}
generate();
console.log(JSON.stringify({snapshotRecords:records.length,originPins:set.pins.pins.length-extras.length,demoSubmissions:extras.length,pins:set.pins.pins.length,places:set.places.length,regions:set.regions.length,detailedProfiles:Object.keys(set.details).length,fragments:Object.keys(set.fragments).length,version:set.pins.version}));
