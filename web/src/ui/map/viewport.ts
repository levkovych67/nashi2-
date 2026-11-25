import type { Map as MaplibreMap } from 'maplibre-gl'
import type { BBox } from '../../core/map/aggregation'
import { UKRAINE_BBOX, type CameraMove, type Insets } from '../../core/map/camera'
import { latOfY, lonOfX, mercatorX, mercatorY, TILE_SIZE } from './mercator'

export const MIN_ZOOM = 5
export const MAX_ZOOM = 17
const BOUNDS_MARGIN_DEG = 1
const SLACK = 1.001 // keeps the overview strictly inside maxBounds despite rounding

export interface Size { width: number; height: number }

/** Zoom at which `bbox` fills `size` minus `padding`, like MapLibre's fitBounds. */
export function fitZoom([west, south, east, north]: BBox, size: Size, padding: Insets): number {
  const spanX = (mercatorX(east) - mercatorX(west)) * TILE_SIZE
  const spanY = (mercatorY(south) - mercatorY(north)) * TILE_SIZE
  const zoomX = Math.log2((size.width - padding.left - padding.right) / spanX)
  const zoomY = Math.log2((size.height - padding.top - padding.bottom) / spanY)
  return Math.min(zoomX, zoomY)
}

/**
 * Zoom and pan limits: zoom 5…17 and UKRAINE_BBOX ± 1°. On a phone the whole country (D-10) only fits below
 * zoom 5 and needs more room than ± 1° (MapLibre zooms in until the viewport lies inside maxBounds), so the minimum
 * zoom drops to the overview's and the bounds widen to hold the overview viewport.
 */
export function mapLimits(size: Size, padding: Insets): { minZoom: number; maxBounds: BBox } {
  const overviewZoom = fitZoom(UKRAINE_BBOX, size, padding)
  const world = TILE_SIZE * 2 ** overviewZoom
  const [west, south, east, north] = UKRAINE_BBOX
  const cx = (mercatorX(west) + mercatorX(east)) / 2
  const cy = (mercatorY(north) + mercatorY(south)) / 2
  const halfX = Math.max(
    cx - mercatorX(west - BOUNDS_MARGIN_DEG),
    (size.width + Math.abs(padding.left - padding.right)) / 2 / world,
  ) * SLACK
  const halfY = Math.max(
    mercatorY(south - BOUNDS_MARGIN_DEG) - cy,
    cy - mercatorY(north + BOUNDS_MARGIN_DEG),
    (size.height + Math.abs(padding.top - padding.bottom)) / 2 / world,
  ) * SLACK
  return {
    minZoom: Math.min(MIN_ZOOM, overviewZoom),
    maxBounds: [lonOfX(cx - halfX), latOfY(cy + halfY), lonOfX(cx + halfX), latOfY(cy - halfY)],
  }
}

type CameraTarget = Pick<MaplibreMap, 'flyTo' | 'fitBounds' | 'cameraForBounds'>

/** Runs a core camera move; `minZoom` makes a fit land at least on that zoom (opening a region reaches its places). */
export function moveCamera(map: CameraTarget, move: CameraMove, minZoom?: number) {
  if (move.type === 'flyTo') {
    map.flyTo({ center: move.center, zoom: move.zoom, duration: move.duration, essential: true })
    return
  }
  if (minZoom === undefined) {
    map.fitBounds(move.bounds, { padding: move.padding, duration: move.duration, essential: true })
    return
  }
  const fit = map.cameraForBounds(move.bounds, { padding: move.padding })
  if (!fit) return
  map.flyTo({ center: fit.center, zoom: Math.max(fit.zoom ?? minZoom, minZoom), duration: move.duration, essential: true })
}

export const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
