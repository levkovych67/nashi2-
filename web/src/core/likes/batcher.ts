import type { Clock } from '../audio/engine'

export type SendResult = { ok: true; likesCount: number } | { ok: false; retryAfterMs: number }

export interface LikeBatcherDeps {
  send(id: string, count: number, opts: { keepalive: boolean }): Promise<SendResult>
  clock: Clock
}

export interface LikeBatcher {
  tap(id: string): void
  /** Taps not yet confirmed by the server: queued plus in flight (so the UI count never dips mid-request). */
  pending(id: string): number
  /** Last count the server confirmed for `id` (0 until the first response); survives hook unmounts. */
  confirmed(id: string): number
  flushAll(opts: { keepalive: boolean }): void
  onServerCount(l: (id: string, likesCount: number) => void): () => void
}

export const ERROR_RETRY_MS = 2000
export const MAX_ERROR_RETRIES = 5

interface Slot {
  queued: number
  inFlight: number
  busy: boolean
  timer: number | null
  blockedUntil: number
  failures: number
}

export function createLikeBatcher(deps: LikeBatcherDeps, cfg: { flushMs: number; max: number }): LikeBatcher {
  const { send, clock } = deps
  const slots = new Map<string, Slot>()
  const confirmedCounts = new Map<string, number>()
  const listeners = new Set<(id: string, likesCount: number) => void>()

  const slot = (id: string): Slot => {
    let s = slots.get(id)
    if (!s) slots.set(id, (s = { queued: 0, inFlight: 0, busy: false, timer: null, blockedUntil: 0, failures: 0 }))
    return s
  }
  const emit = (id: string, n: number) => listeners.forEach((l) => l(id, n))

  /** (Re)start the id's timer; never earlier than a rate-limit / error back-off. */
  function schedule(id: string, s: Slot, delay: number) {
    if (s.timer !== null) clock.clearTimeout(s.timer)
    const wait = Math.max(delay, s.blockedUntil - clock.now())
    s.timer = clock.setTimeout(() => {
      s.timer = null
      void flush(id)
    }, wait)
  }

  function failed(id: string, s: Slot, n: number, r: SendResult | 'error') {
    s.queued += n
    if (r === 'error') {
      s.failures++
      if (s.failures > MAX_ERROR_RETRIES) return // give up until the next tap
      s.blockedUntil = clock.now() + ERROR_RETRY_MS
      schedule(id, s, ERROR_RETRY_MS)
    } else if (!r.ok) {
      s.blockedUntil = clock.now() + r.retryAfterMs
      schedule(id, s, r.retryAfterMs)
    }
  }

  function succeeded(id: string, s: Slot, likesCount: number) {
    s.failures = 0
    s.blockedUntil = 0
    confirmedCounts.set(id, Math.max(confirmedCounts.get(id) ?? 0, likesCount))
    emit(id, likesCount)
  }

  async function flush(id: string): Promise<void> {
    const s = slot(id)
    if (s.busy || s.queued === 0 || s.blockedUntil > clock.now()) return
    if (s.timer !== null) {
      clock.clearTimeout(s.timer)
      s.timer = null
    }
    const n = Math.min(s.queued, cfg.max)
    s.queued -= n
    s.inFlight += n
    s.busy = true
    let r: SendResult | 'error'
    try {
      r = await send(id, n, { keepalive: false })
    } catch {
      r = 'error'
    }
    s.busy = false
    s.inFlight -= n
    if (r !== 'error' && r.ok) {
      succeeded(id, s, r.likesCount)
      // A timer started by later taps keeps its own schedule; otherwise continue right away.
      if (s.queued >= cfg.max || (s.queued > 0 && s.timer === null)) void flush(id)
    } else {
      failed(id, s, n, r)
    }
  }

  async function sendKeepalive(id: string, s: Slot, n: number) {
    s.queued -= n
    s.inFlight += n
    let r: SendResult | 'error'
    try {
      r = await send(id, n, { keepalive: true })
    } catch {
      r = 'error'
    }
    s.inFlight -= n
    if (r !== 'error' && r.ok) succeeded(id, s, r.likesCount)
    else failed(id, s, n, r)
  }

  return {
    tap(id) {
      const s = slot(id)
      if (s.failures > MAX_ERROR_RETRIES) s.failures = 0
      s.queued++
      if (s.queued >= cfg.max && s.blockedUntil <= clock.now()) void flush(id)
      else schedule(id, s, cfg.flushMs)
    },
    pending(id) {
      const s = slots.get(id)
      return s ? s.queued + s.inFlight : 0
    },
    confirmed: (id) => confirmedCounts.get(id) ?? 0,
    flushAll({ keepalive }) {
      for (const [id, s] of slots) {
        if (keepalive) {
          // Page is going away: send everything queued now, even beside an in-flight request.
          if (s.timer !== null) {
            clock.clearTimeout(s.timer)
            s.timer = null
          }
          while (s.queued > 0) void sendKeepalive(id, s, Math.min(s.queued, cfg.max))
        } else {
          void flush(id)
        }
      }
    },
    onServerCount(l) {
      listeners.add(l)
      return () => listeners.delete(l)
    },
  }
}
