import type { components } from '../../api/types.gen'
import type { MapItem } from '../../core/map/aggregation'
import { pixelDistance } from './mercator'
import { MAX_ZOOM } from './viewport'

type MapPin = components['schemas']['MapPin']
type PlaceStat = components['schemas']['PlaceStat']

export const MAX_DOM_MARKERS = 300
/** Search radius for a cluster's name; matches the place index cluster radius. */
const CLUSTER_NAME_RADIUS_PX = 60

/** Above `max` items only aggregated markers are kept (capped at `max`), so the DOM never holds more than `max`. */
export function capMarkers(items: MapItem[], max = MAX_DOM_MARKERS): { items: MapItem[]; capped: boolean } {
  if (items.length <= max) return { items, capped: false }
  return { items: items.filter((i) => i.kind !== 'pin' && i.kind !== 'place').slice(0, max), capped: true }
}

export function itemKey(item: MapItem): string {
  switch (item.kind) {
    case 'region': return `r:${item.code}`
    case 'place': return `p:${item.placeId}`
    case 'placeCluster': return `pc:${item.id}`
    case 'pinCluster': return `c:${item.id}`
    case 'pin': return `m:${item.pin.id}`
  }
}

/** Pins on identical coordinates stay one cluster at every zoom; at the top zoom they open as a list. */
export function isUnsplittableStack(item: MapItem, expansionZoom: number, zoom: number): boolean {
  return item.kind === 'pinCluster' && expansionZoom > MAX_ZOOM && zoom >= MAX_ZOOM
}

// Plain lon/lat distance is enough to rank points that are a few hundred metres apart.
const squaredDistance = (lon: number, lat: number, p: { lon: number; lat: number }) => {
  const k = Math.cos((lat * Math.PI) / 180)
  return ((p.lon - lon) * k) ** 2 + (p.lat - lat) ** 2
}

export function nearestPin(pins: MapPin[], lon: number, lat: number): MapPin | null {
  let best: MapPin | null = null
  let bestDistance = Infinity
  for (const p of pins) {
    const d = squaredDistance(lon, lat, p)
    if (d < bestDistance) [best, bestDistance] = [p, d]
  }
  return best
}

/** Members of a pin cluster that cannot be split any more: the `count` pins closest to its centre. */
export function stackPins(pins: MapPin[], lon: number, lat: number, count: number): MapPin[] {
  return pins
    .map((p) => ({ p, d: squaredDistance(lon, lat, p) }))
    .sort((a, b) => a.d - b.d)
    .slice(0, count)
    .map(({ p }) => p)
}

/**
 * A place cluster is named after its largest place: the biggest within the cluster radius, else the nearest.
 * `standalone` places have their own bubble at this zoom, so they cannot be members.
 */
export function largestPlaceNear(
  places: PlaceStat[],
  lon: number,
  lat: number,
  zoom: number,
  standalone: ReadonlySet<string> = new Set(),
): PlaceStat | null {
  let largest: PlaceStat | null = null
  let nearest: PlaceStat | null = null
  let nearestDistance = Infinity
  for (const p of places) {
    if (standalone.has(p.placeId)) continue
    const d = pixelDistance(lon, lat, p.lon, p.lat, zoom)
    if (d <= CLUSTER_NAME_RADIUS_PX && (!largest || p.count > largest.count)) largest = p
    if (d < nearestDistance) [nearest, nearestDistance] = [p, d]
  }
  return largest ?? nearest
}
