import Supercluster from 'supercluster'
import type { components } from '../../api/types.gen'
import { applyFilters, type FilterState } from '../filters/filterState'

type MapPin = components['schemas']['MapPin']
type RegionStat = components['schemas']['RegionStat']
type PlaceStat = components['schemas']['PlaceStat']
type Region = components['schemas']['Region']

export type AggregationMode = 'REGION_PLACE_PIN' | 'PLACE_PIN' | 'CHOROPLETH_PLACE_PIN'
export type MapLevel = 'REGION' | 'PLACE' | 'PIN'
export interface Thresholds { regionMaxZoom: number; placeMaxZoom: number }
export type BBox = [west: number, south: number, east: number, north: number]
export type MapItem =
  | { kind: 'region'; code: Region; name: string; lon: number; lat: number; count: number }
  | { kind: 'placeCluster'; id: number; lon: number; lat: number; count: number }
  | { kind: 'place'; placeId: string; name: string; lon: number; lat: number; count: number }
  | { kind: 'pinCluster'; id: number; lon: number; lat: number; count: number }
  | { kind: 'pin'; pin: MapPin }
export interface MapIndexes {
  level(zoom: number): MapLevel
  items(bbox: BBox, zoom: number): MapItem[]
  expansionZoom(item: MapItem): number
  total: number
}

const MAX_MAP_ZOOM = 17
const PLACE_RADIUS = 60
const PIN_RADIUS = 40
/** Pins use maxZoom 17 (not 16) so pins on identical coordinates stay a cluster at the map's top zoom. */
const PIN_CLUSTER_MAX_ZOOM = MAX_MAP_ZOOM

export function levelForZoom(zoom: number, t: Thresholds, mode: AggregationMode): MapLevel {
  if (zoom >= t.placeMaxZoom) return 'PIN'
  if (mode !== 'PLACE_PIN' && zoom < t.regionMaxZoom) return 'REGION'
  return 'PLACE'
}

/** 7 → "7", 1234 → "1,2 тис.", 4000 → "4 тис." */
export function formatCount(n: number): string {
  if (n < 1000) return String(n)
  const thousands = (Math.round(n / 100) / 10).toString().replace('.', ',')
  return `${thousands} тис.`
}

function countBy<T>(list: T[], key: (item: T) => string): Map<string, number> {
  const counts = new Map<string, number>()
  for (const item of list) counts.set(key(item), (counts.get(key(item)) ?? 0) + 1)
  return counts
}

interface CountProps { count: number }
interface PlaceProps extends CountProps { placeId: string; name: string }
interface PinProps extends CountProps { pin: MapPin }

function pointFeature<P>(lon: number, lat: number, properties: P): Supercluster.PointFeature<P> {
  return { type: 'Feature', geometry: { type: 'Point', coordinates: [lon, lat] }, properties }
}

function sumCount(acc: CountProps, props: CountProps) {
  acc.count += props.count
}

export function buildIndexes(input: {
  pins: MapPin[]
  regions: RegionStat[]
  places: PlaceStat[]
  filters: FilterState
  thresholds: Thresholds
  mode: AggregationMode
}): MapIndexes {
  const { pins, regions, places, filters, thresholds, mode } = input
  const loaded = pins.length > 0
  const filtered = applyFilters(pins, filters)

  // Before the pins arrive only the server's counts exist; they can honour the region filter but nothing else.
  const selectedRegions = filters.region?.length ? new Set(filters.region) : null
  const regionCounts = loaded
    ? countBy(filtered, (p) => p.region)
    : new Map(regions.filter((r) => !selectedRegions || selectedRegions.has(r.code)).map((r) => [r.code, r.count]))
  const placeCounts = loaded
    ? countBy(filtered, (p) => p.placeId)
    : new Map(places.filter((p) => !selectedRegions || selectedRegions.has(p.region)).map((p) => [p.placeId, p.count]))

  const regionItems: Extract<MapItem, { kind: 'region' }>[] = regions
    .filter((r) => (regionCounts.get(r.code) ?? 0) > 0)
    .map((r) => ({
      kind: 'region' as const, code: r.code, name: r.name, lon: r.center[0], lat: r.center[1], count: regionCounts.get(r.code)!,
    }))

  const placeIndex = new Supercluster<PlaceProps, CountProps>({
    radius: PLACE_RADIUS,
    maxZoom: thresholds.placeMaxZoom,
    map: (p) => ({ count: p.count }),
    reduce: sumCount,
  })
  placeIndex.load(
    places
      .filter((p) => (placeCounts.get(p.placeId) ?? 0) > 0)
      .map((p) => pointFeature(p.lon, p.lat, { placeId: p.placeId, name: p.name, count: placeCounts.get(p.placeId)! })),
  )

  const pinIndex = new Supercluster<PinProps, CountProps>({
    radius: PIN_RADIUS,
    maxZoom: PIN_CLUSTER_MAX_ZOOM,
    map: (p) => ({ count: p.count }),
    reduce: sumCount,
  })
  pinIndex.load(filtered.map((pin) => pointFeature(pin.lon, pin.lat, { pin, count: 1 })))

  const level = (zoom: number) => levelForZoom(zoom, thresholds, mode)

  return {
    level,
    total: loaded ? filtered.length : regionItems.reduce((sum, r) => sum + r.count, 0),
    items(bbox, zoom) {
      const lvl = level(zoom)
      if (lvl === 'REGION') {
        const [w, s, e, n] = bbox
        return regionItems.filter((r) => r.lon >= w && r.lon <= e && r.lat >= s && r.lat <= n)
      }
      const z = Math.floor(zoom)
      if (lvl === 'PLACE') {
        return placeIndex.getClusters(bbox, z).map((f): MapItem => {
          const [lon, lat] = f.geometry.coordinates
          const p = f.properties
          return 'cluster' in p && p.cluster
            ? { kind: 'placeCluster', id: p.cluster_id, lon, lat, count: p.count }
            : { kind: 'place', placeId: (p as PlaceProps).placeId, name: (p as PlaceProps).name, lon, lat, count: p.count }
        })
      }
      return pinIndex.getClusters(bbox, z).map((f): MapItem => {
        const [lon, lat] = f.geometry.coordinates
        const p = f.properties
        return 'cluster' in p && p.cluster
          ? { kind: 'pinCluster', id: p.cluster_id, lon, lat, count: p.count }
          : { kind: 'pin', pin: (p as PinProps).pin }
      })
    },
    expansionZoom(item) {
      switch (item.kind) {
        case 'region': return thresholds.regionMaxZoom
        case 'place': return thresholds.placeMaxZoom
        case 'placeCluster': return Math.min(placeIndex.getClusterExpansionZoom(item.id), thresholds.placeMaxZoom)
        case 'pinCluster': return pinIndex.getClusterExpansionZoom(item.id)
        case 'pin': return MAX_MAP_ZOOM
      }
    },
  }
}
