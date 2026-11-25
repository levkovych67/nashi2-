import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { createLikeBatcher, type SendResult } from './batcher'
import { LikesContext } from './LikesRoot'
import { useLike } from './useLike'

function setup() {
  let release!: (r: SendResult) => void
  const batcher = createLikeBatcher(
    {
      send: () => new Promise<SendResult>((res) => (release = res)),
      clock: { now: () => 0, setTimeout: (fn, ms) => window.setTimeout(fn, ms), clearTimeout: (h) => window.clearTimeout(h) },
    },
    { flushMs: 600, max: 3 },
  )
  const wrapper = ({ children }: { children: ReactNode }) => (
    <LikesContext.Provider value={batcher}>{children}</LikesContext.Provider>
  )
  return { batcher, wrapper, release: (r: SendResult) => release(r) }
}

describe('useLike', () => {
  it('shows page count + pending immediately, then max(server response, page) + pending without bouncing back', async () => {
    const { wrapper, release } = setup()
    const { result, rerender } = renderHook(({ n }) => useLike('m1', n), { wrapper, initialProps: { n: 10 } })
    expect(result.current.count).toBe(10)
    act(() => result.current.tap())
    act(() => result.current.tap())
    expect(result.current.count).toBe(12)
    act(() => result.current.tap()) // reaches max 3 -> in flight, still counted
    expect(result.current.count).toBe(13)
    await act(async () => release({ ok: true, likesCount: 13 }))
    expect(result.current.count).toBe(13)
    // stale page data (older than the response) must not lower the count
    rerender({ n: 11 })
    expect(result.current.count).toBe(13)
    // fresher page data wins
    rerender({ n: 20 })
    expect(result.current.count).toBe(20)
  })

  it('throws outside LikesRoot', () => {
    expect(() => renderHook(() => useLike('m1', 0))).toThrow(/LikesRoot/)
  })

  it('keeps the confirmed count per musician: switching musicianId does not leak another musician\'s count', async () => {
    const { wrapper, release } = setup()
    const { result, rerender } = renderHook(({ id, n }) => useLike(id, n), { wrapper, initialProps: { id: 'm1', n: 10 } })
    for (let i = 0; i < 3; i++) act(() => result.current.tap())
    await act(async () => release({ ok: true, likesCount: 500 }))
    expect(result.current.count).toBe(500)
    rerender({ id: 'm2', n: 7 })
    expect(result.current.count).toBe(7)
    rerender({ id: 'm1', n: 10 })
    expect(result.current.count).toBe(500)
  })

  it('unmount and remount keep the confirmed count even if the cached page data is stale', async () => {
    const { wrapper, release } = setup()
    const first = renderHook(() => useLike('m1', 10), { wrapper })
    for (let i = 0; i < 3; i++) act(() => first.result.current.tap())
    await act(async () => release({ ok: true, likesCount: 13 }))
    expect(first.result.current.count).toBe(13)
    first.unmount()
    const second = renderHook(() => useLike('m1', 10), { wrapper })
    expect(second.result.current.count).toBe(13)
  })
})
