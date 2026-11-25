// Performance budgets read from the built dist (run `npm run build -w web` first):
//  - start JS (the static import graph of index.html, which every route loads) <= 180 KB gzip, with no maplibre in it;
//  - the `maplibre` chunk (maplibre-gl + react-map-gl) is reachable from the map route `/` only, never from
//    `/m/:slug`, `/suggest`, `/about` or the not-found page;
//  - a route's whole JS (start + its page chunk and what that imports) without maplibre stays under ROUTE_BUDGET_KB.
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

export const START_BUDGET_KB = 180
// Guard against regressions on top of the brief: the map route carries the Ukraine geometry the core bundles (~90 KB gzip).
export const ROUTE_BUDGET_KB = 250
export const MAP_CHUNK = 'maplibre'

export const ROUTES = {
  '/': 'src/pages/MapPage.tsx',
  '/m/:slug': 'src/pages/MusicianPage.tsx',
  '/suggest': 'src/pages/SuggestPage.tsx',
  '/about': 'src/pages/AboutPage.tsx',
  '*': 'src/pages/NotFoundPage.tsx',
}

/** Static import closure of a manifest entry (entry included). */
export function closure(manifest, entry) {
  const seen = new Set()
  const visit = (key) => {
    if (seen.has(key)) return
    seen.add(key)
    for (const dep of manifest[key]?.imports ?? []) visit(dep)
  }
  visit(entry)
  return [...seen]
}

const isMap = (manifest, key) => manifest[key].file.endsWith('.js') && manifest[key].file.split('/').pop().startsWith(`${MAP_CHUNK}-`)

/** `sizeOf(file)` returns gzip bytes. Returns the measurements and the list of broken budgets. */
export function analyze(manifest, sizeOf, { entry = 'index.html', routes = ROUTES } = {}) {
  const errors = []
  const kb = (keys) => keys.reduce((sum, k) => sum + sizeOf(manifest[k].file), 0) / 1024
  const jsOnly = (keys) => keys.filter((k) => manifest[k].file.endsWith('.js'))

  const mapKey = Object.keys(manifest).find((k) => isMap(manifest, k))
  if (!mapKey) errors.push(`no "${MAP_CHUNK}" chunk in the build: maplibre is not split out`)

  const start = jsOnly(closure(manifest, entry))
  const startKb = kb(start)
  if (startKb > START_BUDGET_KB) errors.push(`start JS is ${startKb.toFixed(1)} KB gzip, budget ${START_BUDGET_KB} KB`)
  if (mapKey && start.includes(mapKey)) errors.push('the start graph (index.html) loads the maplibre chunk')

  const perRoute = {}
  for (const [route, page] of Object.entries(routes)) {
    if (!manifest[page]) {
      errors.push(`route ${route}: ${page} is not in the manifest`)
      continue
    }
    const all = jsOnly([...new Set([...closure(manifest, entry), ...closure(manifest, page)])])
    const withMap = mapKey ? all.includes(mapKey) : false
    const withoutMap = all.filter((k) => k !== mapKey)
    perRoute[route] = { kb: kb(withoutMap), mapKb: withMap ? kb([mapKey]) : 0, withMap }
    if (route === '/' && mapKey && !withMap) errors.push('route /: the map route does not load the maplibre chunk')
    if (route !== '/' && withMap) errors.push(`route ${route}: loads the maplibre chunk, only "/" may`)
    if (perRoute[route].kb > ROUTE_BUDGET_KB) errors.push(`route ${route}: ${perRoute[route].kb.toFixed(1)} KB gzip without maplibre, budget ${ROUTE_BUDGET_KB} KB`)
  }
  return { startKb, mapKb: mapKey ? kb([mapKey]) : 0, perRoute, errors }
}

function main() {
  const dist = resolve(dirname(fileURLToPath(import.meta.url)), '../dist')
  const manifest = JSON.parse(readFileSync(join(dist, '.vite/manifest.json'), 'utf8'))
  const sizeOf = (file) => gzipSync(readFileSync(join(dist, file)), { level: 9 }).length
  const { startKb, mapKb, perRoute, errors } = analyze(manifest, sizeOf)
  console.log(`start JS: ${startKb.toFixed(1)} KB gzip (budget ${START_BUDGET_KB})`)
  console.log(`maplibre chunk: ${mapKb.toFixed(1)} KB gzip`)
  for (const [route, r] of Object.entries(perRoute)) {
    console.log(`  ${route.padEnd(10)} ${r.kb.toFixed(1).padStart(6)} KB without maplibre${r.withMap ? ` + maplibre ${r.mapKb.toFixed(1)} KB` : ''}`)
  }
  if (errors.length) {
    for (const e of errors) console.error(`FAIL ${e}`)
    process.exit(1)
  }
  console.log('bundle budgets OK')
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
