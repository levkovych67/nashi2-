import { byId, known } from '../decisions/catalog'
import file from '../../decisions/decisions.json'
interface Entry {
  params?: Parameters
  variant: string
  status: 'PROPOSED' | 'APPROVED'
  approvedAt?: string
  approvedBy?: string
  note?: string
}
export const recordedDecisions = file.decisions as Record<string, Entry>
type Parameters = Record<string, string | number | boolean>
export type Draft = Record<string, { variant: string; params?: Parameters }>
export const STORAGE_KEY = 'nashi-studio:draft:v1'

export function encodeSelection(s: Draft) {
  const bytes = new TextEncoder().encode(JSON.stringify({ v: 1, s }))
  return btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '')
}
export function decodeSelection(value: string | null): {
  draft: Draft
  rejected: string[]
} {
  const draft: Draft = {}
  const rejected: string[] = []
  if (value === null) return { draft, rejected }
  if (value.length > 4000) return { draft, rejected: ['sel:too-long'] }
  try {
    const bytes = Uint8Array.from(
      atob(value.replaceAll('-', '+').replaceAll('_', '/')),
      (c) => c.charCodeAt(0),
    )
    const parsed = JSON.parse(new TextDecoder().decode(bytes))
    if (parsed.v !== 1) return { draft, rejected: ['sel:version'] }
    if (!parsed.s || typeof parsed.s !== 'object' || Array.isArray(parsed.s))
      return { draft, rejected: ['sel'] }
    for (const [key, raw] of Object.entries(parsed.s)) {
      const variant = (raw as { variant?: unknown } | null)?.variant
      if (typeof variant === 'string' && known(key, variant)) {
        const params = (raw as { params?: unknown }).params
        if (params === undefined) draft[key] = { variant }
        else if (
          params &&
          typeof params === 'object' &&
          !Array.isArray(params) &&
          Object.values(params).every(
            (p) =>
              typeof p === 'string' ||
              typeof p === 'boolean' ||
              (typeof p === 'number' && Number.isFinite(p)),
          )
        ) {
          draft[key] = { variant, params: params as Parameters }
        } else rejected.push(key)
      } else rejected.push(byId(key) ? key + '=' + String(variant) : key)
    }
    return { draft, rejected }
  } catch {
    return { draft, rejected: ['sel'] }
  }
}

export function decisionsFile(draft: Draft) {
  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    studioCommit: import.meta.env?.VITE_STUDIO_COMMIT ?? '',
    decisions: {
      ...recordedDecisions,
      ...Object.fromEntries(
        Object.entries(draft).map(([key, entry]) => [
          key,
          entry.variant === recordedDecisions[key]?.variant &&
          JSON.stringify(entry.params ?? {}) ===
            JSON.stringify(recordedDecisions[key]?.params ?? {})
            ? recordedDecisions[key]
            : { ...entry, status: 'PROPOSED' },
        ]),
      ),
    },
  }
}
