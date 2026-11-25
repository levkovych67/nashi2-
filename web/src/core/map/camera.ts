import type { components } from '../../api/types.gen'
import type { BBox } from './aggregation'

type RegionStat = components['schemas']['RegionStat']

export interface Insets { top: number; right: number; bottom: number; left: number }
export type CameraMove =
  | { type: 'fitBounds'; bounds: [[number, number], [number, number]]; padding: Insets; duration: number }
  | { type: 'flyTo'; center: [number, number]; zoom: number; duration: number }

export const UKRAINE_BBOX: BBox = [22.137, 44.361, 40.228, 52.379]

const PADDING = 40
const MAX_ZOOM = 17
const dur = (ms: number, reduced: boolean) => (reduced ? 0 : ms)

function fit(bbox: BBox, insets: Insets, reduced: boolean): CameraMove {
  const [west, south, east, north] = bbox
  return {
    type: 'fitBounds',
    bounds: [[west, south], [east, north]],
    padding: {
      top: PADDING + insets.top,
      right: PADDING + insets.right,
      bottom: PADDING + insets.bottom,
      left: PADDING + insets.left,
    },
    duration: dur(1200, reduced),
  }
}

export function ukraineCamera(insets: Insets, reducedMotion: boolean): CameraMove {
  return fit(UKRAINE_BBOX, insets, reducedMotion)
}

/** Fits the union of the regions' bboxes; no regions means the whole country. */
export function regionsCamera(regions: RegionStat[], insets: Insets, reducedMotion: boolean): CameraMove {
  if (regions.length === 0) return ukraineCamera(insets, reducedMotion)
  const union = regions.reduce<BBox>(
    ([w, s, e, n], r) => [Math.min(w, r.bbox[0]), Math.min(s, r.bbox[1]), Math.max(e, r.bbox[2]), Math.max(n, r.bbox[3])],
    [Infinity, Infinity, -Infinity, -Infinity],
  )
  return fit(union, insets, reducedMotion)
}

export function placeCamera(lon: number, lat: number, reducedMotion: boolean): Extract<CameraMove, { type: 'flyTo' }> {
  return { type: 'flyTo', center: [lon, lat], zoom: 12, duration: dur(1200, reducedMotion) }
}

export function clusterCamera(
  lon: number,
  lat: number,
  expansionZoom: number,
  reducedMotion: boolean,
): Extract<CameraMove, { type: 'flyTo' }> {
  return { type: 'flyTo', center: [lon, lat], zoom: Math.min(expansionZoom, MAX_ZOOM), duration: dur(600, reducedMotion) }
}
