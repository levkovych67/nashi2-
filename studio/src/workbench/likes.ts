type Result =
  | { ok: true; likesCount: number }
  | { ok: false; retryAfterSec?: number }
type Send = (count: number, options: { keepalive: boolean }) => Promise<Result>
export function createLikeBatcher(initial: number, send: Send) {
  let value = initial,
    confirmed = initial,
    pending = 0,
    inflight = 0,
    retryAt = 0
  let timer: ReturnType<typeof setTimeout> | undefined
  let keepaliveRequested = false
  const listeners = new Set<() => void>()
  function schedule(delay: number) {
    clearTimeout(timer)
    timer = setTimeout(() => {
      void flush()
    }, delay)
  }
  async function flush(keepalive = false) {
    if (keepalive && pending) keepaliveRequested = true
    if (!pending || inflight) return
    if (retryAt > Date.now()) {
      schedule(retryAt - Date.now())
      return
    }
    clearTimeout(timer)
    keepalive = keepaliveRequested
    inflight = Math.min(50, pending)
    pending -= inflight
    let result: Result
    try {
      result = await send(inflight, { keepalive })
    } catch {
      result = { ok: false }
    }
    if (result.ok) confirmed = Math.max(confirmed, result.likesCount)
    else {
      pending += inflight
      retryAt = Date.now() + Math.max(1, result.retryAfterSec || 1) * 1000
    }
    inflight = 0
    value = Math.max(value, confirmed + pending)
    listeners.forEach((fn) => fn())
    if (!pending) keepaliveRequested = false
    if (pending >= 50 || (keepaliveRequested && pending))
      void flush(keepaliveRequested)
    else if (pending)
      schedule(retryAt > Date.now() ? retryAt - Date.now() : 600)
  }
  return {
    get: () => value,
    subscribe(fn: () => void) {
      listeners.add(fn)
      return () => {
        listeners.delete(fn)
      }
    },
    tap() {
      pending++
      value++
      listeners.forEach((fn) => fn())
      if (pending >= 50) void flush()
      else schedule(Math.max(600, retryAt - Date.now()))
    },
    flush,
  }
}
const counts = new Map<string, number>()
const batches = new Map<string, ReturnType<typeof createLikeBatcher>>()
const retryOnce = new Set<string>()
export function simulateLikeLimit(id: string, enabled: boolean) {
  if (enabled) retryOnce.add(id)
  else retryOnce.delete(id)
}
export function likesFor(id: string, initial: number) {
  const previous = batches.get(id)
  if (previous) return previous
  const batch = createLikeBatcher(initial, async (count, { keepalive }) => {
    // This is an in-memory fixture server. No user reaction leaves the studio.
    const limited = retryOnce.delete(id)
    const debug = window as Window & {
      __likeRequests?: {
        id: string
        count: number
        keepalive: boolean
        status: number
      }[]
    }
    ;(debug.__likeRequests ??= []).push({
      id,
      count,
      keepalive,
      status: limited ? 429 : 200,
    })
    if (limited) return { ok: false, retryAfterSec: 2 }
    const likesCount = (counts.get(id) ?? initial) + count
    counts.set(id, likesCount)
    return { ok: true, likesCount }
  })
  batches.set(id, batch)
  return batch
}
