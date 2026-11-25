import { describe, expect, it } from 'vitest'
import type { Clock } from '../audio/engine'
import { createLikeBatcher, type SendResult } from './batcher'

function fakeClock() {
  let t = 0
  let next = 1
  const timers = new Map<number, { at: number; fn: () => void }>()
  const clock: Clock = {
    now: () => t,
    setTimeout(fn, ms) {
      const id = next++
      timers.set(id, { at: t + ms, fn })
      return id
    },
    clearTimeout(id) {
      timers.delete(id)
    },
  }
  const advance = async (ms: number) => {
    const target = t + ms
    for (;;) {
      let pick: [number, { at: number; fn: () => void }] | undefined
      for (const e of timers) if (e[1].at <= target && (!pick || e[1].at < pick[1].at)) pick = e
      if (!pick) break
      timers.delete(pick[0])
      t = pick[1].at
      pick[1].fn()
      await flushMicrotasks()
    }
    t = target
  }
  return { clock, advance }
}

const flushMicrotasks = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve()
}

type Call = { id: string; count: number; keepalive: boolean }

function setup(respond?: (call: Call, n: number) => SendResult | Promise<SendResult> | 'throw') {
  const { clock, advance } = fakeClock()
  const calls: Call[] = []
  let server = 100
  const send = async (id: string, count: number, opts: { keepalive: boolean }): Promise<SendResult> => {
    const call = { id, count, keepalive: opts.keepalive }
    calls.push(call)
    const r = respond?.(call, calls.length)
    if (r === 'throw') throw new Error('network')
    if (r) return r
    server += count
    return { ok: true, likesCount: server }
  }
  const batcher = createLikeBatcher({ send, clock }, { flushMs: 600, max: 50 })
  const counts: Array<[string, number]> = []
  batcher.onServerCount((id, n) => counts.push([id, n]))
  const sum = () => calls.filter((c) => !failed.has(c)).reduce((s, c) => s + c.count, 0)
  const failed = new Set<Call>()
  return { batcher, calls, advance, counts, sum, failed }
}

const tapN = (b: { tap(id: string): void }, n: number, id = 'm1') => {
  for (let i = 0; i < n; i++) b.tap(id)
}

describe('like batcher', () => {
  it('groups 7 quick taps into one request of 7 after 600 ms', async () => {
    const { batcher, calls, advance, counts } = setup()
    tapN(batcher, 7)
    await advance(599)
    expect(calls).toEqual([])
    await advance(1)
    expect(calls).toEqual([{ id: 'm1', count: 7, keepalive: false }])
    expect(counts).toEqual([['m1', 107]])
    expect(batcher.pending('m1')).toBe(0)
  })

  it('restarts the timer on each tap', async () => {
    const { batcher, calls, advance } = setup()
    tapN(batcher, 1)
    await advance(400)
    tapN(batcher, 1)
    await advance(400)
    expect(calls).toEqual([])
    await advance(200)
    expect(calls.map((c) => c.count)).toEqual([2])
  })

  it('flushes immediately at 50 taps and continues batching the rest', async () => {
    const { batcher, calls, advance } = setup()
    tapN(batcher, 53)
    await flushMicrotasks()
    expect(calls.map((c) => c.count)).toEqual([50])
    await advance(599)
    expect(calls.map((c) => c.count)).toEqual([50])
    await advance(1)
    expect(calls.map((c) => c.count)).toEqual([50, 3])
  })

  it('retries rate-limited taps without losing any', async () => {
    const { batcher, calls, advance, failed } = setup((call, n) => {
      if (n === 1) {
        failed.add(call)
        return { ok: false, retryAfterMs: 3000 }
      }
      return undefined as never
    })
    tapN(batcher, 7)
    await advance(600)
    expect(calls.map((c) => c.count)).toEqual([7])
    tapN(batcher, 3) // more taps while rate-limited accumulate and must not shorten the wait
    expect(batcher.pending('m1')).toBe(10)
    await advance(2999)
    expect(calls).toHaveLength(1)
    await advance(1)
    expect(calls.map((c) => c.count)).toEqual([7, 10])
    const ok = calls.filter((c) => !failed.has(c)).reduce((s, c) => s + c.count, 0)
    expect(ok).toBe(10)
    expect(ok).toBe(7 + 3)
    expect(batcher.pending('m1')).toBe(0)
  })

  it('retries after a thrown error in 2000 ms, at most 5 times in a row, then waits for the next tap', async () => {
    const { batcher, calls, advance } = setup(() => 'throw')
    tapN(batcher, 4)
    await advance(600)
    expect(calls).toHaveLength(1)
    for (let i = 0; i < 5; i++) await advance(2000)
    expect(calls).toHaveLength(6)
    expect(calls.every((c) => c.count === 4)).toBe(true)
    await advance(60_000)
    expect(calls).toHaveLength(6)
    expect(batcher.pending('m1')).toBe(4)
    tapN(batcher, 1)
    await advance(600)
    expect(calls).toHaveLength(7)
    expect(calls[6].count).toBe(5)
  })

  it('flushAll sends remaining with keepalive', async () => {
    const { batcher, calls, advance } = setup()
    tapN(batcher, 7, 'a')
    tapN(batcher, 2, 'b')
    batcher.flushAll({ keepalive: true })
    await flushMicrotasks()
    expect(calls).toEqual([
      { id: 'a', count: 7, keepalive: true },
      { id: 'b', count: 2, keepalive: true },
    ])
    await advance(10_000)
    expect(calls).toHaveLength(2)
    expect(batcher.pending('a') + batcher.pending('b')).toBe(0)
  })

  it('flushAll sends exactly the unsent taps in chunks of at most 50, even while a request is in flight', async () => {
    let release!: (r: SendResult) => void
    const { batcher, calls } = setup((_c, n) =>
      n === 1 ? new Promise<SendResult>((res) => (release = res)) : undefined as never,
    )
    tapN(batcher, 50) // in flight (never answered)
    tapN(batcher, 120) // queued: 50 flushed? no, in flight blocks it
    batcher.flushAll({ keepalive: true })
    await flushMicrotasks()
    const kept = calls.slice(1)
    expect(kept.every((c) => c.keepalive)).toBe(true)
    expect(kept.map((c) => c.count)).toEqual([50, 50, 20])
    release({ ok: true, likesCount: 1 })
    await flushMicrotasks()
    expect(batcher.pending('m1')).toBe(0)
  })

  it('flushAll puts taps back when the keepalive request is rejected', async () => {
    const { batcher, calls, advance } = setup((_c, n) => (n === 1 ? 'throw' : (undefined as never)))
    tapN(batcher, 3)
    batcher.flushAll({ keepalive: true })
    await flushMicrotasks()
    expect(batcher.pending('m1')).toBe(3)
    await advance(2000)
    expect(calls.map((c) => c.count)).toEqual([3, 3])
    expect(batcher.pending('m1')).toBe(0)
  })

  it('never has two requests in flight for one musician', async () => {
    const resolvers: Array<(r: SendResult) => void> = []
    let inFlight = 0
    let maxInFlight = 0
    const { clock, advance } = fakeClock()
    const sent: number[] = []
    const batcher = createLikeBatcher(
      {
        clock,
        send: (_id, count) => {
          sent.push(count)
          inFlight++
          maxInFlight = Math.max(maxInFlight, inFlight)
          return new Promise<SendResult>((res) =>
            resolvers.push((r) => {
              inFlight--
              res(r)
            }),
          )
        },
      },
      { flushMs: 600, max: 50 },
    )
    tapN(batcher, 5)
    await advance(600)
    expect(sent).toEqual([5])
    tapN(batcher, 4)
    await advance(600) // timer fires while the first is still in flight
    expect(sent).toEqual([5])
    resolvers[0]({ ok: true, likesCount: 5 })
    await flushMicrotasks()
    expect(sent).toEqual([5, 4])
    resolvers[1]({ ok: true, likesCount: 9 })
    await flushMicrotasks()
    expect(maxInFlight).toBe(1)
    expect(batcher.pending('m1')).toBe(0)
  })

  it('optimistic count = server count + pending (pending includes unconfirmed in-flight taps)', async () => {
    let release!: (r: SendResult) => void
    const { batcher, advance } = setup(() => new Promise<SendResult>((res) => (release = res)))
    expect(batcher.pending('m1')).toBe(0)
    tapN(batcher, 3)
    expect(batcher.pending('m1')).toBe(3)
    await advance(600) // now in flight
    expect(batcher.pending('m1')).toBe(3)
    tapN(batcher, 2)
    expect(batcher.pending('m1')).toBe(5)
    release({ ok: true, likesCount: 103 })
    await flushMicrotasks()
    expect(batcher.pending('m1')).toBe(2)
  })

  it('unsubscribes server-count listeners', async () => {
    const { batcher, advance } = setup()
    const seen: number[] = []
    const off = batcher.onServerCount((_id, n) => seen.push(n))
    off()
    tapN(batcher, 1)
    await advance(600)
    expect(seen).toEqual([])
  })
})
