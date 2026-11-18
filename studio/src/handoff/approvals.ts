import { useMemo, useSyncExternalStore } from 'react'
import { decisionsFile, type Draft } from '../selection/selection'
import { reviewDecisions, sameChoice, type DecisionEntry } from './review'

const key = 'nashi-studio:review:v1'
let memory: string | null = null
let memoryOnly = false
const listeners = new Set<() => void>()
function snapshot() {
  if (memoryOnly) return memory
  try {
    return localStorage.getItem(key)
  } catch {
    return memory
  }
}
function saved(raw: string | null) {
  try {
    const checked = reviewDecisions({
      schemaVersion: 1,
      decisions: JSON.parse(raw || '{}'),
    })
    if (checked.errors.length) return {}
    return Object.fromEntries(
      Object.entries(checked.file.decisions).filter(
        ([, entry]) => entry.status === 'APPROVED',
      ),
    )
  } catch {
    return {}
  }
}
function subscribe(listener: () => void) {
  listeners.add(listener)
  const sync = (event: StorageEvent) => {
    if (event.key === key || event.key === null) listener()
  }
  window.addEventListener('storage', sync)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', sync)
  }
}
export function saveApprovals(entries: Record<string, DecisionEntry>) {
  const raw = JSON.stringify({ ...saved(snapshot()), ...entries })
  memory = raw
  try {
    localStorage.setItem(key, raw)
    memoryOnly = false
  } catch {
    memoryOnly = true
  }
  listeners.forEach((listener) => listener())
  return !memoryOnly
}
export function effectiveReviewed(
  draft: Draft,
  raw: string | null = snapshot(),
) {
  const result = reviewDecisions(decisionsFile(draft))
  const local = saved(raw)
  for (const [id, entry] of Object.entries(result.file.decisions))
    if (
      entry.status !== 'APPROVED' &&
      local[id] &&
      sameChoice(entry, local[id])
    )
      result.file.decisions[id] = local[id]
  result.pending = Object.keys(result.file.decisions).filter(
    (id) => result.file.decisions[id].status !== 'APPROVED',
  )
  return result
}
export function useReviewed(draft: Draft) {
  const raw = useSyncExternalStore(subscribe, snapshot, () => null)
  return useMemo(() => effectiveReviewed(draft, raw), [draft, raw])
}
