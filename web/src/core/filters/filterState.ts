import type { components } from '../../api/types.gen'

type MapPin = components['schemas']['MapPin']
type FilterDefinition = components['schemas']['FilterDefinition']

/** Selected option values per filter key; a key with no values is simply absent. */
export type FilterState = Record<string, string[]>

/** Reads `?key=a,b` for every known filter; unknown keys and values are dropped. */
export function parseFilterParams(sp: URLSearchParams, defs: FilterDefinition[]): FilterState {
  const state: FilterState = {}
  for (const def of defs) {
    const raw = sp.get(def.key)
    if (!raw) continue
    const known = new Set(def.options.map((o) => o.value))
    const values = [...new Set(raw.split(',').filter((v) => known.has(v)))]
    if (values.length > 0) state[def.key] = values
  }
  return state
}

/** Returns a copy of `sp` with every key of `state` written comma-joined, or removed when it has no values. */
export function writeFilterParams(sp: URLSearchParams, state: FilterState): URLSearchParams {
  const next = new URLSearchParams(sp)
  for (const [key, values] of Object.entries(state)) {
    if (values.length > 0) next.set(key, values.join(','))
    else next.delete(key)
  }
  return next
}

/** OR within a key, AND across keys; the key names the pin field it matches. */
export function applyFilters(pins: MapPin[], state: FilterState): MapPin[] {
  const active = Object.entries(state).filter(([, values]) => values.length > 0)
  if (active.length === 0) return pins
  return pins.filter((pin) =>
    active.every(([key, values]) => values.includes(String((pin as unknown as Record<string, unknown>)[key]))),
  )
}

export function activeFilterCount(state: FilterState): number {
  return Object.values(state).reduce((sum, values) => sum + values.length, 0)
}
