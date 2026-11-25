import { useCallback, useContext, useEffect, useReducer } from 'react'
import { LikesContext } from './LikesRoot'

/** count = max(last server-confirmed count of this musician, count from page data) + taps not yet confirmed. */
export function useLike(musicianId: string, serverCount: number): { count: number; tap(): void } {
  const batcher = useContext(LikesContext)
  if (!batcher) throw new Error('useLike must be used inside <LikesRoot>')
  const [, rerender] = useReducer((n: number) => n + 1, 0)

  useEffect(
    () =>
      batcher.onServerCount((id) => {
        if (id === musicianId) rerender() // the confirmed count moved and/or pending shrank
      }),
    [batcher, musicianId],
  )

  const tap = useCallback(() => {
    batcher.tap(musicianId)
    rerender()
  }, [batcher, musicianId])

  return { count: Math.max(batcher.confirmed(musicianId), serverCount) + batcher.pending(musicianId), tap }
}
