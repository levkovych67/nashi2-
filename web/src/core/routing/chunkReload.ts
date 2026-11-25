// A deploy replaces the hashed chunks; a tab opened before it then fails to load a lazy route. One reload fetches the
// new index.html and its chunks. The time of that reload is kept so a chunk that is really missing cannot loop.

const KEY = 'nashi.chunkReloadAt'
const LOOP_WINDOW_MS = 60_000

const PATTERNS = [
  /Failed to fetch dynamically imported module/i, // Chromium
  /error loading dynamically imported module/i, // Firefox
  /Importing a module script failed/i, // Safari
  /Unable to preload CSS/i, // Vite preload helper
  /Loading (CSS )?chunk [\w-]+ failed/i,
]

export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : typeof error === 'string' ? error : ''
  return PATTERNS.some((p) => p.test(message))
}

type Store = Pick<Storage, 'getItem' | 'setItem'>

function session(): Store | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

/** True when this tab has not reloaded for a missing chunk within the last minute (and can remember that it did). */
export function canReloadForChunk(store: Store | null = session(), now = Date.now()): boolean {
  if (!store) return false
  try {
    return now - Number(store.getItem(KEY) ?? 0) > LOOP_WINDOW_MS
  } catch {
    return false
  }
}

export function markChunkReload(store: Store | null = session(), now = Date.now()) {
  try {
    store?.setItem(KEY, String(now))
  } catch {
    // canReloadForChunk already said no in that case
  }
}
