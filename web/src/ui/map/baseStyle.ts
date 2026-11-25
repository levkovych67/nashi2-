import type { StyleSpecification } from 'maplibre-gl'
import { blankStyle, prepareMapStyle } from '../../core/map/mapStyle'
import designStyle from '../../design/map-style.json'

const OPENFREEMAP_TILES = 'https://tiles.openfreemap.org/planet'
const OPENFREEMAP_GLYPHS = 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf'

/** The handoff style's own outline/region layers; MapOverlays draws them from tokens in both styles. */
export const OVERLAY_LAYER_IDS = ['region-color', 'region-line', 'ua-outline-line']

type MapEnv = Partial<Record<'VITE_MAP_STYLE' | 'VITE_MAP_TILES_URL' | 'VITE_MAP_GLYPHS_URL', string>>

/** `VITE_MAP_STYLE=blank` → offline style; otherwise the handoff style with tile and glyph URLs filled in. */
export function baseMapStyle(env: MapEnv): StyleSpecification {
  if (env.VITE_MAP_STYLE === 'blank') return blankStyle()
  const style = prepareMapStyle(designStyle as StyleSpecification, {
    tiles: env.VITE_MAP_TILES_URL || OPENFREEMAP_TILES,
    glyphs: env.VITE_MAP_GLYPHS_URL || OPENFREEMAP_GLYPHS,
  })
  const layers = style.layers.filter((l) => !OVERLAY_LAYER_IDS.includes(l.id))
  const used = new Set(layers.map((l) => ('source' in l ? l.source : undefined)))
  const sources = Object.fromEntries(Object.entries(style.sources).filter(([id]) => used.has(id)))
  return { ...style, layers, sources }
}
