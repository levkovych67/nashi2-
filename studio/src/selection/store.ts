import { known } from '../decisions/catalog'
import { recordedDecisions } from './selection'
import { useSyncExternalStore } from 'react'
import {
  decodeSelection,
  encodeSelection,
  STORAGE_KEY,
  type Draft,
} from './selection'

let draft: Draft = {}
let warning = ''
const listeners = new Set<() => void>()
function saved() {
  try {
    return decodeSelection(localStorage.getItem(STORAGE_KEY)).draft
  } catch {
    return {}
  }
}
function write(next: Draft) {
  draft = next
  try {
    localStorage.setItem(STORAGE_KEY, encodeSelection(next))
  } catch {
    /* Private browsing keeps the draft in memory. */
  }
  listeners.forEach((fn) => fn())
}
if (typeof window !== 'undefined') {
  draft = saved()
  const url = new URL(window.location.href)
  if (url.searchParams.has('sel')) {
    const { draft: shared, rejected } = decodeSelection(
      url.searchParams.get('sel'),
    )
    if (rejected.length) warning = 'Частину вибору з посилання не розпізнано'
    if (Object.keys(shared).length) write({ ...draft, ...shared })
    url.searchParams.delete('sel')
    history.replaceState(history.state, '', url)
  }
  addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY && event.key !== null) return
    draft = saved()
    listeners.forEach((fn) => fn())
  })
}
function subscribe(fn: () => void) {
  listeners.add(fn)
  return () => {
    listeners.delete(fn)
  }
}
export function useDraft() {
  return useSyncExternalStore(subscribe, () => draft)
}
export function choose(
  id: string,
  variant: string,
  params?: Draft[string]['params'],
) {
  if (!known(id, variant)) return
  const current = draft[id] ?? recordedDecisions[id]
  const nextParams =
    params ?? (current?.variant === variant ? current.params : undefined)
  write({
    ...draft,
    [id]: nextParams ? { variant, params: nextParams } : { variant },
  })
}
export function reset() {
  write({})
}
export const importWarning = () => warning
