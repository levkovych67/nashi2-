// Where the map was in each history entry, so Back from a musician page (or a reload) returns to the same camera
// and preview. Kept in sessionStorage by React Router's location key: the router rewrites history.state on its own
// navigations, so a field there would be lost. "default" is the key of every new document's first entry, so it is
// never used (the map gives that entry its own key first).

export interface MapMemory {
  center: [lon: number, lat: number]
  zoom: number
  /** Musician whose preview was open, or null. */
  previewId: string | null
}

const STORAGE_KEY = 'nashi.mapMemory'
export const MAP_MEMORY_LIMIT = 30

type Store = Pick<Storage, 'getItem' | 'setItem'>

function session(): Store | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

const isNumber = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v)

function valid(m: unknown): m is MapMemory {
  const v = m as MapMemory | null
  return (
    typeof v === 'object' && v !== null &&
    Array.isArray(v.center) && v.center.length === 2 && v.center.every(isNumber) &&
    isNumber(v.zoom) &&
    (v.previewId === null || typeof v.previewId === 'string')
  )
}

/** Entries oldest first; an array, because object keys that look like numbers would lose their order. */
function readAll(store: Store): Array<[string, unknown]> {
  try {
    const all: unknown = JSON.parse(store.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(all) ? all.filter((e): e is [string, unknown] => Array.isArray(e) && typeof e[0] === 'string') : []
  } catch {
    return []
  }
}

export function readMapMemory(entryKey: string, store: Store | null = session()): MapMemory | null {
  if (!store || entryKey === 'default') return null
  const found = readAll(store).find(([k]) => k === entryKey)?.[1]
  return valid(found) ? { center: [found.center[0], found.center[1]], zoom: found.zoom, previewId: found.previewId } : null
}

export function saveMapMemory(entryKey: string, memory: MapMemory, store: Store | null = session()) {
  if (!store || entryKey === 'default') return
  const all = readAll(store).filter(([k]) => k !== entryKey)
  all.push([entryKey, memory])
  try {
    store.setItem(STORAGE_KEY, JSON.stringify(all.slice(-MAP_MEMORY_LIMIT)))
  } catch {
    // storage full or blocked: Back then opens the overview
  }
}
