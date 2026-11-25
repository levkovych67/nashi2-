import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadDesignConfig, withDecisionOverrides } from './config'

afterEach(() => vi.restoreAllMocks())

describe('design config', () => {
  it('uses defaults from 01-decisions when decisions are empty', () => {
    const c = loadDesignConfig(null)
    expect(c.variant('audio.mobile-trigger')).toBe('TAP_PREVIEW_AND_PLAY')
    expect(c.variant('map.aggregation')).toBe('REGION_PLACE_PIN')
    expect(loadDesignConfig({ schemaVersion: 1, decisions: {} }).variant('map.pin')).toBe('AVATAR_RING')
  })

  it('ignores PROPOSED decisions and applies APPROVED ones with params', () => {
    const c = loadDesignConfig({
      schemaVersion: 1,
      decisions: {
        'map.pin': { variant: 'DOT_LABEL', status: 'PROPOSED' },
        'audio.mobile-trigger': { variant: 'LONG_PRESS_PLAY', status: 'APPROVED', params: { longPressMs: 400 } },
      },
    })
    expect(c.variant('map.pin')).toBe('AVATAR_RING')
    expect(c.variant('audio.mobile-trigger')).toBe('LONG_PRESS_PLAY')
    expect(c.param('audio.mobile-trigger', 'longPressMs', 500)).toBe(400)
    expect(c.param('audio.mobile-trigger', 'other', 7)).toBe(7)
    expect(c.param('map.pin', 'size', 9)).toBe(9)
  })

  it('unknown variant id for a technical decision falls back to default and warns once', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const c = loadDesignConfig({
      schemaVersion: 1,
      decisions: { 'map.pin': { variant: 'NOPE', status: 'APPROVED' } },
    })
    expect(c.variant('map.pin')).toBe('AVATAR_RING')
    expect(c.variant('map.pin')).toBe('AVATAR_RING')
    expect(warn).toHaveBeenCalledTimes(1)
  })

  it('withDecisionOverrides puts test decisions over the handoff ones and leaves the file alone', () => {
    const file = {
      schemaVersion: 1,
      decisions: {
        'map.aggregation': { variant: 'CHOROPLETH_PLACE_PIN', status: 'PROPOSED' as const },
        'map.pin': { variant: 'AVATAR_RING', status: 'PROPOSED' as const },
      },
    }
    const merged = withDecisionOverrides(file, { 'map.aggregation': { variant: 'CHOROPLETH_PLACE_PIN', status: 'APPROVED' } })
    expect(loadDesignConfig(merged).variant('map.aggregation')).toBe('CHOROPLETH_PLACE_PIN')
    expect(merged.decisions['map.pin']).toEqual(file.decisions['map.pin'])
    expect(file.decisions['map.aggregation'].status).toBe('PROPOSED')
    expect(withDecisionOverrides(file, undefined)).toBe(file)
  })
})
