import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../api/mocks/node'
import type { components } from '../../api/types.gen'
import type { AudioEngine, EngineState } from './engine'
import { AudioEngineContext } from './AudioRoot'
import { useFragmentPlayer } from './useFragmentPlayer'

type Fragment = components['schemas']['Fragment']

const WITH_AUDIO = '0020093dc8080b4a9cf48a5d'
const NO_AUDIO = 'ffffffffffffffffffffffff'

function fakeEngine() {
  let state: EngineState = { status: 'idle', musicianId: null, clipId: null }
  const subs = new Set<(s: EngineState) => void>()
  const set = (s: EngineState) => {
    state = s
    subs.forEach((l) => l(s))
  }
  const plays: Array<{ id: string; f: Fragment }> = []
  const stops: Array<{ fade?: boolean } | undefined> = []
  const engine: AudioEngine = {
    async play(id, f) {
      plays.push({ id, f })
      set({ status: 'playing', musicianId: id, clipId: f.clipId })
    },
    unlock: async () => true,
    stop(o) {
      stops.push(o)
      set({ status: 'idle', musicianId: null, clipId: null })
    },
    getState: () => state,
    subscribe(l) {
      subs.add(l)
      return () => subs.delete(l)
    },
  }
  return { engine, plays, stops, set }
}

const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

/** Pass the same client to two setups for two consumers of one app (the map controller and the preview card). */
function setup(engine: AudioEngine, client = newClient()) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AudioEngineContext.Provider value={engine}>{children}</AudioEngineContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>
  )
  return renderHook(() => ({ player: useFragmentPlayer(), navigate: useNavigate() }), { wrapper })
}

const mockDesktop = (matches: boolean) =>
  vi.stubGlobal('matchMedia', (q: string) => ({ matches, media: q, addEventListener() {}, removeEventListener() {} }))

describe('useFragmentPlayer', () => {
  beforeEach(() => mockDesktop(true))
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('204 marks musician as noAudio and does not play', async () => {
    const f = fakeEngine()
    const { result } = setup(f.engine)
    await act(async () => result.current.player.playMusician(NO_AUDIO))
    await waitFor(() => expect(result.current.player.noAudio.has(NO_AUDIO)).toBe(true))
    expect(f.plays).toHaveLength(0)
  })

  it('exposes the current fragment, also while loading and to a second consumer, and drops it on stop', async () => {
    const f = fakeEngine()
    const { result } = setup(f.engine)
    expect(result.current.player.fragment).toBeNull()
    await act(async () => result.current.player.playMusician(WITH_AUDIO))
    await waitFor(() => expect(f.plays).toHaveLength(1))
    expect(result.current.player.fragment?.clipId).toBe(f.plays[0].f.clipId)
    expect(result.current.player.fragment?.title).toBe(f.plays[0].f.title)
    act(() => f.set({ status: 'loading', musicianId: WITH_AUDIO, clipId: f.plays[0].f.clipId }))
    expect(result.current.player.fragment?.title).toBe(f.plays[0].f.title)
    // another hook instance on the same engine (the preview card next to the map controller) sees it too
    const other = setup(f.engine)
    expect(other.result.current.player.fragment?.clipId).toBe(f.plays[0].f.clipId)
    act(() => result.current.player.stop())
    expect(result.current.player.fragment).toBeNull()
  })

  it('second play of same musician sends exclude = previous clipId', async () => {
    const seen: Array<string | null> = []
    server.use(
      http.get('*/musicians/:id/fragment', ({ request }) => {
        seen.push(new URL(request.url).searchParams.get('exclude'))
        return HttpResponse.json({
          clipId: `clip-${seen.length}`,
          url: 'https://x/a.wav',
          startSec: 0,
          durationSec: 15,
          fadeInMs: 300,
          fadeOutMs: 600,
          title: 't',
          source: 'UPLOAD',
          attribution: 'a',
        })
      }),
    )
    const f = fakeEngine()
    const { result } = setup(f.engine)
    await act(async () => result.current.player.playMusician(WITH_AUDIO))
    await waitFor(() => expect(f.plays).toHaveLength(1))
    await act(async () => result.current.player.playMusician(WITH_AUDIO))
    await waitFor(() => expect(f.plays).toHaveLength(2))
    expect(seen).toEqual([null, 'clip-1'])
  })

  it('retries once with exclude = broken clip when playback errors', async () => {
    const seen: Array<string | null> = []
    server.use(
      http.get('*/musicians/:id/fragment', ({ request }) => {
        seen.push(new URL(request.url).searchParams.get('exclude'))
        return HttpResponse.json({
          clipId: `clip-${seen.length}`, url: 'https://x/a.wav', startSec: 0, durationSec: 15,
          fadeInMs: 300, fadeOutMs: 600, title: 't', source: 'UPLOAD', attribution: 'a',
        })
      }),
    )
    const f = fakeEngine()
    const realPlay = f.engine.play
    let n = 0
    f.engine.play = async (id, fr) => {
      await realPlay(id, fr)
      if (n++ === 0) f.set({ status: 'error', musicianId: id, clipId: fr.clipId })
    }
    const { result } = setup(f.engine)
    await act(async () => result.current.player.playMusician(WITH_AUDIO))
    await waitFor(() => expect(f.plays).toHaveLength(2))
    expect(seen).toEqual([null, 'clip-1'])
  })

  it('stops on route change and when page becomes hidden', async () => {
    const f = fakeEngine()
    const { result } = setup(f.engine)
    await act(async () => result.current.player.playMusician(WITH_AUDIO))
    await waitFor(() => expect(f.plays).toHaveLength(1))
    act(() => result.current.navigate('/about'))
    await waitFor(() => expect(f.stops.length).toBeGreaterThanOrEqual(1))
    const before = f.stops.length
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    act(() => void document.dispatchEvent(new Event('visibilitychange')))
    expect(f.stops.length).toBeGreaterThan(before)
  })

  it('unmounting stops the engine and cancels an in-flight fetch', async () => {
    let release!: () => void
    const gate = new Promise<void>((r) => (release = r))
    server.use(
      http.get('*/musicians/:id/fragment', async () => {
        await gate
        return HttpResponse.json({
          clipId: 'late', url: 'https://x/a.wav', startSec: 0, durationSec: 15,
          fadeInMs: 300, fadeOutMs: 600, title: 't', source: 'UPLOAD', attribution: 'a',
        })
      }),
    )
    const f = fakeEngine()
    const { result, unmount } = setup(f.engine)
    act(() => result.current.player.playMusician(WITH_AUDIO))
    unmount()
    expect(f.stops.at(-1)).toEqual({ fade: true })
    release()
    await new Promise((r) => setTimeout(r, 50))
    expect(f.plays).toHaveLength(0)
  })

  it('desktop hover plays after 250 ms and stops with fade on leave', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const f = fakeEngine()
      const { result } = setup(f.engine)
      act(() => result.current.player.pointerEnter(WITH_AUDIO))
      await act(async () => void (await vi.advanceTimersByTimeAsync(300)))
      await waitFor(() => expect(f.plays).toHaveLength(1))
      act(() => result.current.player.pointerLeave(WITH_AUDIO))
      expect(f.stops.at(-1)).toEqual({ fade: true })
    } finally {
      vi.useRealTimers()
    }
  })

  describe('a fragment that is still being fetched', () => {
    function gatedFragment() {
      let release!: () => void
      const gate = new Promise<void>((r) => (release = r))
      let answered = 0
      server.use(
        http.get('*/musicians/:id/fragment', async () => {
          await gate
          answered++
          return HttpResponse.json({
            clipId: 'late', url: 'https://x/a.wav', startSec: 0, durationSec: 15,
            fadeInMs: 300, fadeOutMs: 600, title: 't', source: 'UPLOAD', attribution: 'a',
          })
        }),
      )
      return {
        release: async () => {
          release()
          await waitFor(() => expect(answered).toBe(1))
          await act(async () => void (await new Promise((r) => setTimeout(r, 50))))
        },
      }
    }

    it('closing the preview while the fragment loads stops it: no playback after the sheet is gone', async () => {
      mockDesktop(false)
      const gate = gatedFragment()
      const f = fakeEngine()
      const { result } = setup(f.engine)
      act(() => result.current.player.gesture({ type: 'tap', musicianId: WITH_AUDIO }))
      expect(result.current.player.previewId).toBe(WITH_AUDIO)
      act(() => result.current.player.gesture({ type: 'closePreview', musicianId: WITH_AUDIO }))
      expect(result.current.player.previewId).toBeNull()
      await gate.release()
      expect(f.plays).toHaveLength(0)
    })

    it('a second tap on the same pin while its fragment loads stops it (it counts as playing)', async () => {
      mockDesktop(false)
      const gate = gatedFragment()
      const f = fakeEngine()
      const { result } = setup(f.engine)
      act(() => result.current.player.gesture({ type: 'tap', musicianId: WITH_AUDIO }))
      act(() => result.current.player.gesture({ type: 'tap', musicianId: WITH_AUDIO }))
      await gate.release()
      expect(f.plays).toHaveLength(0)
    })

    it('stop from another consumer of the same engine (the card) cancels the fetch the map started', async () => {
      mockDesktop(false)
      const gate = gatedFragment()
      const f = fakeEngine()
      const client = newClient()
      const map = setup(f.engine, client)
      const card = setup(f.engine, client)
      act(() => map.result.current.player.gesture({ type: 'tap', musicianId: WITH_AUDIO }))
      act(() => card.result.current.player.stop())
      await gate.release()
      expect(f.plays).toHaveLength(0)
    })

    it('another consumer unmounting does not cancel it (the card closes, the map keeps the request)', async () => {
      mockDesktop(false)
      const gate = gatedFragment()
      const f = fakeEngine()
      const client = newClient()
      const map = setup(f.engine, client)
      const card = setup(f.engine, client)
      act(() => map.result.current.player.playMusician(WITH_AUDIO))
      card.unmount()
      await gate.release()
      expect(f.plays).toHaveLength(1)
    })
  })

  describe('another fragment', () => {
    const clipServer = (clipFor: (n: number) => string) => {
      const seen: Array<string | null> = []
      server.use(
        http.get('*/musicians/:id/fragment', ({ request }) => {
          seen.push(new URL(request.url).searchParams.get('exclude'))
          return HttpResponse.json({
            clipId: clipFor(seen.length), url: 'https://x/a.wav', startSec: seen.length, durationSec: 15,
            fadeInMs: 300, fadeOutMs: 600, title: 't', source: 'UPLOAD', attribution: 'a',
          })
        }),
      )
      return seen
    }

    it('the card asks for a different clip than the one the map just played', async () => {
      const seen = clipServer((n) => `clip-${n}`)
      const f = fakeEngine()
      const client = newClient()
      const map = setup(f.engine, client)
      const card = setup(f.engine, client)
      await act(async () => map.result.current.player.playMusician(WITH_AUDIO))
      await waitFor(() => expect(f.plays).toHaveLength(1))
      await act(async () => card.result.current.player.playMusician(WITH_AUDIO))
      await waitFor(() => expect(f.plays).toHaveLength(2))
      expect(seen).toEqual([null, 'clip-1'])
      expect(f.plays.map((p) => p.f.clipId)).toEqual(['clip-1', 'clip-2'])
    })

    it('a musician with one clip gets a fresh fragment every time, never a cached replay', async () => {
      clipServer(() => 'only')
      const f = fakeEngine()
      const { result } = setup(f.engine)
      for (let i = 1; i <= 3; i++) {
        await act(async () => result.current.player.playMusician(WITH_AUDIO))
        await waitFor(() => expect(f.plays).toHaveLength(i))
      }
      expect(f.plays.map((p) => p.f.startSec)).toEqual([1, 2, 3])
    })
  })

  it('touch: gesture goes through the reducer (tap previews and plays)', async () => {
    mockDesktop(false)
    const f = fakeEngine()
    const { result } = setup(f.engine)
    act(() => result.current.player.pointerEnter(WITH_AUDIO)) // ignored on touch
    act(() => result.current.player.gesture({ type: 'tap', musicianId: WITH_AUDIO }))
    expect(result.current.player.previewId).toBe(WITH_AUDIO)
    await waitFor(() => expect(f.plays).toHaveLength(1))
    await waitFor(() => expect(result.current.player.state.status).toBe('playing'))
    act(() => result.current.player.gesture({ type: 'tap', musicianId: WITH_AUDIO }))
    expect(result.current.player.state.status).toBe('idle')
  })
})
