import type { StyleSpecification } from 'maplibre-gl'

const ukrainianName = () => ['coalesce', ['get', 'name:uk'], ['get', 'name']]

function replaceDeep<T>(value: T, replacements: [string, string][]): T {
  if (typeof value === 'string') {
    return replacements.reduce((s, [from, to]) => s.split(from).join(to), value as string) as T
  }
  if (Array.isArray(value)) return value.map((v) => replaceDeep(v, replacements)) as T
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, replaceDeep(v, replacements)])) as T
  }
  return value
}

/**
 * Pure: fills the URL placeholders, shows Ukrainian names on every layer that has a text-field (fallback: local name) and hides the
 * basemap's own borders, because national and regional borders come from our GeoJSON (with Crimea).
 */
export function prepareMapStyle(style: StyleSpecification, urls: { tiles: string; glyphs: string }): StyleSpecification {
  const filled = replaceDeep(style, [
    ['{{TILES_URL}}', urls.tiles],
    ['{{GLYPHS_URL}}', urls.glyphs],
  ])
  return {
    ...filled,
    layers: filled.layers.map((layer) => {
      if (layer.type === 'symbol' && layer.layout?.['text-field'] !== undefined) {
        return { ...layer, layout: { ...layer.layout, 'text-field': ukrainianName() } }
      }
      if ('source-layer' in layer && layer['source-layer'] === 'boundary') {
        return { ...layer, layout: { ...layer.layout, visibility: 'none' as const } }
      }
      return layer
    }),
  } as StyleSpecification
}

/** Offline style with no tiles (e2e and tests): our GeoJSON layers draw on a plain background. */
export function blankStyle(): StyleSpecification {
  return {
    version: 8,
    sources: {},
    layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#f2efe9' } }],
  }
}
