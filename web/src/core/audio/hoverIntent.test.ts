import { describe, expect, it, vi } from 'vitest'
import type { Clock } from './engine'
import { createHoverIntent } from './hoverIntent'

function fakeClock() {
  let t = 0
  let next = 1
  const timers = new Map<number, { at: number; fn: () => void }>()
  const clock: Clock = {
    now: () => t,
    setTimeout: (fn, ms) => (timers.set(next, { at: t + ms, fn }), next++),
    clearTimeout: (id) => void timers.delete(id),
  }
  const advance = (ms: number) => {
    t += ms
    for (const [id, x] of [...timers]) if (x.at <= t) (timers.delete(id), x.fn())
  }
  return { clock, advance }
}

describe('hover intent', () => {
  it('hover intent fires after 250 ms and is cancelled by leave', () => {
    const { clock, advance } = fakeClock()
    const h = createHoverIntent(250, clock)
    const fire = vi.fn()
    h.enter('a', fire)
    advance(249)
    expect(fire).not.toHaveBeenCalled()
    advance(1)
    expect(fire).toHaveBeenCalledTimes(1)

    const fire2 = vi.fn()
    h.enter('b', fire2)
    advance(100)
    h.leave('b')
    advance(500)
    expect(fire2).not.toHaveBeenCalled()
  })

  it('entering another pin replaces the pending one; leave of a different id is ignored; cancel clears', () => {
    const { clock, advance } = fakeClock()
    const h = createHoverIntent(250, clock)
    const a = vi.fn()
    const b = vi.fn()
    h.enter('a', a)
    advance(100)
    h.enter('b', b)
    h.leave('a')
    advance(250)
    expect(a).not.toHaveBeenCalled()
    expect(b).toHaveBeenCalledTimes(1)
    const c = vi.fn()
    h.enter('c', c)
    h.cancel()
    advance(500)
    expect(c).not.toHaveBeenCalled()
  })
})
