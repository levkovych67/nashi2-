import fs from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec'
import { MapStudies } from '../../src/workbench/MapStudies'
import type { AtlasData } from '../../src/workbench/data'
import {
  boundsOf,
  fit,
  levelAt,
  makeIndex,
  project,
  regionAt,
} from '../../src/workbench/mapGeometry'
import { studyMapStyle } from '../../src/workbench/mapStyle'

vi.mock('../../src/workbench/MusicianProfile', () => ({
  MusicianProfile: ({ slug, back }: { slug: string; back: () => void }) => (
    <>
      <h2>{slug}</h2>
      <button onClick={back}>Повернутися</button>
    </>
  ),
}))
function read(path: string) {
  return JSON.parse(
    fs.readFileSync(
      new URL('../../../contracts/' + path, import.meta.url),
      'utf8',
    ),
  )
}
const data: AtlasData = {
  shapes: read('geo/regions.geojson'),
  regions: read('fixtures/map-regions.json'),
  places: read('fixtures/map-places.json'),
  pins: read('fixtures/map-pins.json').pins,
  profiles: new Set(read('fixtures/profile-slugs.json')),
}
function study(
  id: string,
  variant: string,
  onMusician?: Parameters<typeof MapStudies>[0]['onMusician'],
) {
  return render(
    <MapStudies
      data={data}
      theme="atlas"
      id={id}
      variant={variant}
      onMusician={onMusician}
    />,
  )
}
beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
  history.replaceState(null, '', '/')
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('карта України', () => {
  it('визначає регіони за межами, включно з Кримом і містами спеціального статусу', () => {
    expect(regionAt(30.5234, 50.4501, data.shapes)).toBe('KYIV_CITY')
    expect(regionAt(24.03, 49.84, data.shapes)).toBe('LVIV')
    expect(regionAt(34.1, 44.95, data.shapes)).toBe('CRIMEA')
    expect(regionAt(33.52, 44.6, data.shapes)).toBe('SEVASTOPOL_CITY')
    expect(regionAt(21.01, 52.23, data.shapes)).toBeNull()
  })
  it('вміщує країну на телефоні й комп’ютері та зберігає суму всіх кластерів', () => {
    for (const width of [360, 390, 1440]) {
      const size = { width, height: 330 }
      const box = boundsOf(data.regions)
      const cam = fit(box, size)
      for (const [lon, lat] of [
        [box[0], box[1]],
        [box[2], box[3]],
      ]) {
        const [x, y] = project([lon, lat], cam, size)
        expect(x).toBeGreaterThan(0)
        expect(x).toBeLessThan(size.width)
        expect(y).toBeGreaterThan(0)
        expect(y).toBeLessThan(size.height)
      }
    }
    const index = makeIndex(data.places, 'place')
    expect(
      index
        .getClusters([20, 42, 42, 54], 4)
        .reduce((sum, item) => sum + item.properties.count, 0),
    ).toBe(data.pins.length)
    expect(levelAt(6.99, 'REGION_PLACE_PIN')).toBe('region')
    expect(levelAt(7, 'REGION_PLACE_PIN')).toBe('place')
    expect(levelAt(10, 'REGION_PLACE_PIN')).toBe('pin')
    expect(levelAt(4, 'PLACE_PIN')).toBe('place')
  })
  it('передає дійсні стилі MapLibre з українськими підписами й власним контуром', () => {
    for (const id of ['light-minimal', 'night', 'atlas-paper']) {
      const style = studyMapStyle(id, '{{TILES_URL}}', '{{GLYPHS_URL}}')
      expect(validateStyleMin(studyMapStyle(id))).toEqual([])
      expect(style.sources.openmaptiles).toMatchObject({ url: '{{TILES_URL}}' })
      expect(JSON.stringify(style.layers)).toContain('name:uk')
      expect(style.layers.map((l) => l.id)).toContain('ua-outline-line')
      expect(validateStyleMin(studyMapStyle(id, null))).toEqual([])
    }
  })
  it('переходить від області до поселення, музиканта й профілю та назад', () => {
    study('map.aggregation', 'REGION_PLACE_PIN')
    fireEvent.click(screen.getByRole('button', { name: /^Львівська область,/ }))
    fireEvent.change(screen.getByLabelText('Населений пункт'), {
      target: { value: 'Q36036' },
    })
    const pin = data.pins.find((p) => p.slug === 'okean-elzy')!
    fireEvent.change(screen.getByLabelText('Музикант'), {
      target: { value: pin.id },
    })
    expect(screen.getByRole('heading', { name: pin.name })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Профіль музиканта' }))
    expect(screen.getByRole('heading', { name: pin.slug })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Повернутися' }))
    expect(
      (screen.getByLabelText('Музикант') as HTMLSelectElement).value,
    ).toEqual(pin.id)
  })
  it('запитує координати лише після натискання, обробляє регіон, відмову й закордон', () => {
    const get = vi.fn()
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition: get } })
    study('map.initial-view', 'UKRAINE_WITH_NEAR_ME_PROMPT')
    expect(get).not.toHaveBeenCalled()
    get.mockImplementation((ok) =>
      ok({ coords: { longitude: 24.03, latitude: 49.84 } }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Біля мене' }))
    expect(
      screen.getByRole('heading', { name: 'Львівська область' }),
    ).toBeTruthy()
    get.mockImplementation((_, fail) => fail({ code: 1 }))
    fireEvent.click(screen.getByRole('button', { name: 'Біля мене' }))
    expect(screen.getByRole('status').textContent).toMatch(/закрито/)
    get.mockImplementation((ok) =>
      ok({ coords: { longitude: 21.01, latitude: 52.23 } }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Біля мене' }))
    expect(screen.getByRole('status').textContent).toMatch(/за межами України/)
  })
  it.each(['SHEET_LIST', 'CHIPS_SCROLL', 'MAP_TAP_REGION'])(
    'відновлює, застосовує і скидає мультивибір у %s',
    (variant) => {
      history.replaceState(
        null,
        '',
        '/v/filters.region/' + variant + '?region=LVIV&identity=atlas',
      )
      study('filters.region', variant)
      fireEvent.click(
        screen.getByRole('button', {
          name:
            variant === 'CHIPS_SCROLL'
              ? 'Усі області'
              : variant === 'MAP_TAP_REGION'
                ? /Список областей/
                : /Фільтри/,
        }),
      )
      fireEvent.change(screen.getByLabelText('Пошук області'), {
        target: { value: 'Одеська' },
      })
      fireEvent.click(screen.getByRole('checkbox', { name: /Одеська область/ }))
      const count = data.regions
        .filter((r) => ['LVIV', 'ODESA'].includes(r.code))
        .reduce((n, r) => n + r.count, 0)
      fireEvent.click(screen.getByRole('button', { name: 'Показати ' + count }))
      expect(new URLSearchParams(location.search).get('region')).toBe(
        'LVIV,ODESA',
      )
      expect(screen.getByRole('heading', { name: String(count) })).toBeTruthy()
      fireEvent.click(screen.getByRole('button', { name: 'Скинути' }))
      expect(location.search).toBe('?identity=atlas')
      expect(screen.getByTestId('origin-notice')).toBeTruthy()
    },
  )
  it('майбутні критерії фільтрують конкретні піни та відновлюються з URL', () => {
    history.replaceState(null, '', '/v/filters.region/SHEET_LIST')
    study('filters.region', 'SHEET_LIST')
    fireEvent.click(screen.getByRole('button', { name: 'Фільтри' }))
    fireEvent.click(
      screen.getByRole('checkbox', { name: 'Показати майбутні фільтри' }),
    )
    fireEvent.change(screen.getByLabelText('Тип'), {
      target: { value: 'BAND' },
    })
    fireEvent.click(screen.getByRole('checkbox', { name: 'Є аудіофрагмент' }))
    const count = data.pins.filter(
      (p) => p.kind === 'BAND' && p.hasAudio,
    ).length
    fireEvent.click(screen.getByRole('button', { name: 'Показати ' + count }))
    expect(screen.getByRole('heading', { name: String(count) })).toBeTruthy()
    expect(location.search).toContain('kind=BAND')
    expect(location.search).toContain('hasAudio=true')
  })
  it('залишає постійне пояснення для доповнення в картці', () => {
    study('map.origin-notice', 'PIN_CARD_LABEL')
    expect(screen.getByTestId('origin-notice')).toBeTruthy()
    expect(screen.getByText('Місце заснування')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '⌖ Місце походження' }))
    expect(screen.getByText(/Позначка — місце походження/)).toBeTruthy()
  })
})
