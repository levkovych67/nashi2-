import assert from 'node:assert/strict'
import { test } from 'node:test'
import { analyze } from './check-bundle.mjs'

const KB = 1024
const sizes = { 'a/index.js': 100 * KB, 'a/vendor.js': 50 * KB, 'a/maplibre-x.js': 300 * KB, 'a/MapPage.js': 10 * KB, 'a/Musician.js': 5 * KB }
const sizeOf = (f) => sizes[f] ?? 0
const base = () => ({
  'index.html': { file: 'a/index.js', imports: ['v'] },
  v: { file: 'a/vendor.js' },
  m: { file: 'a/maplibre-x.js', imports: ['v'] },
  'src/pages/MapPage.tsx': { file: 'a/MapPage.js', imports: ['index.html', 'm'] },
  'src/pages/MusicianPage.tsx': { file: 'a/Musician.js', imports: ['index.html'] },
})
const routes = { '/': 'src/pages/MapPage.tsx', '/m/:slug': 'src/pages/MusicianPage.tsx' }

test('a clean build passes and excludes maplibre from the sizes', () => {
  const r = analyze(base(), sizeOf, { routes })
  assert.deepEqual(r.errors, [])
  assert.equal(r.startKb, 150)
  assert.equal(r.perRoute['/'].kb, 160)
  assert.equal(r.perRoute['/'].withMap, true)
  assert.equal(r.perRoute['/m/:slug'].withMap, false)
})

test('a route over the first-load budget fails', () => {
  const s = { ...sizes, 'a/MapPage.js': 40 * KB }
  assert.match(analyze(base(), (f) => s[f] ?? 0, { routes }).errors[0], /route \/:/)
})

test('start JS over budget fails', () => {
  const s = { ...sizes, 'a/index.js': 140 * KB }
  assert.match(analyze(base(), (f) => s[f] ?? 0, { routes }).errors[0], /start JS/)
})

test('maplibre reachable from the start graph or another route fails', () => {
  const m = base()
  m['index.html'].imports.push('m')
  const errors = analyze(m, sizeOf, { routes }).errors.join('\n')
  assert.match(errors, /start graph/)
  assert.match(errors, /route \/m\/:slug: loads the maplibre chunk/)
})

test('a build without a maplibre chunk fails', () => {
  const m = base()
  delete m.m
  m['src/pages/MapPage.tsx'].imports = ['index.html']
  assert.match(analyze(m, sizeOf, { routes }).errors[0], /not split out/)
})
