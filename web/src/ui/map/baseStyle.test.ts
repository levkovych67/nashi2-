import { describe, expect, it } from 'vitest'
import { baseMapStyle, OVERLAY_LAYER_IDS } from './baseStyle'

describe('baseMapStyle', () => {
  it('VITE_MAP_STYLE=blank gives the offline style', () => {
    const style = baseMapStyle({ VITE_MAP_STYLE: 'blank' })
    expect(style.sources).toEqual({})
    expect(style.layers.map((l) => l.id)).toEqual(['background'])
  })

  it('fills tile and glyph URLs, defaulting to OpenFreeMap', () => {
    const json = JSON.stringify(baseMapStyle({}))
    expect(json).toContain('https://tiles.openfreemap.org/planet')
    expect(json).toContain('https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf')
    expect(json).not.toContain('{{')
    const custom = JSON.stringify(baseMapStyle({ VITE_MAP_TILES_URL: 'https://t.example/tiles', VITE_MAP_GLYPHS_URL: 'https://g.example/{fontstack}/{range}.pbf' }))
    expect(custom).toContain('https://t.example/tiles')
    expect(custom).toContain('https://g.example/{fontstack}/{range}.pbf')
  })

  it('leaves the Ukraine outline, region lines and region fill to MapOverlays', () => {
    const style = baseMapStyle({})
    const ids = style.layers.map((l) => l.id)
    for (const id of OVERLAY_LAYER_IDS) expect(ids).not.toContain(id)
    expect(ids).toContain('territory')
    // no layer left draws from the outline source, so it is not loaded at all
    expect(style.sources.outline).toBeUndefined()
  })
})
