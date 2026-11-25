import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useContext, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { useInRouterContext, useLocation } from 'react-router-dom'
import type { components } from '../../api/types.gen'
import { useDesign } from '../design/config'
import { AudioEngineContext, realClock } from './AudioRoot'
import type { EngineState } from './engine'
import { getFragment } from './fragments'
import { createHoverIntent } from './hoverIntent'
import { reducePinGesture, type MobileTrigger, type PinGesture } from './mobileTrigger'

type Fragment = components['schemas']['Fragment']

const HOVER_INTENT_MS = 250
const IDLE: EngineState = { status: 'idle', musicianId: null, clipId: null }

/**
 * What every consumer of one engine (map controller, in-card player) shares: what was played, and the request in
 * flight. A play request or a stop from any consumer supersedes the fetches the others still wait for.
 */
interface Session {
  /** Fragments handed to the engine, by clipId, so every consumer sees what is playing. */
  played: Map<string, Fragment>
  /** Last clip per musician: the next request for the musician excludes it («Інший фрагмент»). */
  lastClip: Map<string, string>
  /** Bumped by every play request and every stop; a fetch from an older epoch never starts playback. */
  epoch: number
  /** The musician whose fragment is being fetched; for the tap reducer it counts as playing. */
  pending: { id: string; epoch: number; owner: object } | null
}
const sessions = new WeakMap<object, Session>()
function sessionOf(engine: object): Session {
  let s = sessions.get(engine)
  if (!s) sessions.set(engine, (s = { played: new Map(), lastClip: new Map(), epoch: 0, pending: null }))
  return s
}
const fragmentKey = (id: string, exclude?: string) => ['fragment', id, exclude ?? null]
const isDesktop = () => window.matchMedia?.('(hover: hover) and (pointer: fine)').matches ?? false

function usePathname(): string | null {
  // AudioRoot lives above the router; consumers normally sit inside it.
  const inRouter = useInRouterContext()
  // eslint-disable-next-line react-hooks/rules-of-hooks -- inRouter never changes for a mounted component
  return inRouter ? useLocation().pathname : null
}

export function useFragmentPlayer() {
  const engine = useContext(AudioEngineContext)
  const queryClient = useQueryClient()
  const mode = useDesign().variant('audio.mobile-trigger') as MobileTrigger
  const [previewId, setPreviewId] = useState<string | null>(null)
  const [noAudio, setNoAudio] = useState<Set<string>>(() => new Set())
  const session = useMemo(() => (engine ? sessionOf(engine) : null), [engine])
  // this consumer's own requests: its unmount or pointer leave cancels only those
  const token = useRef(0)
  const [owner] = useState(() => ({}))
  const hover = useMemo(() => createHoverIntent(HOVER_INTENT_MS, realClock), [])

  const state = useSyncExternalStore(
    useCallback((l) => engine?.subscribe(l) ?? (() => {}), [engine]),
    () => engine?.getState() ?? IDLE,
  )

  // Fragment of the current engine state (also while it is loading); null when idle or unknown.
  const fragment = (session && state.clipId && state.status !== 'idle' ? session.played.get(state.clipId) : undefined) ?? null

  const fetchFragment = useCallback(
    (id: string, exclude?: string): Promise<Fragment | null> =>
      queryClient.fetchQuery({
        queryKey: fragmentKey(id, exclude),
        queryFn: () => getFragment(id, exclude),
        staleTime: 30_000, // a hover prefetch is reused by the play that follows it
      }),
    [queryClient],
  )

  /** Cancels this consumer's own fetch (unmount, pointer leave); other consumers' requests go on. */
  const cancelOwn = useCallback(() => {
    token.current++
    if (session?.pending?.owner === owner) session.pending = null
  }, [owner, session])

  const stop = useCallback(
    (opts?: { fade?: boolean }) => {
      token.current++
      hover.cancel()
      if (session) {
        session.epoch++ // whichever consumer started a fetch, it must not start playback now
        session.pending = null
      }
      engine?.stop(opts)
    },
    [engine, hover, session],
  )

  const playMusician = useCallback(
    async (id: string) => {
      if (!engine || !session) return
      const my = ++token.current
      const epoch = ++session.epoch
      const live = () => my === token.current && epoch === session.epoch
      let exclude = session.lastClip.get(id)
      try {
        for (let attempt = 0; attempt < 2; attempt++) {
          session.pending = { id, epoch, owner }
          let f: Fragment | null
          try {
            f = await fetchFragment(id, exclude)
          } catch {
            return
          }
          if (!live()) return
          session.pending = null
          // a response plays once: the next request for this musician asks the server again (a new clip or start)
          queryClient.removeQueries({ queryKey: fragmentKey(id, exclude), exact: true })
          if (!f) {
            setNoAudio((prev) => (prev.has(id) ? prev : new Set(prev).add(id)))
            return
          }
          session.lastClip.set(id, f.clipId)
          session.played.set(f.clipId, f)
          await engine.play(id, f)
          if (!live()) return
          const s = engine.getState()
          if (s.status !== 'error' || s.clipId !== f.clipId) return
          exclude = f.clipId // one retry with a different clip
        }
      } finally {
        if (session.pending?.epoch === epoch) session.pending = null
      }
    },
    [engine, fetchFragment, owner, queryClient, session],
  )

  const pointerEnter = useCallback(
    (id: string) => {
      if (!isDesktop() || noAudio.has(id)) return
      void fetchFragment(id, session?.lastClip.get(id)).catch(() => {}) // prefetch while hover-intent waits
      hover.enter(id, () => void playMusician(id))
    },
    [fetchFragment, hover, noAudio, playMusician, session],
  )

  const pointerLeave = useCallback(
    (id: string) => {
      if (!isDesktop()) return
      hover.leave(id)
      const s = engine?.getState()
      if (s?.musicianId === id && s.status !== 'idle') stop({ fade: true })
      else cancelOwn() // a fetch that is still in flight
    },
    [cancelOwn, engine, hover, stop],
  )

  const gesture = useCallback(
    (g: PinGesture) => {
      const s = engine?.getState() ?? IDLE
      // a fragment still being fetched counts as playing: closing the preview or tapping the pin again stops it
      const playingId = session?.pending?.id ?? (s.status === 'playing' || s.status === 'loading' ? s.musicianId : null)
      const action = reducePinGesture(mode, { previewId, playingId }, g)
      if (action.preview !== undefined) setPreviewId(action.preview)
      if (action.stop) stop({ fade: true })
      if (action.play) void playMusician(action.play)
    },
    [engine, mode, playMusician, previewId, session, stop],
  )

  const pathname = usePathname()
  const prevPath = useRef(pathname)
  useEffect(() => {
    if (prevPath.current !== pathname) {
      prevPath.current = pathname
      stop({ fade: true })
    }
  }, [pathname, stop])

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') stop()
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [stop])

  useEffect(
    () => () => {
      cancelOwn() // an in-flight fetch of this consumer cannot start playback after unmount
      hover.cancel()
      engine?.stop({ fade: true })
    },
    [cancelOwn, engine, hover],
  )

  return { state, fragment, previewId, noAudio, pointerEnter, pointerLeave, gesture, playMusician: (id: string) => void playMusician(id), stop: () => stop({ fade: true }) }
}
