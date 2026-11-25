import type { StyleSpecification } from 'maplibre-gl'
import { describe, expect, it } from 'vitest'
import { blankStyle, prepareMapStyle } from './mapStyle'

const urls = { tiles: 'https://tiles.test/{z}/{x}/{y}.pbf', glyphs: 'https://glyphs.test/{fontstack}/{range}.pbf' }

const fixture = (): StyleSpecification => ({
  version: 8,
  glyphs: '{{GLYPHS_URL}}',
  sources: { openmaptiles: { type: 'vector', tiles: ['{{TILES_URL}}'] } },
  layers: [
    { id: 'bg', type: 'background', paint: { 'background-color': '#fff' } },
    { id: 'country', type: 'symbol', source: 'openmaptiles', 'source-layer': 'place', layout: { 'text-field': '{name}' } },
    { id: 'city', type: 'symbol', source: 'openmaptiles', 'source-layer': 'place', layout: { 'text-field': ['get', 'name_en'] } },
    { id: 'poi', type: 'symbol', source: 'openmaptiles', 'source-layer': 'poi', layout: { 'icon-image': 'x' } },
    { id: 'border-country', type: 'line', source: 'openmaptiles', 'source-layer': 'boundary' },
    { id: 'border-state', type: 'line', source: 'openmaptiles', 'source-layer': 'boundary', layout: { visibility: 'visible' } },
  ],
})

describe('prepareMapStyle', () => {
  it('replaces {{TILES_URL}} and {{GLYPHS_URL}}', () => {
    const out = prepareMapStyle(fixture(), urls)
    expect(out.glyphs).toBe(urls.glyphs)
    expect((out.sources.openmaptiles as { tiles: string[] }).tiles).toEqual([urls.tiles])
  })

  it('every symbol text-field is coalesce(name:uk, name)', () => {
    const symbols = prepareMapStyle(fixture(), urls).layers.filter((l) => l.type === 'symbol')
    expect(symbols).toHaveLength(3)
    for (const l of symbols) {
      expect(l.layout?.['text-field']).toEqual(['coalesce', ['get', 'name:uk'], ['get', 'name']])
    }
  })

  it('hides every layer of source-layer boundary (borders come from our GeoJSON with Crimea)', () => {
    const out = prepareMapStyle(fixture(), urls)
    const borders = out.layers.filter((l) => 'source-layer' in l && l['source-layer'] === 'boundary')
    expect(borders).toHaveLength(2)
    for (const l of borders) expect(l.layout?.visibility).toBe('none')
    expect(out.layers.find((l) => l.id === 'bg')?.layout?.visibility).toBeUndefined()
  })

  it('is pure and idempotent', () => {
    const input = fixture()
    const snapshot = JSON.stringify(input)
    const once = prepareMapStyle(input, urls)
    expect(JSON.stringify(input)).toBe(snapshot)
    expect(prepareMapStyle(once, urls)).toEqual(once)
  })
})

describe('blankStyle', () => {
  it('is a valid style with a background and no remote sources', () => {
    const s = blankStyle()
    expect(s.version).toBe(8)
    expect(s.sources).toEqual({})
    expect(s.layers.map((l) => l.type)).toEqual(['background'])
  })
})
