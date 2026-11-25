import { describe, expect, it } from 'vitest'
import { fillOpacityExpression, regionFillOpacity } from './fill'

describe('regionFillOpacity', () => {
  const counts = { LVIV: 484, KYIV_CITY: 603, CRIMEA: 54 }

  it('without choropleth only selected regions are filled', () => {
    expect(regionFillOpacity(counts, ['LVIV'], false)).toEqual({ LVIV: 0.35 })
    expect(regionFillOpacity(counts, [], false)).toEqual({})
  })

  it('choropleth shades every region with musicians by its rank: fewest 0.15, most 0.6', () => {
    expect(regionFillOpacity(counts, [], true)).toEqual({ CRIMEA: 0.15, LVIV: 0.375, KYIV_CITY: 0.6 })
  })

  it('choropleth keeps the selected region emphasised and skips empty regions', () => {
    const out = regionFillOpacity({ ...counts, ODESA: 0 }, ['CRIMEA'], true)
    expect(out.CRIMEA).toBe(0.35)
    expect(out.ODESA).toBeUndefined()
  })

  it('a single region is shaded at the lowest step', () => {
    expect(regionFillOpacity({ LVIV: 5 }, [], true)).toEqual({ LVIV: 0.15 })
  })
})

describe('fillOpacityExpression', () => {
  it('looks the opacity up by the feature region code, 0 when missing', () => {
    expect(fillOpacityExpression({ LVIV: 0.75 })).toEqual([
      'to-number',
      ['get', ['to-string', ['get', 'code']], ['literal', { LVIV: 0.75 }]],
      0,
    ])
  })
})
