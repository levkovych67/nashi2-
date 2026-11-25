import type { ComponentType } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import decisions from '../design/decisions.json'
import { loadDesignConfig } from '../core/design/config'
import type { DecisionId, DecisionsFile } from '../core/design/types'
import { CORE_CONFIG_DECISIONS, VARIANTS, resolveVariant } from './variants'

const FREE_ID: DecisionId[] = ['identity', 'map.basemap']

const A: ComponentType = () => null
const B: ComponentType = () => null

function withRegistry(id: DecisionId, entries: Record<string, ComponentType>, fn: () => void) {
  const reg = VARIANTS as Record<string, Record<string, ComponentType>>
  reg[id] = entries
  try {
    fn()
  } finally {
    delete reg[id]
  }
}

function design(id: DecisionId, variant: string, status: 'APPROVED' | 'PROPOSED' = 'APPROVED') {
  return loadDesignConfig({ schemaVersion: 1, decisions: { [id]: { variant, status } } })
}

afterEach(() => vi.restoreAllMocks())

describe('variant registry', () => {
  it('every APPROVED variant in src/design/decisions.json is registered, core config or a free identity/basemap id', () => {
    const file = decisions as DecisionsFile
    for (const [id, entry] of Object.entries(file.decisions)) {
      if (entry.status !== 'APPROVED') continue
      if (FREE_ID.includes(id as DecisionId) || CORE_CONFIG_DECISIONS.includes(id as DecisionId)) continue
      expect(VARIANTS[id as DecisionId]?.[entry.variant], `${id} = ${entry.variant}`).toBeDefined()
    }
  })

  it('core config decisions are never in the registry', () => {
    for (const id of CORE_CONFIG_DECISIONS) expect(VARIANTS[id]).toBeUndefined()
  })

  it('resolveVariant returns the component of the configured variant', () => {
    withRegistry('map.pin', { AVATAR_RING: A, DOT_LABEL: B }, () => {
      expect(resolveVariant('map.pin', design('map.pin', 'DOT_LABEL'))).toBe(B)
      // PROPOSED is ignored by the config, so the default variant is used
      expect(resolveVariant('map.pin', design('map.pin', 'DOT_LABEL', 'PROPOSED'))).toBe(A)
    })
  })

  it('falls back to the only registered variant and warns once', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    withRegistry('map.cluster', { BUBBLE_COUNT_LABEL: B }, () => {
      const d = design('map.cluster', 'BUBBLE_COUNT')
      expect(resolveVariant('map.cluster', d)).toBe(B)
      expect(resolveVariant('map.cluster', d)).toBe(B)
    })
    expect(warn).toHaveBeenCalledTimes(1)
  })

  it('returns null when nothing is registered, and when several are but the chosen one is not', () => {
    expect(resolveVariant('submission.form', design('submission.form', 'WIZARD'))).toBeNull()
    withRegistry('audio.player', { MINI_BAR: A, IN_CARD: B }, () => {
      const d = loadDesignConfig({ schemaVersion: 1, decisions: {} })
      expect(resolveVariant('audio.player', design('audio.player', 'MINI_BAR'))).toBe(A)
      // default is MINI_BAR, remove it to leave two others unrelated to the choice
      delete (VARIANTS as Record<string, Record<string, ComponentType>>)['audio.player'].MINI_BAR
      ;(VARIANTS as Record<string, Record<string, ComponentType>>)['audio.player'].X = A
      expect(resolveVariant('audio.player', d)).toBeNull()
    })
  })
})
