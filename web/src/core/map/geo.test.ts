import { describe, expect, it } from 'vitest'
import { regionAt, regionsGeoJson, ukraineOutline } from './geo'

describe('geo', () => {
  it('has 27 regions and one outline polygon', () => {
    expect(regionsGeoJson.features).toHaveLength(27)
    expect(ukraineOutline.features).toHaveLength(1)
  })

  it('regionAt finds the region of a point', () => {
    expect(regionAt(24.03, 49.84)).toBe('LVIV')
    expect(regionAt(30.52, 50.45)).toBe('KYIV_CITY')
    expect(regionAt(34.1, 44.95)).toBe('CRIMEA')
    expect(regionAt(30.72, 46.48)).toBe('ODESA')
  })

  it('regionAt returns null outside Ukraine', () => {
    expect(regionAt(37.6, 55.75)).toBeNull()
  })
})
