import Supercluster from 'supercluster'
import type { AtlasData, Region, Place, Pin } from './data'

export type Box = [number, number, number, number]
export type Coord = [number, number]
export interface Camera {
  center: Coord
  zoom: number
}
export interface Dimensions {
  width: number
  height: number
}
export const levelAt = (zoom: number, mode: string) =>
  zoom >= 10 ? 'pin' : zoom >= 7 || mode === 'PLACE_PIN' ? 'place' : 'region'

export function regionAt(
  lon: number,
  lat: number,
  shapes: AtlasData['shapes'],
) {
  function contains(ring: number[][]) {
    let inside = false
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [x, y] = ring[i]
      const [x2, y2] = ring[j]
      if (y > lat !== y2 > lat && lon < ((x2 - x) * (lat - y)) / (y2 - y) + x)
        inside = !inside
    }
    return inside
  }
  const hits = shapes.features.filter(({ geometry }) => {
    const polygons =
      geometry.type === 'Polygon'
        ? [geometry.coordinates]
        : geometry.coordinates
    return polygons.some(
      ([outer, ...holes]) => contains(outer) && !holes.some(contains),
    )
  })
  const feature =
    hits.find((f) => String(f.properties?.code).endsWith('_CITY')) || hits[0]
  return feature ? String(feature.properties?.code) : null
}

export function boundsOf(regions: Region[]): Box {
  return [
    Math.min(...regions.map((r) => r.bbox[0])),
    Math.min(...regions.map((r) => r.bbox[1])),
    Math.max(...regions.map((r) => r.bbox[2])),
    Math.max(...regions.map((r) => r.bbox[3])),
  ]
}
export function worldY(lat: number) {
  const s = Math.sin((Math.max(-85, Math.min(85, lat)) * Math.PI) / 180)
  return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)
}
function latitude(y: number) {
  return (Math.atan(Math.sinh(Math.PI * (1 - 2 * y))) * 180) / Math.PI
}
export function project(
  [lon, lat]: number[],
  view: Camera,
  size: Dimensions,
): Coord {
  const side = 512 * 2 ** view.zoom
  return [
    size.width / 2 + ((lon - view.center[0]) / 360) * side,
    size.height / 2 + (worldY(lat) - worldY(view.center[1])) * side,
  ]
}
export function unproject(
  x: number,
  y: number,
  view: Camera,
  size: Dimensions,
): Coord {
  const side = 512 * 2 ** view.zoom
  return [
    view.center[0] + ((x - size.width / 2) * 360) / side,
    latitude(worldY(view.center[1]) + (y - size.height / 2) / side),
  ]
}
export function viewportBounds(view: Camera, size: Dimensions): Box {
  const nw = unproject(0, 0, view, size)
  const se = unproject(size.width, size.height, view, size)
  return [nw[0], se[1], se[0], nw[1]]
}
export function fit(bounds: Box, size: Dimensions, maxZoom = 9): Camera {
  const [west, south, east, north] = bounds
  const y0 = worldY(north),
    y1 = worldY(south)
  const scale = Math.min(
    Math.max(1, size.width - 60) / ((east - west) / 360),
    Math.max(1, size.height - 100) / (y1 - y0),
  )
  return {
    center: [(west + east) / 2, latitude((y0 + y1) / 2)],
    zoom: Math.min(maxZoom, Math.max(2, Math.log2(scale / 512))),
  }
}

interface Indexed {
  id: string
  name: string
  count: number
  region: string
}
interface Summary {
  count: number
  name: string
  largest: number
}
export function makeIndex(rows: Place[] | Pin[], kind: 'place' | 'pin') {
  const index = new Supercluster<Indexed, Summary>({
    radius: kind === 'place' ? 50 : 40,
    maxZoom: kind === 'place' ? 12 : 15,
    map: (p) => ({ count: p.count, name: p.name, largest: p.count }),
    reduce: (acc, p) => {
      acc.count += p.count
      if (p.largest > acc.largest) {
        acc.name = p.name
        acc.largest = p.largest
      }
    },
  })
  return index.load(
    rows.map((row) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [row.lon, row.lat] },
      properties: {
        id: 'id' in row ? row.id : row.placeId,
        region: row.region,
        name: row.name,
        count: 'count' in row ? row.count : 1,
      },
    })),
  )
}
export function parseRegions(query: string, regions: Region[]) {
  const ids = new Set(
    new URLSearchParams(query).getAll('region').flatMap((s) => s.split(',')),
  )
  return regions.filter((r) => ids.has(r.code)).map((r) => r.code)
}
export function density(count: number, regions: Region[]) {
  return (
    0.15 +
    (0.45 * regions.filter((r) => r.count < count).length) /
      Math.max(1, regions.length - 1)
  )
}
