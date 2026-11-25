import { describe, expect, it } from 'vitest'
import { loadGeo, regionAt } from './geo'

describe('geo', () => {
  it('loads 27 regions and one outline polygon once', async () => {
    const geo = await loadGeo()
    expect(geo.regions.features).toHaveLength(27)
    expect(geo.outline.features).toHaveLength(1)
    expect(await loadGeo()).toBe(geo)
  })

  it('regionAt finds the region of a point', async () => {
    expect(await regionAt(24.03, 49.84)).toBe('LVIV')
    expect(await regionAt(30.52, 50.45)).toBe('KYIV_CITY')
    expect(await regionAt(34.1, 44.95)).toBe('CRIMEA')
    expect(await regionAt(30.72, 46.48)).toBe('ODESA')
  })

  it('regionAt returns null outside Ukraine', async () => {
    expect(await regionAt(37.6, 55.75)).toBeNull()
  })
})
