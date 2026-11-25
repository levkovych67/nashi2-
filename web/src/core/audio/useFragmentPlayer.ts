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
  const lastClip = useRef(new Map<string, string>())
  const token = useRef(0)
  const hover = useMemo(() => createHoverIntent(HOVER_INTENT_MS, realClock), [])

  const state = useSyncExternalStore(
    useCallback((l) => engine?.subscribe(l) ?? (() => {}), [engine]),
    () => engine?.getState() ?? IDLE,
  )

  const fetchFragment = useCallback(
    (id: string, exclude?: string): Promise<Fragment | null> =>
      queryClient.fetchQuery({
        queryKey: ['fragment', id, exclude ?? null],
        queryFn: () => getFragment(id, exclude),
        staleTime: 30_000,
      }),
    [queryClient],
  )

  const stop = useCallback(
    (opts?: { fade?: boolean }) => {
      token.current++
      hover.cancel()
      engine?.stop(opts)
    },
    [engine, hover],
  )

  const playMusician = useCallback(
    async (id: string) => {
      if (!engine) return
      const my = ++token.current
      let exclude = lastClip.current.get(id)
      for (let attempt = 0; attempt < 2; attempt++) {
        let f: Fragment | null
        try {
          f = await fetchFragment(id, exclude)
        } catch {
          return
        }
        if (my !== token.current) return
        if (!f) {
          setNoAudio((prev) => (prev.has(id) ? prev : new Set(prev).add(id)))
          return
        }
        lastClip.current.set(id, f.clipId)
        await engine.play(id, f)
        if (my !== token.current) return
        const s = engine.getState()
        if (s.status !== 'error' || s.clipId !== f.clipId) return
        exclude = f.clipId // one retry with a different clip
      }
    },
    [engine, fetchFragment],
  )

  const pointerEnter = useCallback(
    (id: string) => {
      if (!isDesktop() || noAudio.has(id)) return
      void fetchFragment(id, lastClip.current.get(id)).catch(() => {}) // prefetch while hover-intent waits
      hover.enter(id, () => void playMusician(id))
    },
    [fetchFragment, hover, noAudio, playMusician],
  )

  const pointerLeave = useCallback(
    (id: string) => {
      if (!isDesktop()) return
      hover.leave(id)
      const s = engine?.getState()
      if (s?.musicianId === id && s.status !== 'idle') stop({ fade: true })
      else token.current++ // cancel a fetch that is still in flight
    },
    [engine, hover, stop],
  )

  const gesture = useCallback(
    (g: PinGesture) => {
      const s = engine?.getState() ?? IDLE
      const playingId = s.status === 'playing' || s.status === 'loading' ? s.musicianId : null
      const action = reducePinGesture(mode, { previewId, playingId }, g)
      if (action.preview !== undefined) setPreviewId(action.preview)
      if (action.stop) stop({ fade: true })
      if (action.play) void playMusician(action.play)
    },
    [engine, mode, playMusician, previewId, stop],
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
      token.current++ // cancel any in-flight fetch so it cannot start playback after unmount
      hover.cancel()
      engine?.stop({ fade: true })
    },
    [engine, hover],
  )

  return { state, previewId, noAudio, pointerEnter, pointerLeave, gesture, playMusician: (id: string) => void playMusician(id), stop: () => stop({ fade: true }) }
}
