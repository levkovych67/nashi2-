import { createContext, useContext } from 'react'
import { DEFAULT_VARIANTS, SUPPORTED_VARIANTS } from './defaults'
import type { DecisionId, DecisionsFile, DesignConfig } from './types'

export function loadDesignConfig(file: DecisionsFile | null): DesignConfig {
  const warned = new Set<string>()
  const approved = (id: DecisionId) => {
    const entry = file?.decisions?.[id]
    return entry?.status === 'APPROVED' ? entry : undefined
  }
  return {
    variant(id) {
      const entry = approved(id)
      if (!entry) return DEFAULT_VARIANTS[id]
      const supported = SUPPORTED_VARIANTS[id]
      if (supported && !supported.includes(entry.variant)) {
        if (!warned.has(id)) {
          warned.add(id)
          console.warn(`[design] ${id}: unsupported variant "${entry.variant}", using ${DEFAULT_VARIANTS[id]}`)
        }
        return DEFAULT_VARIANTS[id]
      }
      return entry.variant
    },
    param(id, name, fallback) {
      const value = approved(id)?.params?.[name]
      return typeof value === 'number' ? value : fallback
    },
  }
}

export const DesignContext = createContext<DesignConfig>(loadDesignConfig(null))

export function useDesign(): DesignConfig {
  return useContext(DesignContext)
}
