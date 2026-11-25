import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/types.gen'
import { loadGeo } from '../../core/map/geo'
import { useMapController } from './useMapController'

type MapPin = components['schemas']['MapPin']

const player = vi.hoisted(() => ({
  state: { status: 'idle', musicianId: null as string | null, clipId: null as string | null },
  previewId: null as string | null,
  noAudio: new Set<string>(),
  pointerEnter: vi.fn(),
  pointerLeave: vi.fn(),
  gesture: vi.fn(),
  playMusician: vi.fn(),
  stop: vi.fn(),
}))
vi.mock('../../core/audio/useFragmentPlayer', () => ({ useFragmentPlayer: () => ({ ...player }) }))

// A pin from contracts/fixtures/map-pins.json, served by MSW.
const okeanElzy: MapPin = {
  id: '945a9193544ad1ca7a278141', slug: 'okean-elzy', name: 'Океан Ельзи', kind: 'BAND', hasAudio: true,
  lat: 49.828536358078686, lon: 24.021302050093865, region: 'LVIV', placeId: 'Q36036',
}

let search = ''
function Probe() {
  search = useLocation().search
  return null
}

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/']}>
        <Probe />
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  )
  return renderHook(() => useMapController(), { wrapper })
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

function stubPosition(lon: number, lat: number) {
  vi.stubGlobal('navigator', {
    geolocation: { getCurrentPosition: (ok: (p: unknown) => void) => ok({ coords: { longitude: lon, latitude: lat } }) },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  player.state = { status: 'idle', musicianId: null, clipId: null }
  player.previewId = null
})

describe('useMapController', () => {
  it('a mouse click opens the preview without the audio gesture', async () => {
    const { result } = setup()
    act(() => result.current.handlers.onPin(okeanElzy, 'mouse'))
    await waitFor(() => expect(result.current.previewPin?.slug).toBe('okean-elzy'))
    expect(player.gesture).not.toHaveBeenCalled()
  })

  it('a tap (touch, pen, keyboard) goes to the audio core', () => {
    const { result } = setup()
    act(() => result.current.handlers.onPin(okeanElzy, 'tap'))
    expect(player.gesture).toHaveBeenCalledWith({ type: 'tap', musicianId: okeanElzy.id })
  })

  it('mouse hover is forwarded to the audio core', () => {
    const { result } = setup()
    result.current.handlers.onHoverStart('x')
    result.current.handlers.onHoverEnd('x')
    expect(player.pointerEnter).toHaveBeenCalledWith('x')
    expect(player.pointerLeave).toHaveBeenCalledWith('x')
  })

  it('handlers stay the same object when audio or preview state changes, and use the latest state', async () => {
    const { result, rerender } = setup()
    const before = result.current.handlers
    player.state = { status: 'playing', musicianId: okeanElzy.id, clipId: 'c1' }
    player.previewId = okeanElzy.id
    rerender()
    expect(result.current.handlers).toBe(before)
    expect(result.current.playingId).toBe(okeanElzy.id)
    act(() => result.current.handlers.closeSheets())
    expect(player.gesture).toHaveBeenCalledWith({ type: 'closePreview', musicianId: okeanElzy.id })
  })

  describe('sound', () => {
    it('muted: hover and tap do not start audio, a tap only opens the preview', async () => {
      const { result } = setup()
      act(() => result.current.handlers.toggleSound())
      expect(result.current.muted).toBe(true)
      expect(player.stop).toHaveBeenCalled()
      result.current.handlers.onHoverStart('x')
      expect(player.pointerEnter).not.toHaveBeenCalled()
      act(() => result.current.handlers.onPin(okeanElzy, 'tap'))
      expect(player.gesture).not.toHaveBeenCalled()
      await waitFor(() => expect(result.current.previewPin?.slug).toBe('okean-elzy'))
    })

    it('unmuting lets hover through again', () => {
      const { result } = setup()
      act(() => result.current.handlers.toggleSound())
      act(() => result.current.handlers.toggleSound())
      expect(result.current.muted).toBe(false)
      result.current.handlers.onHoverStart('x')
      expect(player.pointerEnter).toHaveBeenCalledWith('x')
    })

    it('while the browser blocks sound, the press clears the blocked state instead of muting', () => {
      player.state = { status: 'blocked', musicianId: okeanElzy.id, clipId: 'c1' }
      const { result } = setup()
      expect(result.current.blocked).toBe(true)
      act(() => result.current.handlers.toggleSound())
      expect(player.stop).toHaveBeenCalled()
      expect(result.current.muted).toBe(false)
    })
  })

  describe('near me', () => {
    it('shows the toast when the position is outside Ukraine', async () => {
      stubPosition(-0.12, 51.5)
      const { result } = setup()
      act(() => result.current.handlers.nearMe())
      await waitFor(() => expect(result.current.toast).toBe('Не вдалося визначити область. Виберіть її на карті.'))
    })

    it('shows the toast when the position is refused', () => {
      vi.stubGlobal('navigator', { geolocation: { getCurrentPosition: (_: unknown, fail: () => void) => fail() } })
      const { result } = setup()
      act(() => result.current.handlers.nearMe())
      expect(result.current.toast).toBe('Не вдалося визначити область. Виберіть її на карті.')
    })

    it('inside Ukraine: no toast and no ?region written', async () => {
      stubPosition(24.03, 49.84)
      const { result } = setup()
      await waitFor(() => expect(result.current.resultsCount).toBe(3661)) // every query has answered
      act(() => result.current.handlers.nearMe())
      await act(async () => {
        await loadGeo() // the lookup waits for the geometry
        await Promise.resolve()
      })
      expect(result.current.toast).toBeNull()
      expect(search).toBe('')
    })
  })

  it('applyRegions writes ?region= and keeps other filters; an empty list clears it', async () => {
    const { result } = setup()
    await waitFor(() => expect(result.current.regionOptions).toBeDefined())
    act(() => result.current.handlers.applyRegions(['LVIV', 'KYIV']))
    await waitFor(() => expect(search).toBe('?region=LVIV%2CKYIV'))
    expect(result.current.selectedRegions).toEqual(['LVIV', 'KYIV'])
    expect(result.current.activeFilterCount).toBe(2)
    act(() => result.current.handlers.applyRegions([]))
    await waitFor(() => expect(search).toBe(''))
  })

  describe('?musician= deep link', () => {
    const VIEWPORT = { bbox: [22, 44, 41, 53] as [number, number, number, number], zoom: 5.5 }
    function setupAt(url: string) {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={[url]}>
            <Probe />
            {children}
          </MemoryRouter>
        </QueryClientProvider>
      )
      const hook = renderHook(() => useMapController(), { wrapper })
      const flyTo = vi.fn()
      ;(hook.result.current.mapRef as { current: unknown }).current = { flyTo, fitBounds: vi.fn(), cameraForBounds: vi.fn() }
      act(() => hook.result.current.handlers.onViewport(VIEWPORT))
      return { ...hook, flyTo }
    }

    it('flies to the pin of the slug, opens its preview and drops the param', async () => {
      const { result, flyTo } = setupAt('/?musician=okean-elzy')
      await waitFor(() => expect(result.current.previewPin?.slug).toBe('okean-elzy'))
      expect(flyTo).toHaveBeenCalledTimes(1)
      expect(flyTo.mock.calls[0][0]).toMatchObject({ center: [okeanElzy.lon, okeanElzy.lat], zoom: 16 })
      expect(result.current.toast).toBeNull()
      expect(player.gesture).not.toHaveBeenCalled()
      await waitFor(() => expect(search).toBe(''))
    })

    it('unknown slug: toast errors.notFound, no camera move, no preview', async () => {
      const { result, flyTo } = setupAt('/?musician=does-not-exist')
      await waitFor(() => expect(result.current.toast).toBe('Цю сторінку не знайдено.'))
      expect(flyTo).not.toHaveBeenCalled()
      expect(result.current.previewPin).toBeUndefined()
    })

    it('keeps other params when it drops ?musician=', async () => {
      const { result } = setupAt('/?musician=okean-elzy&region=LVIV')
      await waitFor(() => expect(result.current.previewPin).toBeDefined())
      await waitFor(() => expect(search).toBe('?region=LVIV'))
    })
  })

  it('counts the musicians that match the filters', async () => {
    const { result } = setup()
    await waitFor(() => expect(result.current.resultsCount).toBe(3661))
    act(() => result.current.handlers.applyRegions(['LVIV']))
    await waitFor(() => expect(result.current.resultsCount).toBe(484))
  })
})
