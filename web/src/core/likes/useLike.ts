import { useCallback, useContext, useEffect, useReducer, useState } from 'react'
import { LikesContext } from './LikesRoot'

/** count = max(last server count known from a response, from page data) + taps not yet confirmed. */
export function useLike(musicianId: string, serverCount: number): { count: number; tap(): void } {
  const batcher = useContext(LikesContext)
  if (!batcher) throw new Error('useLike must be used inside <LikesRoot>')
  const [fromResponse, setFromResponse] = useState(0)
  const [, rerender] = useReducer((n: number) => n + 1, 0)

  useEffect(
    () =>
      batcher.onServerCount((id, likesCount) => {
        if (id !== musicianId) return
        setFromResponse((prev) => Math.max(prev, likesCount))
        rerender() // pending shrank even if the known count did not change
      }),
    [batcher, musicianId],
  )

  const tap = useCallback(() => {
    batcher.tap(musicianId)
    rerender()
  }, [batcher, musicianId])

  return { count: Math.max(fromResponse, serverCount) + batcher.pending(musicianId), tap }
}
