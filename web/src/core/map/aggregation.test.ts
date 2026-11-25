import { describe, expect, it } from 'vitest'
import type { components } from '../../api/types.gen'
import { buildIndexes, formatCount, levelForZoom, type AggregationMode, type BBox, type MapItem } from './aggregation'

type MapPin = components['schemas']['MapPin']
type RegionStat = components['schemas']['RegionStat']
type PlaceStat = components['schemas']['PlaceStat']

const T = { regionMaxZoom: 7, placeMaxZoom: 10 }
const WORLD: BBox = [-180, -85, 180, 85]

const pin = (id: string, region: MapPin['region'], placeId: string, lon: number, lat: number): MapPin => ({
  id, slug: id, name: id, kind: 'PERSON', lat, lon, region, placeId, hasAudio: false,
})
const regionStat = (code: RegionStat['code'], lon: number, lat: number, count: number): RegionStat => ({
  code, name: code, center: [lon, lat], bbox: [lon - 1, lat - 1, lon + 1, lat + 1], count,
})
const placeStat = (placeId: string, region: PlaceStat['region'], lon: number, lat: number, count: number): PlaceStat => ({
  placeId, name: placeId, region, type: 'CITY', lat, lon, count,
})

const pins = [
  pin('a', 'LVIV', 'lviv', 24.03, 49.84),
  pin('b', 'LVIV', 'lviv', 24.03, 49.84),
  pin('c', 'LVIV', 'brody', 25.15, 49.65),
  pin('d', 'ODESA', 'odesa', 30.72, 46.48),
]
const regions = [regionStat('LVIV', 24, 49.5, 3), regionStat('ODESA', 30.7, 46.4, 1), regionStat('KYIV', 30.5, 50.4, 0)]
const places = [
  placeStat('lviv', 'LVIV', 24.03, 49.84, 2),
  placeStat('brody', 'LVIV', 25.15, 49.65, 1),
  placeStat('odesa', 'ODESA', 30.72, 46.48, 1),
]

const countOf = (i: MapItem) => (i.kind === 'pin' ? 1 : i.count)

const build = (over: Partial<Parameters<typeof buildIndexes>[0]> = {}) =>
  buildIndexes({ pins, regions, places, filters: {}, thresholds: T, mode: 'REGION_PLACE_PIN', ...over })

describe('levelForZoom', () => {
  it('levels for REGION_PLACE_PIN at zoom 5, 6.99, 7, 9.99, 10', () =>
    expect([5, 6.99, 7, 9.99, 10].map((z) => levelForZoom(z, T, 'REGION_PLACE_PIN')))
      .toEqual(['REGION', 'REGION', 'PLACE', 'PLACE', 'PIN']))

  it('PLACE_PIN never returns REGION', () => {
    expect([5, 7, 9.99, 10, 17].map((z) => levelForZoom(z, T, 'PLACE_PIN'))).toEqual(['PLACE', 'PLACE', 'PLACE', 'PIN', 'PIN'])
  })

  it('CHOROPLETH_PLACE_PIN follows the same thresholds as REGION_PLACE_PIN', () => {
    const modes: AggregationMode[] = ['CHOROPLETH_PLACE_PIN', 'REGION_PLACE_PIN']
    const [a, b] = modes.map((m) => [5, 7, 10].map((z) => levelForZoom(z, T, m)))
    expect(a).toEqual(b)
  })
})

describe('buildIndexes', () => {
  it('exposes level() and total', () => {
    const idx = build()
    expect(idx.level(6)).toBe('REGION')
    expect(idx.total).toBe(4)
  })

  it('region items only for regions with count > 0 and selected regions when filtered', () => {
    const all = build().items(WORLD, 6)
    expect(all).toEqual([
      { kind: 'region', code: 'LVIV', name: 'LVIV', lon: 24, lat: 49.5, count: 3 },
      { kind: 'region', code: 'ODESA', name: 'ODESA', lon: 30.7, lat: 46.4, count: 1 },
    ])
    const filtered = build({ filters: { region: ['ODESA'] } })
    expect(filtered.items(WORLD, 6).map((i) => i.kind === 'region' && i.code)).toEqual(['ODESA'])
    expect(filtered.total).toBe(1)
  })

  it('place counts are recomputed from filtered pins', () => {
    const items = build({ filters: { region: ['LVIV'] } }).items(WORLD, 9)
    const counts = Object.fromEntries(items.map((i) => [i.kind === 'place' ? i.placeId : '?', countOf(i)]))
    expect(counts).toEqual({ lviv: 2, brody: 1 })
  })

  it('uses server counts until pins are loaded', () => {
    const idx = build({ pins: [] })
    expect(idx.total).toBe(4)
    expect(idx.items(WORLD, 6).map(countOf)).toEqual([3, 1])
    expect(idx.items(WORLD, 9).filter((i) => i.kind === 'place').map(countOf).sort()).toEqual([1, 1, 2])
    expect(build({ pins: [], filters: { region: ['ODESA'] } }).items(WORLD, 6).map(countOf)).toEqual([1])
  })

  it('nearby places merge into a placeCluster with summed count at zoom 7', () => {
    const near = [placeStat('x', 'LVIV', 24.0, 49.8, 2), placeStat('y', 'LVIV', 24.05, 49.82, 3), placeStat('far', 'ODESA', 30.7, 46.4, 1)]
    const items = build({ places: near, pins: [] }).items(WORLD, 7)
    const cluster = items.find((i) => i.kind === 'placeCluster')
    expect(cluster).toMatchObject({ kind: 'placeCluster', count: 5 })
    expect(items.find((i) => i.kind === 'place')).toMatchObject({ placeId: 'far', name: 'far', count: 1 })
    expect(build({ places: near, pins: [] }).expansionZoom(cluster!)).toBeGreaterThan(7)
  })

  it('pins inside bbox only', () => {
    const items = build().items([29, 45, 32, 48], 12)
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({ kind: 'pin', pin: { id: 'd' } })
  })

  it('identical coordinates stay one pinCluster up to zoom 17', () => {
    const idx = build()
    const bbox: BBox = [23, 49, 25, 50.5]
    const at17 = idx.items(bbox, 17).filter((i) => i.kind === 'pinCluster')
    expect(at17).toHaveLength(1)
    expect(countOf(at17[0])).toBe(2)
    expect(idx.expansionZoom(at17[0])).toBeGreaterThan(17)
  })

  it('expansionZoom for non-cluster items', () => {
    const idx = build()
    const [region] = idx.items(WORLD, 6)
    expect(idx.expansionZoom(region)).toBe(7)
    const place = idx.items(WORLD, 9).find((i) => i.kind === 'place')!
    expect(idx.expansionZoom(place)).toBe(10)
    expect(idx.expansionZoom({ kind: 'pin', pin: pins[0] })).toBe(17)
  })
})

describe('formatCount', () => {
  it('formatCount', () => expect([7, 999, 1234, 4000].map(formatCount)).toEqual(['7', '999', '1,2 тис.', '4 тис.']))
})
