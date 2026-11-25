import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../api/mocks/node'
import type { components } from '../../api/types.gen'
import { DesignContext, loadDesignConfig } from '../../core/design/config'
import type { DesignConfig } from '../../core/design/types'
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

function setup(design: DesignConfig = loadDesignConfig(null)) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <DesignContext.Provider value={design}>
        <MemoryRouter initialEntries={['/']}>
          <Probe />
          {children}
        </MemoryRouter>
      </DesignContext.Provider>
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

  describe('loading and errors', () => {
    it('says it is loading until the map has something to show', async () => {
      const { result } = setup()
      expect(result.current.loading).toBe(true)
      expect(result.current.loadError).toBeNull()
      await waitFor(() => expect(result.current.loading).toBe(false))
    })

    it('a failed load shows its message instead of an empty map, and retry loads it again', async () => {
      let fail = true
      server.use(http.get('*/map/regions', () => (fail ? HttpResponse.error() : undefined)))
      const { result } = setup()
      await waitFor(() => expect(result.current.loadError).toBe('errors.network'))
      expect(result.current.loading).toBe(false)
      fail = false
      act(() => result.current.handlers.retryLoad())
      await waitFor(() => expect(result.current.loadError).toBeNull())
      await waitFor(() => expect(result.current.regionOptions).toBeDefined())
    })

    it('a server error says the generic message', async () => {
      server.use(http.get('*/map/pins', () => new HttpResponse(null, { status: 500 })))
      const { result } = setup()
      await waitFor(() => expect(result.current.loadError).toBe('errors.generic'))
    })

    it('near me while the regions did not load says the load error, not that the region is unknown', async () => {
      server.use(http.get('*/map/regions', () => HttpResponse.error()))
      stubPosition(24.03, 49.84)
      const { result } = setup()
      await waitFor(() => expect(result.current.loadError).toBe('errors.network'))
      act(() => result.current.handlers.nearMe())
      await act(async () => {
        await loadGeo()
        await Promise.resolve()
      })
      await waitFor(() => expect(result.current.toast).toBe('Перевірте підключення й спробуйте ще раз.'))
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
    const VIEWPORT = { bbox: [22, 44, 41, 53] as [number, number, number, number], zoom: 5.5, center: [31.5, 48.5] as [number, number] }
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

    it('handles the slug once although a reduced-motion jump reports the new viewport at once (no render loop)', async () => {
      const errors = vi.spyOn(console, 'error').mockImplementation(() => {})
      const { result, flyTo } = setupAt('/?musician=okean-elzy')
      // like MapLibre with duration 0: moveend, hence onViewport, fires inside flyTo, before the URL drops ?musician=
      flyTo.mockImplementation(({ center, zoom }: { center: [number, number]; zoom: number }) =>
        result.current.handlers.onViewport({ zoom, center, bbox: [center[0] - 0.01, center[1] - 0.01, center[0] + 0.01, center[1] + 0.01] }),
      )
      await waitFor(() => expect(result.current.previewPin?.slug).toBe('okean-elzy'))
      await waitFor(() => expect(search).toBe(''))
      expect(flyTo).toHaveBeenCalledTimes(1)
      expect(errors.mock.calls.filter(([m]) => /Maximum update depth/.test(String(m)))).toEqual([])
      errors.mockRestore()
    })

    it('an equal viewport does not re-render (the same camera reported twice)', () => {
      const { result } = setupAt('/')
      const view = { zoom: 5.5, bbox: [22, 44, 41, 53] as [number, number, number, number], center: [31.5, 48.5] as [number, number] }
      act(() => result.current.handlers.onViewport({ ...view }))
      const first = result.current
      act(() => result.current.handlers.onViewport({ zoom: 5.5, bbox: [22, 44, 41, 53], center: [31.5, 48.5] }))
      expect(result.current).toBe(first) // no new render: the hook still returns the same object
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

  describe('back from another page', () => {
    // The controller lives on "/" only, like MapPage: going to a profile unmounts it, Back mounts it again.
    function setupRoutes(url = '/') {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      const out: { map: ReturnType<typeof useMapController> | null; navigate: ReturnType<typeof useNavigate> | null } = { map: null, navigate: null }
      function MapScreen() {
        out.map = useMapController()
        return null
      }
      function Nav() {
        out.navigate = useNavigate()
        return null
      }
      const view = renderHook(() => null, {
        wrapper: () => (
          <QueryClientProvider client={client}>
            <MemoryRouter initialEntries={[url]}>
              <Probe />
              <Nav />
              <Routes>
                <Route path="/" element={<MapScreen />} />
                <Route path="/m/:slug" element={null} />
              </Routes>
            </MemoryRouter>
          </QueryClientProvider>
        ),
      })
      return { ...view, out }
    }
    const HERE = { bbox: [23.9, 49.8, 24.1, 49.9] as [number, number, number, number], zoom: 13.25, center: [24.0, 49.85] as [number, number] }

    it('a fresh visit opens on the overview (no remembered camera)', () => {
      const { out } = setupRoutes()
      expect(out.map!.initialCamera).toBeNull()
    })

    it('restores the camera and the open preview of this history entry', async () => {
      const { out } = setupRoutes()
      act(() => out.map!.handlers.onViewport(HERE))
      act(() => out.map!.handlers.onPin(okeanElzy, 'mouse'))
      await waitFor(() => expect(out.map!.previewPin?.slug).toBe('okean-elzy'))
      act(() => out.navigate!('/m/okean-elzy'))
      expect(out.map).not.toBeNull()
      out.map = null
      act(() => out.navigate!(-1))
      await waitFor(() => expect(out.map).not.toBeNull())
      expect(out.map!.initialCamera).toEqual({ center: HERE.center, zoom: HERE.zoom })
      await waitFor(() => expect(out.map!.previewPin?.slug).toBe('okean-elzy'))
      expect(player.gesture).not.toHaveBeenCalled() // the preview comes back without playing
    })

    it('a remembered camera wins over ?region= (the user had moved on from it)', async () => {
      const { out } = setupRoutes('/?region=LVIV')
      act(() => out.map!.handlers.onViewport(HERE))
      act(() => out.navigate!('/m/okean-elzy'))
      out.map = null
      act(() => out.navigate!(-1))
      await waitFor(() => expect(out.map).not.toBeNull())
      expect(out.map!.initialCamera).toEqual({ center: HERE.center, zoom: HERE.zoom })
      expect(search).toBe('?region=LVIV')
    })
  })

  describe('APPROVED CHOROPLETH_PLACE_PIN + UKRAINE_WITH_NEAR_ME_PROMPT (the studio proposal)', () => {
    const approved = loadDesignConfig({
      schemaVersion: 1,
      decisions: {
        'map.aggregation': { variant: 'CHOROPLETH_PLACE_PIN', status: 'APPROVED' },
        'map.initial-view': { variant: 'UKRAINE_WITH_NEAR_ME_PROMPT', status: 'APPROVED' },
      },
    })
    const UKRAINE = { zoom: 5.5, bbox: [22, 44, 41, 53] as [number, number, number, number], center: [31.5, 48.5] as [number, number] }
    const LVIV_PLACES = { zoom: 8.5, bbox: [23, 49, 25, 50.5] as [number, number, number, number], center: [24, 49.8] as [number, number] }

    it('shades every region by its count at region level, with numerals and the legend', async () => {
      const { result } = setup(approved)
      await waitFor(() => expect(result.current.resultsCount).toBe(3661))
      act(() => result.current.handlers.onViewport(UKRAINE))
      expect(result.current.level).toBe('REGION')
      expect(result.current.numeralRegions).toBe(true)
      expect(result.current.choroplethLegend).toBe(true)
      const opacity = result.current.fillOpacity
      expect(Object.keys(opacity)).toHaveLength(27)
      expect(opacity.KYIV_CITY).toBeCloseTo(0.6) // most musicians: darkest
      expect(opacity.SEVASTOPOL_CITY).toBeCloseTo(0.15) // fewest: lightest
      expect(opacity.LVIV).toBeGreaterThan(opacity.ODESA)
    })

    it('drops the shading and the legend below region level; a selected region still stands out', async () => {
      const { result } = setup(approved)
      await waitFor(() => expect(result.current.regionOptions).toBeDefined())
      act(() => result.current.handlers.applyRegions(['LVIV']))
      act(() => result.current.handlers.onViewport(LVIV_PLACES))
      expect(result.current.level).toBe('PLACE')
      expect(result.current.choroplethLegend).toBe(false)
      await waitFor(() => expect(result.current.fillOpacity).toEqual({ LVIV: 0.35 }))
    })

    it('the PROPOSED default (REGION_PLACE_PIN) has neither', async () => {
      const { result } = setup()
      await waitFor(() => expect(result.current.resultsCount).toBe(3661))
      act(() => result.current.handlers.onViewport(UKRAINE))
      expect(result.current.level).toBe('REGION')
      expect(result.current.numeralRegions).toBe(false)
      expect(result.current.choroplethLegend).toBe(false)
      expect(result.current.fillOpacity).toEqual({})
    })
  })
})
