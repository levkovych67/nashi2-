import type { components } from '../../api/types.gen'
import type { MapItem } from '../../core/map/aggregation'
import { pixelDistance, TILE_SIZE } from './mercator'
import type { PointIndex } from './pointIndex'
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
  places: PointIndex<PlaceStat>,
  lon: number,
  lat: number,
  zoom: number,
  standalone: ReadonlySet<string> = new Set(),
): PlaceStat | null {
  const lonRadius = (CLUSTER_NAME_RADIUS_PX * 360) / (TILE_SIZE * 2 ** zoom)
  let largest: PlaceStat | null = null
  for (const p of places.nearLon(lon, lonRadius)) {
    if (standalone.has(p.placeId) || pixelDistance(lon, lat, p.lon, p.lat, zoom) > CLUSTER_NAME_RADIUS_PX) continue
    if (!largest || p.count > largest.count) largest = p
  }
  return largest ?? places.nearest(lon, lat, (p) => standalone.has(p.placeId))
}

/**
 * Names for the cluster bubbles in `items` (by `itemKey`), computed once per set of items: a place cluster after its
 * largest place, a pin cluster after the place of its nearest pin. `zoom` is the zoom the clusters were built at.
 */
export function clusterNames(
  items: MapItem[],
  places: PointIndex<PlaceStat>,
  pins: PointIndex<MapPin>,
  placeName: ReadonlyMap<string, string>,
  zoom: number,
): Map<string, string> {
  const standalone = new Set(items.flatMap((i) => (i.kind === 'place' ? [i.placeId] : [])))
  const names = new Map<string, string>()
  for (const item of items) {
    if (item.kind === 'placeCluster') {
      names.set(itemKey(item), largestPlaceNear(places, item.lon, item.lat, zoom, standalone)?.name ?? '')
    } else if (item.kind === 'pinCluster') {
      const near = pins.nearest(item.lon, item.lat)
      names.set(itemKey(item), (near && placeName.get(near.placeId)) ?? '')
    }
  }
  return names
}
