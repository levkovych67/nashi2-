import { describe, expect, it } from 'vitest'
import type { components } from '../../api/types.gen'
import { activeFilterCount, applyFilters, parseFilterParams, writeFilterParams } from './filterState'

type Def = components['schemas']['FilterDefinition']
type Pin = components['schemas']['MapPin']

const defs: Def[] = [
  {
    key: 'region',
    label: 'Область',
    type: 'MULTI_SELECT',
    options: ['LVIV', 'KYIV_CITY', 'ODESA'].map((value) => ({ value, label: value, count: 1 })),
  },
]

const pin = (id: string, region: Pin['region'], kind: Pin['kind'] = 'PERSON'): Pin => ({
  id, slug: id, name: id, kind, lat: 50, lon: 30, region, placeId: 'p', hasAudio: false,
})

describe('filterState', () => {
  it('parses region=LVIV,KYIV_CITY', () => {
    expect(parseFilterParams(new URLSearchParams('region=LVIV,KYIV_CITY'), defs)).toEqual({ region: ['LVIV', 'KYIV_CITY'] })
  })

  it('drops unknown keys and unknown values', () => {
    expect(parseFilterParams(new URLSearchParams('region=MOSCOW&genre=x'), defs)).toEqual({})
    expect(parseFilterParams(new URLSearchParams('region=MOSCOW,LVIV,LVIV'), defs)).toEqual({ region: ['LVIV'] })
  })

  it('writes comma-joined and removes empty keys', () => {
    const sp = new URLSearchParams('region=ODESA&other=1')
    expect(writeFilterParams(sp, { region: ['LVIV', 'ODESA'] }).toString()).toBe('region=LVIV%2CODESA&other=1')
    expect(writeFilterParams(sp, { region: [] }).toString()).toBe('other=1')
    expect(sp.toString()).toBe('region=ODESA&other=1') // input is not mutated
  })

  it('applyFilters ORs values within a key and ANDs keys', () => {
    const pins = [pin('a', 'LVIV'), pin('b', 'ODESA'), pin('c', 'LVIV', 'BAND')]
    expect(applyFilters(pins, {}).map((p) => p.id)).toEqual(['a', 'b', 'c'])
    expect(applyFilters(pins, { region: ['LVIV', 'ODESA'] }).map((p) => p.id)).toEqual(['a', 'b', 'c'])
    expect(applyFilters(pins, { region: ['LVIV', 'ODESA'], kind: ['BAND'] }).map((p) => p.id)).toEqual(['c'])
    expect(applyFilters(pins, { region: ['KYIV'] })).toEqual([])
  })

  it('counts active filters by selected values', () => {
    expect(activeFilterCount({})).toBe(0)
    expect(activeFilterCount({ region: ['LVIV', 'ODESA'], kind: [] })).toBe(2)
  })
})
