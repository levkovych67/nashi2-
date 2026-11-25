import { delay, http, HttpResponse } from 'msw'
import { API_BASE } from '../client'
import type { components } from '../types.gen'
import mapPins from '../../../../contracts/fixtures/map-pins.json'
import mapRegions from '../../../../contracts/fixtures/map-regions.json'
import mapPlaces from '../../../../contracts/fixtures/map-places.json'
import filters from '../../../../contracts/fixtures/filters.json'
import placesSearch from '../../../../contracts/fixtures/places-search.json'

type Schemas = components['schemas']

const musicians = Object.values(
  import.meta.glob<Schemas['MusicianDetails']>('../../../../contracts/fixtures/musicians/*.json', {
    eager: true,
    import: 'default',
  }),
)
const fragments = import.meta.glob<Schemas['Fragment']>('../../../../contracts/fixtures/fragments/*.json', {
  import: 'default',
})

// 02-contracts section 2.11 example.
const publicConfig: Schemas['PublicConfig'] = {
  fragmentSeconds: 15,
  map: { regionMaxZoom: 7, placeMaxZoom: 10, minZoom: 5, maxZoom: 17 },
  likes: { batchMax: 50, flushMs: 600 },
  submission: { maxImages: 5, maxImageMb: 10, summaryMin: 30, summaryMax: 1000 },
  turnstileSiteKey: null,
}

// Kept in sessionStorage when there is one, so a page reload in the browser mock still sees the likes sent earlier.
const LIKES_KEY = 'nashi-mock-likes'
const stored = (): [string, number][] => {
  try {
    return JSON.parse(globalThis.sessionStorage?.getItem(LIKES_KEY) ?? '[]')
  } catch {
    return []
  }
}
const likes = new Map<string, number>(stored())
const saveLikes = () => {
  try {
    globalThis.sessionStorage?.setItem(LIKES_KEY, JSON.stringify([...likes]))
  } catch {
    // storage blocked: the counter then lives until the next reload
  }
}
const likesOf = (m: Schemas['MusicianDetails']) => m.likesCount + (likes.get(m.id) ?? 0)

const norm = (s: string) =>
  s.normalize('NFD').replace(/[̀-ͯ'’ʼ`]/g, '').toLowerCase()

function problem(status: number, code: string, title: string) {
  return HttpResponse.json(
    { type: `https://nashi.example/problems/${code.toLowerCase()}`, title, status, code, errors: [] },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

const url = (path: string) => `${API_BASE}${path}`

export const handlers = [
  http.get(url('/map/pins'), () => HttpResponse.json(mapPins)),
  http.get(url('/map/regions'), () => HttpResponse.json(mapRegions)),
  http.get(url('/map/places'), () => HttpResponse.json(mapPlaces)),
  http.get(url('/filters'), () => HttpResponse.json(filters)),
  http.get(url('/config/public'), () => HttpResponse.json(publicConfig)),
  http.get(url('/health'), () => HttpResponse.json({ status: 'UP' })),

  http.get(url('/musicians'), ({ request }) => {
    const sp = new URL(request.url).searchParams
    const regions = sp.getAll('region')
    const q = norm(sp.get('q') ?? '')
    const sort = sp.get('sort') ?? 'NAME'
    const page = Math.max(0, Number(sp.get('page') ?? 0))
    const size = Math.min(100, Math.max(1, Number(sp.get('size') ?? 20)))
    let items: Schemas['MusicianListItem'][] = musicians.map((m) => ({
      id: m.id,
      slug: m.slug,
      name: m.name,
      kind: m.kind,
      thumbUrl: m.images[0]?.thumbUrl,
      placeName: m.origin?.placeName ?? null,
      region: m.origin?.region ?? null,
      likesCount: likesOf(m),
      hasAudio: m.hasAudio,
    }))
    if (regions.length) items = items.filter((m) => m.region && regions.includes(m.region))
    if (q.length >= 2) items = items.filter((m) => norm(m.name).includes(q))
    if (sort === 'LIKES') items.sort((a, b) => b.likesCount - a.likesCount)
    else items.sort((a, b) => a.name.localeCompare(b.name, 'uk')) // RECENT has no fixture signal
    return HttpResponse.json({
      items: items.slice(page * size, (page + 1) * size),
      page,
      size,
      totalItems: items.length,
      totalPages: Math.ceil(items.length / size),
    })
  }),

  http.get(url('/musicians/:slug'), ({ params }) => {
    const m = musicians.find((x) => x.slug === params.slug)
    return m ? HttpResponse.json({ ...m, likesCount: likesOf(m) }) : problem(404, 'NOT_FOUND', 'Не знайдено')
  }),

  http.get(url('/musicians/:id/fragment'), async ({ params }) => {
    const load = fragments[`../../../../contracts/fixtures/fragments/${params.id}.json`]
    return load ? HttpResponse.json(await load()) : new HttpResponse(null, { status: 204 })
  }),

  http.post(url('/musicians/:id/likes'), async ({ params, request }) => {
    const m = musicians.find((x) => x.id === params.id)
    if (!m) return problem(404, 'NOT_FOUND', 'Не знайдено')
    const { count } = (await request.json()) as { count: number }
    if (!Number.isInteger(count) || count < 1 || count > 50) {
      return problem(400, 'VALIDATION_FAILED', 'Некоректні дані')
    }
    likes.set(m.id, (likes.get(m.id) ?? 0) + count)
    saveLikes()
    return HttpResponse.json({ likesCount: likesOf(m) })
  }),

  http.get(url('/places/search'), ({ request }) => {
    const sp = new URL(request.url).searchParams
    const q = norm(sp.get('q') ?? '')
    const region = sp.get('region')
    const limit = Math.min(20, Math.max(1, Number(sp.get('limit') ?? 10)))
    if (q.length < 2) return problem(400, 'VALIDATION_FAILED', 'Некоректні дані')
    const hits = placesSearch.filter(
      (p) => (!region || p.region === region) && norm(p.name).split(/[\s-]+/).some((w) => w.startsWith(q)),
    )
    return HttpResponse.json(hits.slice(0, limit))
  }),

  http.post(url('/submissions'), async () => {
    await delay(1500)
    return HttpResponse.json({ id: 'mock-submission', status: 'PENDING' }, { status: 202 })
  }),
]
