// Static server for Lighthouse runs: the built SPA plus the read-only API the pages need on load, answered from
// contracts/fixtures. The browser MSW mock would add ~0.5 MB gzip of fixtures to every page and distort the score.
import { createReadStream, existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { dirname, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createGzip, gzipSync } from 'node:zlib'

const here = dirname(fileURLToPath(import.meta.url))
const dist = resolve(here, '../dist-lhci')
const fixtures = resolve(here, '../../contracts/fixtures')
const port = Number(process.env.PORT ?? 4173)

const json = (name) => JSON.parse(readFileSync(join(fixtures, name), 'utf8'))
// Fixture photos hotlink other sites; Lighthouse runs against a local stand-in so they stay offline and cookie-free.
const localPhoto = '/fixtures/img/extra-1.svg'
const musicians = readdirSync(join(fixtures, 'musicians')).map((f) => {
  const m = json(`musicians/${f}`)
  return { ...m, images: (m.images ?? []).map((i) => ({ ...i, url: localPhoto, thumbUrl: localPhoto })) }
})
const publicConfig = {
  fragmentSeconds: 15,
  map: { regionMaxZoom: 7, placeMaxZoom: 10, minZoom: 5, maxZoom: 17 },
  likes: { batchMax: 50, flushMs: 600 },
  submission: { maxImages: 5, maxImageMb: 10, summaryMin: 30, summaryMax: 1000 },
  turnstileSiteKey: null,
}
const api = {
  '/map/pins': () => json('map-pins.json'),
  '/map/regions': () => json('map-regions.json'),
  '/map/places': () => json('map-places.json'),
  '/filters': () => json('filters.json'),
  '/config/public': () => publicConfig,
}

const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.geojson': 'application/json', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.jpg': 'image/jpeg', '.png': 'image/png', '.mp3': 'audio/mpeg' }
const compressible = new Set(['.html', '.js', '.css', '.json', '.geojson', '.svg'])

createServer((req, res) => {
  const url = new URL(req.url, 'http://x')
  if (url.pathname.startsWith('/api/v2/')) {
    const path = url.pathname.slice('/api/v2'.length)
    const slug = path.match(/^\/musicians\/([^/]+)$/)?.[1]
    const body = api[path]?.() ?? musicians.find((m) => m.slug === slug)
    const text = Buffer.from(JSON.stringify(body ?? { status: 404 }))
    const gz = /\bgzip\b/.test(req.headers['accept-encoding'] ?? '')
    res.writeHead(body ? 200 : 404, { 'Content-Type': 'application/json', ...(gz && { 'Content-Encoding': 'gzip' }) })
    res.end(gz ? gzipSync(text) : text)
    return
  }
  let file = join(dist, decodeURIComponent(url.pathname))
  if (!file.startsWith(dist) || !existsSync(file) || !statSync(file).isFile()) file = join(dist, 'index.html')
  const ext = extname(file)
  const headers = { 'Content-Type': types[ext] ?? 'application/octet-stream', 'Cache-Control': 'public, max-age=3600' }
  const gzip = compressible.has(ext) && /\bgzip\b/.test(req.headers['accept-encoding'] ?? '')
  if (gzip) headers['Content-Encoding'] = 'gzip'
  res.writeHead(200, headers)
  const stream = createReadStream(file)
  if (gzip) stream.pipe(createGzip()).pipe(res)
  else stream.pipe(res)
}).listen(port, '127.0.0.1', () => console.log(`Local: http://127.0.0.1:${port}/`))
