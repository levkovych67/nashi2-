import { createContext, useEffect, useState, type ReactNode } from 'react'
import { usePublicConfig } from '../../api/queries'
import { realClock } from '../audio/AudioRoot'
import { createLikeBatcher, type LikeBatcher } from './batcher'
import { sendLikes } from './sendLikes'

/** One batcher per app; `null` until `LikesRoot` mounts. */
export const LikesContext = createContext<LikeBatcher | null>(null)

const DEFAULTS = { flushMs: 600, max: 50 }

export function LikesRoot({ children }: { children: ReactNode }) {
  const [cfg] = useState(() => ({ ...DEFAULTS }))
  const [batcher] = useState(() => createLikeBatcher({ send: sendLikes, clock: realClock }, cfg))
  const likes = usePublicConfig().data?.likes
  // The batcher reads cfg on every use, so server-provided limits apply without recreating it (and losing taps).
  if (likes) {
    cfg.flushMs = likes.flushMs
    cfg.max = likes.batchMax
  }
  useEffect(() => {
    const onHide = () => batcher.flushAll({ keepalive: true })
    const onVisibility = () => document.visibilityState === 'hidden' && onHide()
    window.addEventListener('pagehide', onHide)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      window.removeEventListener('pagehide', onHide)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [batcher])
  return <LikesContext.Provider value={batcher}>{children}</LikesContext.Provider>
}
