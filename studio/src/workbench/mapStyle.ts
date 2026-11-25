import type { FlatTokens } from '../tokens/tokens'
import type { LayerSpecification, StyleSpecification } from 'maplibre-gl'

const palettes = {
  'light-minimal': {
    base: '#f4f7f5',
    land: '#e6ede5',
    park: '#d4e3d3',
    water: '#c8dfeb',
    road: '#c8c9be',
    label: '#48635b',
    border: '#80988a',
    outline: '#426d5c',
  },
  night: {
    base: '#151d2a',
    land: '#253347',
    park: '#2d413e',
    water: '#102536',
    road: '#675744',
    label: '#c6cbd4',
    border: '#60718b',
    outline: '#c7a976',
  },
  'atlas-paper': {
    base: '#f1eada',
    land: '#f6edd8',
    park: '#d9dfc5',
    water: '#bad3d8',
    road: '#bd9b71',
    label: '#605440',
    border: '#a7987b',
    outline: '#756044',
  },
}
export function basemapPalette(id: string, tokens?: FlatTokens) {
  const p = palettes[id as keyof typeof palettes] || palettes['light-minimal']
  if (!tokens) return p
  // Keep the basemap's lightness and mix the identity's inks into its cartography.
  const ink = (key: string, original: string, strength: number) => {
    const c = tokens['color.map.' + key]?.value
    if (typeof c !== 'string' || !/^#[0-9a-f]{6}$/i.test(c)) return original
    return (
      '#' +
      [1, 3, 5]
        .map((i) =>
          Math.round(
            parseInt(original.slice(i, i + 2), 16) * (1 - strength) +
              parseInt(c.slice(i, i + 2), 16) * strength,
          )
            .toString(16)
            .padStart(2, '0'),
        )
        .join('')
    )
  }
  return {
    ...p,
    land: ink('land', p.land, 0.16),
    water: ink('water', p.water, 0.18),
    road: ink('road', p.road, 0.3),
    label: ink('label', p.label, 0.25),
    border: ink('region-line', p.border, 0.3),
    outline: ink('ua-border', p.outline, 0.45),
  }
}

export function studyMapStyle(
  id: string,
  tileUrl: string | null = 'https://tiles.openfreemap.org/planet',
  glyphUrl = 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
  tokens?: FlatTokens,
): StyleSpecification {
  const p = basemapPalette(id, tokens)
  const layers: LayerSpecification[] = [
    {
      id: 'background',
      type: 'background',
      paint: { 'background-color': p.base },
    },
    {
      id: 'territory',
      type: 'fill',
      source: 'regions',
      paint: { 'fill-color': p.land },
    },
  ]
  const sources: StyleSpecification['sources'] = {
    regions: { type: 'geojson', data: '/geo/regions.geojson' },
    outline: { type: 'geojson', data: '/geo/ukraine-outline.geojson' },
  }
  if (tileUrl) {
    sources.openmaptiles = {
      type: 'vector',
      url: tileUrl,
      attribution: '© OpenStreetMap · OpenMapTiles · OpenFreeMap',
    }
    layers.push(
      {
        id: 'parks',
        type: 'fill',
        source: 'openmaptiles',
        'source-layer': 'landcover',
        paint: { 'fill-color': p.park, 'fill-opacity': 0.65 },
      },
      {
        id: 'water',
        type: 'fill',
        source: 'openmaptiles',
        'source-layer': 'water',
        paint: { 'fill-color': p.water },
      },
      {
        id: 'rivers',
        type: 'line',
        minzoom: 6,
        source: 'openmaptiles',
        'source-layer': 'waterway',
        paint: { 'line-color': p.water, 'line-width': 1.4 },
      },
      {
        id: 'roads',
        type: 'line',
        source: 'openmaptiles',
        'source-layer': 'transportation',
        minzoom: id === 'light-minimal' ? 8 : 5,
        filter: [
          'any',
          ['>=', ['zoom'], 11],
          [
            'in',
            ['get', 'class'],
            ['literal', ['motorway', 'trunk', 'primary']],
          ],
        ],
        paint: {
          'line-color': p.road,
          'line-width': [
            'interpolate',
            ['linear'],
            ['zoom'],
            5,
            0.5,
            12,
            1.6,
            17,
            5,
          ],
        },
      },
      {
        id: 'place-names',
        type: 'symbol',
        source: 'openmaptiles',
        'source-layer': 'place',
        minzoom: id === 'light-minimal' ? 7 : 5,
        layout: {
          'text-field': ['coalesce', ['get', 'name:uk'], ['get', 'name']],
          'text-font': ['Noto Sans Regular'],
          'text-size': ['interpolate', ['linear'], ['zoom'], 5, 10, 12, 14],
        },
        paint: {
          'text-color': p.label,
          'text-halo-color': p.land,
          'text-halo-width': 1.4,
        },
      },
    )
  }
  layers.push(
    {
      id: 'region-color',
      type: 'fill',
      source: 'regions',
      paint: {
        'fill-color': String(
          tokens?.['color.map.region-fill-active']?.value || p.outline,
        ),
        'fill-opacity': 0,
      },
    },
    {
      id: 'region-line',
      type: 'line',
      source: 'regions',
      paint: { 'line-color': p.border, 'line-width': 1 },
    },
    {
      id: 'ua-outline-line',
      type: 'line',
      source: 'outline',
      paint: { 'line-color': p.outline, 'line-width': 1.8 },
    },
  )
  return {
    version: 8,
    name: id,
    ...(tileUrl ? { glyphs: glyphUrl } : {}),
    sources,
    layers,
  }
}
