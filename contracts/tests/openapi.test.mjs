import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const spec = parse(readFileSync(process.env.OPENAPI_PATH ?? here('../openapi.yaml'), 'utf8'));
const S = spec.components.schemas;
const METHODS = ['get', 'post', 'put', 'patch', 'delete'];

function operations() {
  const out = [];
  for (const [path, item] of Object.entries(spec.paths)) {
    for (const m of METHODS) if (item[m]) out.push({ path, method: m, op: item[m] });
  }
  return out;
}
const deref = (o) => (o?.$ref ? deref(o.$ref.slice(2).split('/').reduce((a, k) => a[k], spec)) : o);
const types = (s) => (Array.isArray(s.type) ? s.type : [s.type]);
const nullable = (s) => types(s).includes('null');

test('document is OpenAPI 3.1 with /api/v2 server', () => {
  assert.equal(spec.openapi, '3.1.0');
  assert.equal(spec.servers[0].url, '/api/v2');
});

test('Region enum is the 27 codes of 02-contracts section 2 in contract order', () => {
  assert.deepEqual(S.Region.enum, ['CRIMEA', 'VINNYTSIA', 'VOLYN', 'DNIPROPETROVSK', 'DONETSK', 'ZHYTOMYR',
    'ZAKARPATTIA', 'ZAPORIZHZHIA', 'IVANO_FRANKIVSK', 'KYIV', 'KIROVOHRAD', 'LUHANSK', 'LVIV', 'MYKOLAIV', 'ODESA',
    'POLTAVA', 'RIVNE', 'SUMY', 'TERNOPIL', 'KHARKIV', 'KHERSON', 'KHMELNYTSKYI', 'CHERKASY', 'CHERNIVTSI',
    'CHERNIHIV', 'KYIV_CITY', 'SEVASTOPOL_CITY']);
});

test('all agreed schemas and enums exist', () => {
  const names = ['MapPin', 'MapPinsResponse', 'RegionStat', 'PlaceStat', 'FilterDefinition', 'FilterOption',
    'FilterType', 'MusicianListItem', 'MusicianPage', 'MusicianDetails', 'TextSource', 'Image', 'ImageCredit',
    'Origin', 'LifeDates', 'Materials', 'ClipSummary', 'Work', 'Video', 'Link', 'Fragment', 'LikeRequest',
    'LikeResponse', 'PlaceSuggestion', 'SubmissionRequest', 'SubmissionAccepted', 'PublicConfig', 'Health',
    'Problem', 'FieldError', 'Region', 'MusicianKind', 'OriginBasis', 'PinPrecision', 'AudioSource',
    'LinkPlatform', 'LinkGroup', 'PlaceType', 'WorkType'];
  for (const n of names) assert.ok(S[n], `missing schema ${n}`);
});

test('enums match 02-contracts section 2', () => {
  assert.deepEqual(S.MusicianKind.enum, ['PERSON', 'BAND']);
  assert.deepEqual(S.OriginBasis.enum, ['BIRTH', 'FORMATION', 'ORIGIN']);
  assert.deepEqual(S.PinPrecision.enum, ['EXACT', 'SETTLEMENT', 'CITY']);
  assert.deepEqual(S.AudioSource.enum, ['UPLOAD', 'DEEZER', 'ITUNES', 'COMMONS']);
  assert.deepEqual(S.LinkGroup.enum, ['MUSIC', 'VIDEO', 'REFERENCE', 'SOCIAL']);
  assert.deepEqual(S.PlaceType.enum, ['CITY', 'TOWN', 'VILLAGE', 'OTHER']);
  assert.deepEqual(S.WorkType.enum, ['ALBUM', 'SINGLE', 'SONG', 'COMPOSITION', 'OTHER']);
  assert.deepEqual(S.FilterType.enum, ['MULTI_SELECT', 'SINGLE_SELECT', 'RANGE', 'BOOLEAN']);
  assert.deepEqual(S.LinkPlatform.enum, ['SPOTIFY', 'APPLE_MUSIC', 'DEEZER', 'YOUTUBE_MUSIC', 'SOUNDCLOUD',
    'BANDCAMP', 'YOUTUBE', 'WIKIPEDIA', 'WEBSITE', 'INSTAGRAM', 'FACEBOOK', 'TIKTOK', 'TELEGRAM', 'X', 'OTHER']);
});

test('every operation has an operationId from the agreed list (12, no more, no less)', () => {
  const expected = ['getMapPins', 'getMapRegions', 'getMapPlaces', 'getFilters', 'listMusicians', 'getMusician',
    'getFragment', 'addLikes', 'searchPlaces', 'createSubmission', 'getPublicConfig', 'getHealth'];
  const ids = operations().map((o) => o.op.operationId);
  assert.deepEqual([...ids].sort(), [...expected].sort());
  assert.equal(new Set(ids).size, ids.length);
});

test('paths and methods match section 2', () => {
  const got = operations().map((o) => `${o.method.toUpperCase()} ${o.path}`).sort();
  assert.deepEqual(got, [
    'GET /config/public', 'GET /filters', 'GET /health', 'GET /map/pins', 'GET /map/places', 'GET /map/regions',
    'GET /musicians', 'GET /musicians/{id}/fragment', 'GET /musicians/{slug}', 'GET /places/search',
    'POST /musicians/{id}/likes', 'POST /submissions',
  ].sort());
});

test('every 4xx/5xx response uses application/problem+json with Problem schema (except health 503)', () => {
  let checked = 0;
  for (const { path, method, op } of operations()) {
    for (const [code, resp] of Object.entries(op.responses)) {
      if (!/^[45]/.test(code)) continue;
      if (path === '/health' && code === '503') continue;
      const r = resp.$ref ? spec.components.responses[resp.$ref.split('/').pop()] : resp;
      assert.ok(r, `${method} ${path} ${code} unresolved`);
      const content = r.content;
      assert.deepEqual(Object.keys(content), ['application/problem+json'], `${method} ${path} ${code}`);
      assert.equal(content['application/problem+json'].schema.$ref, '#/components/schemas/Problem');
      checked++;
    }
  }
  assert.ok(checked >= 15, `only ${checked} error responses checked`);
});

test('429 responses document Retry-After header', () => {
  let n = 0;
  for (const { path, method, op } of operations()) {
    const resp = op.responses['429'];
    if (!resp) continue;
    const r = resp.$ref ? spec.components.responses[resp.$ref.split('/').pop()] : resp;
    assert.ok(r.headers?.['Retry-After'], `${method} ${path} 429 lacks Retry-After`);
    n++;
  }
  assert.ok(n >= 2);
});

test('/health 503 returns Health, not Problem', () => {
  const r = spec.paths['/health'].get.responses['503'];
  assert.equal(r.content['application/json'].schema.$ref, '#/components/schemas/Health');
});

test('map/filters endpoints support ETag, Cache-Control and 304', () => {
  for (const p of ['/map/pins', '/map/regions', '/map/places', '/filters']) {
    const op = spec.paths[p].get;
    assert.ok(op.parameters.map(deref).some((x) => x.name === 'If-None-Match' && x.in === 'header'), `${p} If-None-Match`);
    assert.ok(op.responses['200'].headers.ETag, `${p} ETag`);
    assert.ok(op.responses['200'].headers['Cache-Control'], `${p} Cache-Control`);
    assert.ok(op.responses['304'], `${p} 304`);
  }
});

test('getFragment returns 200 Fragment or 204 and accepts exclude', () => {
  const op = spec.paths['/musicians/{id}/fragment'].get;
  assert.equal(op.responses['200'].content['application/json'].schema.$ref, '#/components/schemas/Fragment');
  assert.ok(op.responses['204']);
  assert.ok(op.parameters.some((p) => p.name === 'exclude' && p.in === 'query' && !p.required));
});

test('listMusicians parameters follow 2.5', () => {
  const ps = Object.fromEntries(spec.paths['/musicians'].get.parameters.map((p) => [p.name, p]));
  assert.deepEqual(Object.keys(ps).sort(), ['page', 'q', 'region', 'size', 'sort']);
  assert.equal(ps.region.schema.type, 'array');
  assert.equal(ps.region.explode, true);
  assert.equal(ps.q.schema.minLength, 2);
  assert.deepEqual(ps.sort.schema.enum, ['NAME', 'LIKES', 'RECENT']);
  assert.equal(ps.sort.schema.default, 'NAME');
  assert.equal(ps.page.schema.minimum, 0);
  assert.equal(ps.page.schema.default, 0);
  assert.equal(ps.size.schema.minimum, 1);
  assert.equal(ps.size.schema.maximum, 100);
  assert.equal(ps.size.schema.default, 20);
});

test('searchPlaces parameters follow 2.9', () => {
  const ps = Object.fromEntries(spec.paths['/places/search'].get.parameters.map((p) => [p.name, p]));
  assert.equal(ps.q.required, true);
  assert.equal(ps.q.schema.minLength, 2);
  assert.ok(!ps.region.required);
  assert.equal(ps.limit.schema.minimum, 1);
  assert.equal(ps.limit.schema.maximum, 20);
  assert.equal(ps.limit.schema.default, 10);
});

test('MapPin has exactly the fields of 02-contracts 2.1', () => {
  assert.deepEqual(Object.keys(S.MapPin.properties).sort(),
    ['hasAudio', 'id', 'kind', 'lat', 'lon', 'name', 'placeId', 'region', 'slug', 'thumbUrl'].sort());
  assert.ok(!S.MapPin.required.includes('thumbUrl'));
  assert.ok(S.MapPin.required.includes('hasAudio'));
});

test('MapPinsResponse, RegionStat, PlaceStat shapes', () => {
  assert.deepEqual(S.MapPinsResponse.required.sort(), ['pins', 'version']);
  assert.equal(S.RegionStat.properties.center.minItems, 2);
  assert.equal(S.RegionStat.properties.center.maxItems, 2);
  assert.equal(S.RegionStat.properties.bbox.minItems, 4);
  assert.equal(S.RegionStat.properties.bbox.maxItems, 4);
  assert.deepEqual(Object.keys(S.RegionStat.properties).sort(), ['bbox', 'center', 'code', 'count', 'name']);
  assert.deepEqual(Object.keys(S.PlaceStat.properties).sort(),
    ['count', 'lat', 'lon', 'name', 'placeId', 'region', 'type']);
  const regions = spec.paths['/map/regions'].get.responses['200'].content['application/json'].schema;
  assert.equal(regions.minItems, 27);
  assert.equal(regions.maxItems, 27);
});

test('MusicianPage and MusicianListItem fields', () => {
  assert.deepEqual(Object.keys(S.MusicianPage.properties).sort(),
    ['items', 'page', 'size', 'totalItems', 'totalPages']);
  assert.equal(S.MusicianPage.properties.items.items.$ref, '#/components/schemas/MusicianListItem');
  assert.deepEqual(Object.keys(S.MusicianListItem.properties).sort(),
    ['hasAudio', 'id', 'kind', 'likesCount', 'name', 'placeName', 'region', 'slug', 'thumbUrl']);
});

test('MusicianDetails has all fields of 2.6 and getMusician is by slug', () => {
  assert.deepEqual(Object.keys(S.MusicianDetails.properties).sort(), [
    'dates', 'description', 'genres', 'hasAudio', 'id', 'images', 'kind', 'likesCount', 'links', 'materials',
    'name', 'origin', 'slug', 'summary', 'tagline', 'textSource', 'updatedAt', 'youtubeChannelUrl'].sort());
  const p = spec.paths['/musicians/{slug}'].get.parameters[0];
  assert.equal(p.name, 'slug');
  assert.equal(p.in, 'path');
  assert.deepEqual(Object.keys(S.Materials.properties).sort(), ['clips', 'videos', 'works']);
  assert.deepEqual(Object.keys(S.ClipSummary.properties).sort(),
    ['clipId', 'durationSec', 'externalUrl', 'source', 'title']);
  assert.deepEqual(Object.keys(S.Origin.properties).sort(),
    ['basis', 'lat', 'lon', 'placeId', 'placeName', 'precision', 'region', 'regionName']);
});

test('Fragment fields', () => {
  assert.deepEqual(Object.keys(S.Fragment.properties).sort(), ['attribution', 'clipId', 'durationSec',
    'expiresAt', 'externalUrl', 'fadeInMs', 'fadeOutMs', 'source', 'startSec', 'title', 'url'].sort());
  assert.equal(S.Fragment.properties.startSec.minimum, 0);
});

test('nullable and optional markers follow the contract notes', () => {
  assert.ok(nullable(S.MusicianDetails.properties.textSource) || S.MusicianDetails.properties.textSource.oneOf?.some((x) => x.type === 'null'),
    'textSource nullable');
  const credit = S.Image.properties.credit;
  assert.ok(credit.oneOf?.some((x) => x.type === 'null') || nullable(credit), 'Image.credit nullable');
  assert.ok(nullable(S.ImageCredit.properties.licenseUrl), 'ImageCredit.licenseUrl nullable');
  for (const f of ['author', 'license']) {
    assert.ok(nullable(S.ImageCredit.properties[f]), `ImageCredit.${f} nullable`);
    assert.ok(S.ImageCredit.required.includes(f), `ImageCredit.${f} required`);
  }
  assert.ok(nullable(S.MusicianDetails.properties.summary), 'MusicianDetails.summary nullable');
  assert.ok(S.MusicianDetails.required.includes('summary'), 'MusicianDetails.summary required');
  assert.ok(types(S.MusicianDetails.properties.summary).includes('string'), 'MusicianDetails.summary string');
  assert.ok(nullable(S.Fragment.properties.expiresAt), 'Fragment.expiresAt nullable');
  for (const f of ['birth', 'death', 'activeFrom', 'activeTo']) {
    assert.ok(nullable(deref(S.LifeDates.properties[f])), `LifeDates.${f} nullable`);
  }
  assert.ok(nullable(S.SubmissionRequest.properties.birthPlaceId), 'birthPlaceId nullable');
  assert.ok(nullable(S.SubmissionRequest.properties.wikipediaUrl), 'wikipediaUrl nullable');
  assert.ok(nullable(S.SubmissionRequest.properties.youtubeChannelUrl), 'youtubeChannelUrl nullable');
  assert.ok(nullable(S.PublicConfig.properties.turnstileSiteKey), 'turnstileSiteKey nullable');
  assert.ok(!S.MapPin.required.includes('thumbUrl'));
  assert.ok(!nullable(S.MapPin.properties.hasAudio));
});

test('LikeRequest.count is integer 1..50; LikeResponse has likesCount', () => {
  const c = S.LikeRequest.properties.count;
  assert.equal(c.type, 'integer');
  assert.equal(c.minimum, 1);
  assert.equal(c.maximum, 50);
  assert.deepEqual(S.LikeRequest.required, ['count']);
  assert.deepEqual(S.LikeResponse.required, ['likesCount']);
  const op = spec.paths['/musicians/{id}/likes'].post;
  assert.deepEqual(Object.keys(op.responses).filter((c) => /^[45]/.test(c)).sort(), ['400', '404', '429']);
});

test('SubmissionRequest required = name, kind, originPlaceId, summary, links, consent', () => {
  assert.deepEqual([...S.SubmissionRequest.required].sort(),
    ['consent', 'kind', 'links', 'name', 'originPlaceId', 'summary']);
});

test('SubmissionRequest field constraints follow 2.10', () => {
  const p = S.SubmissionRequest.properties;
  assert.equal(p.name.minLength, 2);
  assert.equal(p.name.maxLength, 100);
  assert.equal(p.summary.minLength, 30);
  assert.equal(p.summary.maxLength, 1000);
  assert.equal(p.genres.maxItems, 5);
  assert.equal(p.genres.items.minLength, 2);
  assert.equal(p.genres.items.maxLength, 40);
  assert.equal(p.links.minItems, 1);
  assert.equal(p.links.maxItems, 10);
  assert.equal(p.contact.maxLength, 200);
  assert.equal(p.consent.const ?? p.consent.enum?.[0], true);
  assert.equal(p.faxNumber.maxLength, 0);
});

test('createSubmission is multipart with data (json) and images (1..5), responds 202', () => {
  const op = spec.paths['/submissions'].post;
  const mp = op.requestBody.content['multipart/form-data'];
  assert.ok(mp);
  const data = mp.schema.properties.data;
  assert.equal(data.contentMediaType, 'application/json');
  assert.equal(data.contentSchema.$ref, '#/components/schemas/SubmissionRequest');
  const images = mp.schema.properties.images;
  assert.equal(images.type, 'array');
  assert.equal(images.minItems, 1);
  assert.equal(images.maxItems, 5);
  assert.equal(images.items.type, 'string');
  assert.equal(images.items.contentMediaType, 'image/*');
  assert.deepEqual(mp.schema.required.sort(), ['data', 'images']);
  assert.ok(op.responses['202']);
  for (const c of ['400', '413', '415', '429', '503']) assert.ok(op.responses[c], `missing ${c}`);
  assert.equal(S.SubmissionAccepted.properties.status.const ?? S.SubmissionAccepted.properties.status.enum[0], 'PENDING');
});

test('Problem schema has RFC 9457 fields, code enum and errors[]', () => {
  const p = S.Problem.properties;
  for (const f of ['type', 'title', 'status', 'detail', 'instance', 'code', 'errors']) assert.ok(p[f], f);
  assert.deepEqual([...p.code.enum].sort(), ['INTERNAL', 'NOT_FOUND', 'PAYLOAD_TOO_LARGE', 'RATE_LIMITED',
    'UNAVAILABLE', 'UNSUPPORTED_MEDIA_TYPE', 'VALIDATION_FAILED']);
  assert.equal(p.errors.items.$ref, '#/components/schemas/FieldError');
  assert.deepEqual(Object.keys(S.FieldError.properties).sort(), ['field', 'message']);
});

test('PublicConfig and Health shapes', () => {
  assert.deepEqual(Object.keys(S.PublicConfig.properties).sort(),
    ['fragmentSeconds', 'likes', 'map', 'submission', 'turnstileSiteKey']);
  assert.deepEqual(S.Health.properties.status.enum, ['UP', 'DOWN']);
});

test('every response with a JSON example has examples; successful 200s carry examples', () => {
  for (const { path, method, op } of operations()) {
    const r = op.responses['200'] ?? op.responses['202'];
    const mt = r.content?.['application/json'];
    assert.ok(mt.example !== undefined || mt.examples, `${method} ${path} has no example`);
  }
});

test('test vectors: links.json covers every LinkPlatform at least twice and specific cases', () => {
  const links = JSON.parse(readFileSync(here('../test-vectors/links.json'), 'utf8'));
  assert.ok(links.length >= 30);
  const counts = {};
  for (const l of links) {
    assert.ok(S.LinkPlatform.enum.includes(l.platform), `unknown platform ${l.platform}`);
    assert.equal(typeof l.url, 'string');
    counts[l.platform] = (counts[l.platform] ?? 0) + 1;
  }
  for (const p of S.LinkPlatform.enum) assert.ok((counts[p] ?? 0) >= 2, `${p} has < 2 rows`);
  const find = (needle) => links.find((l) => l.url.includes(needle));
  assert.equal(find('music.youtube.com').platform, 'YOUTUBE_MUSIC');
  assert.equal(find('youtu.be').platform, 'YOUTUBE');
  assert.equal(find('uk.m.wikipedia.org').platform, 'WIKIPEDIA');
  assert.equal(find('t.me').platform, 'TELEGRAM');
  assert.equal(find('x.com').platform, 'X');
  assert.equal(find('twitter.com').platform, 'X');
  assert.equal(find('mailto:').platform, 'OTHER');
  assert.ok(links.some((l) => l.platform === 'WEBSITE'));
});

test('test vectors: names.json is an array', () => {
  assert.ok(Array.isArray(JSON.parse(readFileSync(here('../test-vectors/names.json'), 'utf8'))));
});

test('origin, placeName and region are required but nullable (musician outside Ukraine)', () => {
  const anyNull = (x) => x.anyOf?.some((y) => y.type === 'null');
  assert.ok(S.MusicianDetails.required.includes('origin'));
  assert.ok(anyNull(S.MusicianDetails.properties.origin), 'origin nullable');
  assert.ok(S.MusicianDetails.properties.origin.anyOf.some((y) => y.$ref === '#/components/schemas/Origin'));
  assert.ok(S.MusicianListItem.required.includes('placeName'));
  assert.ok(nullable(S.MusicianListItem.properties.placeName), 'placeName nullable');
  assert.ok(S.MusicianListItem.required.includes('region'));
  assert.ok(anyNull(S.MusicianListItem.properties.region), 'region nullable');
  assert.ok(S.MusicianListItem.properties.region.anyOf.some((y) => y.$ref === '#/components/schemas/Region'));
});
