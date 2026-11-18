import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const contracts = new URL('../../',import.meta.url);
const spec = fileURLToPath(new URL('openapi.yaml', contracts));
const fixtures = new URL('fixtures/',contracts);
const referenceBase = 'urn:nashi:v2:contract';

function validatorFor(document) {
  const ajv = new Ajv2020({strict:false,allErrors:true});
  addFormats(ajv);
  ajv.addSchema({$id:referenceBase,components:document.components});
  return (name,array=false) => {
    const ref = {$ref:referenceBase+'#/components/schemas/'+name};
    const validate = ajv.compile(array ? {type:'array',items:ref} : ref);
    return (data,file) => assert.ok(validate(data),file + ' ' + ajv.errorsText(validate.errors));
  };
}
test('schema validator follows nested OpenAPI references and enforces array item types', () => {
  const schema = {components:{schemas:{
    Point:{type:'object',required:['lat'],properties:{lat:{type:'number'}}},
    Place:{type:'object',required:['point'],properties:{point:{$ref:'#/components/schemas/Point'}}}
  }}};
  const validate = validatorFor(schema)('Place',true);
  validate([{point:{lat:50}}],'valid sample');
  assert.throws(()=>validate([{point:{lat:'50'}}],'invalid sample'),/invalid sample.*number/u);
});
test('committed fixtures conform to the published V2 OpenAPI schemas', {skip:existsSync(spec) ? false : 'Технічна команда ще не створила contracts/openapi.yaml (T1).'}, async () => {
  const schema = await SwaggerParser.bundle(spec,{resolve:{http:false}});
  const getValidator = validatorFor(schema);
  const groups = [
    ['MapPinsResponse',false,['map-pins.json']],['RegionStat',true,['map-regions.json']],
    ['PlaceStat',true,['map-places.json']],['FilterDefinition',true,['filters.json']],
    ['MusicianPage',false,['musicians/index.json']],['PlaceSuggestion',true,['places-search.json']],
    ['MusicianDetails',false,readdirSync(new URL('musicians/',fixtures)).filter(f=>f!=='index.json').map(f=>'musicians/'+f)],
    ['Fragment',false,readdirSync(new URL('fragments/',fixtures)).map(f=>'fragments/'+f)]
  ];
  for (const [name,array,files] of groups) {
    const validate = getValidator(name,array);
    for (const file of files) validate(JSON.parse(readFileSync(new URL(file,fixtures),'utf8')),file);
  }
});
