import type { ComponentType } from 'react'
import type { DecisionId, DesignConfig } from '../core/design/types'

// Registry of IMPLEMENTED variants per decision (one per decision: the customer's selection), filled in by the UI
// tasks. Decisions that are core configuration (not components) never appear here.
export const CORE_CONFIG_DECISIONS: readonly DecisionId[] = ['map.aggregation', 'map.initial-view', 'audio.mobile-trigger']

export type VariantRegistry = Partial<Record<DecisionId, Record<string, ComponentType<any>>>>

export const VARIANTS: VariantRegistry = {}

const warned = new Set<string>()

/** Component for the configured variant of `id`; null when the decision has no registered component. */
export function resolveVariant<P>(id: DecisionId, design: DesignConfig): ComponentType<P> | null {
  const registered = VARIANTS[id]
  if (!registered) return null
  const wanted = design.variant(id)
  if (registered[wanted]) return registered[wanted]
  const names = Object.keys(registered)
  if (names.length !== 1) return null
  if (import.meta.env.DEV && !warned.has(id)) {
    warned.add(id)
    console.warn(`[design] ${id}: variant "${wanted}" is not implemented, using ${names[0]}`)
  }
  return registered[names[0]]
}
