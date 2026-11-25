import { describe, expect, it } from 'vitest'
import type { components } from '../../api/types.gen'
import type { MapItem } from '../../core/map/aggregation'
import { capMarkers, isUnsplittableStack, itemKey, largestPlaceNear, nearestPin, stackPins } from './markers'

type MapPin = components['schemas']['MapPin']
type PlaceStat = components['schemas']['PlaceStat']

const pin = (id: string, lon: number, lat: number, placeId = 'p'): MapPin => ({
  id, slug: id, name: id, kind: 'PERSON', lon, lat, region: 'LVIV', placeId, hasAudio: false,
})
const place = (placeId: string, lon: number, lat: number, count: number): PlaceStat => ({
  placeId, name: placeId, region: 'LVIV', type: 'CITY', lon, lat, count,
})
const single = (id: string): MapItem => ({ kind: 'pin', pin: pin(id, 24, 49) })
const cluster = (id: number): MapItem => ({ kind: 'pinCluster', id, lon: 24, lat: 49, count: 2 })

describe('capMarkers', () => {
  it('keeps every item up to the limit', () => {
    const items = [single('a'), cluster(1)]
    expect(capMarkers(items, 2)).toEqual({ items, capped: false })
  })

  it('above the limit keeps only clusters, at most the limit', () => {
    const items = [single('a'), cluster(1), single('b'), cluster(2), cluster(3)]
    expect(capMarkers(items, 2)).toEqual({ items: [cluster(1), cluster(2)], capped: true })
    expect(capMarkers(items, 4)).toEqual({ items: [cluster(1), cluster(2), cluster(3)], capped: true })
  })

  it('defaults to 300 DOM markers', () => {
    const many = Array.from({ length: 301 }, (_, i) => single(String(i)))
    expect(capMarkers(many).items).toHaveLength(0)
    expect(capMarkers(many.slice(0, 300)).capped).toBe(false)
  })
})

describe('itemKey', () => {
  it('is unique per kind even when ids coincide', () => {
    const keys = [
      itemKey({ kind: 'region', code: 'LVIV', name: 'L', lon: 0, lat: 0, count: 1 }),
      itemKey({ kind: 'place', placeId: '1', name: 'P', lon: 0, lat: 0, count: 1 }),
      itemKey({ kind: 'placeCluster', id: 1, lon: 0, lat: 0, count: 1 }),
      itemKey({ kind: 'pinCluster', id: 1, lon: 0, lat: 0, count: 1 }),
      itemKey(single('1')),
    ]
    expect(new Set(keys).size).toBe(5)
  })
})

describe('stackPins', () => {
  const pins = [pin('far', 24.1, 49.9), pin('b', 24, 49), pin('a', 24, 49), pin('near', 24.0001, 49)]

  it('returns the `count` pins closest to the cluster centre', () => {
    expect(stackPins(pins, 24, 49, 2).map((p) => p.id).sort()).toEqual(['a', 'b'])
    expect(stackPins(pins, 24, 49, 3).map((p) => p.id).sort()).toEqual(['a', 'b', 'near'])
  })

  it('never returns more pins than exist', () => {
    expect(stackPins(pins, 24, 49, 10)).toHaveLength(4)
  })
})

describe('nearestPin', () => {
  it('finds the closest pin, or null without pins', () => {
    expect(nearestPin([pin('x', 30, 50), pin('y', 24.01, 49.01)], 24, 49)?.id).toBe('y')
    expect(nearestPin([], 24, 49)).toBeNull()
  })
})

describe('largestPlaceNear', () => {
  const places = [place('village', 24.001, 49.001, 2), place('city', 24.02, 49.02, 270), place('other-city', 26, 49, 900)]

  it('names a cluster after the largest place within the radius around its centre', () => {
    // at zoom 9 one pixel is ~0.0014° of longitude, so 60 px cover the city but not the other city
    expect(largestPlaceNear(places, 24, 49, 9)?.placeId).toBe('city')
  })

  it('skips places that are on the map as their own bubble: they are not members of the cluster', () => {
    expect(largestPlaceNear(places, 24, 49, 9, new Set(['city']))?.placeId).toBe('village')
  })

  it('falls back to the nearest place when none is within the radius', () => {
    expect(largestPlaceNear(places, 24, 49, 16)?.placeId).toBe('village')
    expect(largestPlaceNear([], 24, 49, 9)).toBeNull()
  })
})

describe('isUnsplittableStack', () => {
  const placeCluster: MapItem = { kind: 'placeCluster', id: 1, lon: 24, lat: 49, count: 3 }

  it('is a pin cluster that no zoom splits, seen at the top zoom', () => {
    expect(isUnsplittableStack(cluster(1), 18, 17)).toBe(true)
  })

  it('below the top zoom the camera flies in first', () => {
    expect(isUnsplittableStack(cluster(1), 18, 12)).toBe(false)
  })

  it('a cluster that still splits, or a place cluster, is never a stack', () => {
    expect(isUnsplittableStack(cluster(1), 16, 17)).toBe(false)
    expect(isUnsplittableStack(placeCluster, 18, 17)).toBe(false)
  })
})
