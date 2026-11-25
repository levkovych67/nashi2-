import { readFileSync } from 'node:fs'
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import {
  ArtistStudies,
  ArtistJourneyCard,
} from '../../src/workbench/ArtistStudies'
import { createLikeBatcher } from '../../src/workbench/likes'
import { formatLifeDates, groupLinks } from '../../src/workbench/profile'
import { type AtlasData, type Pin } from '../../src/workbench/data'

function read(path: string) {
  return JSON.parse(
    readFileSync(new URL('../../../contracts' + path, import.meta.url), 'utf8'),
  )
}
const data: AtlasData = {
  regions: read('/fixtures/map-regions.json'),
  places: read('/fixtures/map-places.json'),
  pins: read('/fixtures/map-pins.json').pins,
  profiles: new Set(read('/fixtures/profile-slugs.json')),
  shapes: read('/geo/regions.geojson'),
}
const ocean = data.pins.find((p) => p.slug === 'okean-elzy')!
const samples: string[] = []
class Media {
  src = ''
  currentTime = 0
  muted = false
  paused = true
  volume = 1
  play() {
    this.paused = false
    if (!this.muted) samples.push(this.src)
    return Promise.resolve()
  }
  pause() {
    this.paused = true
  }
}
beforeEach(() => {
  vi.stubGlobal('Audio', Media)
  vi.stubGlobal('fetch', async (p: string) => ({
    ok: true,
    status: 200,
    json: async () => read(p),
  }))
  samples.length = 0
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
const mount = (id: string, variant: string) =>
  render(<ArtistStudies id={id} variant={variant} data={data} theme="atlas" />)
const button = (name: string | RegExp) => screen.getByRole('button', { name })
function pin(name: string) {
  return button(new RegExp('^' + name + ','))
}

it('один дотик запускає локальний WAV, повторний ставить на паузу, а тиха позначка лишає картку без програвача', async () => {
  mount('audio.mobile-trigger', 'TAP_PREVIEW_AND_PLAY')
  fireEvent.click(pin('Океан Ельзи'))
  expect(
    (await screen.findByTestId('playing-indicator')).textContent,
  ).toContain(ocean.name)
  expect(samples.at(-1)).toMatch(/^\/fixtures\/audio\/clip-0[1-6]\.wav$/)
  fireEvent.click(pin('Океан Ельзи'))
  expect(screen.queryByTestId('playing-indicator')).toBeNull()
  expect(button(/Продовжити/)).toBeTruthy()
  fireEvent.click(pin('Один в каное'))
  expect(
    (await screen.findByTestId('playing-indicator')).textContent,
  ).toContain('Один в каное')
  fireEvent.click(pin('Формула води'))
  expect(screen.queryByTestId('playing-indicator')).toBeNull()
  expect(screen.getByText('Фрагмента немає')).toBeTruthy()
  expect(screen.queryByRole('button', { name: /Слухати фрагмент/ })).toBeNull()
  fireEvent.click(button('Закрити картку'))
  expect(screen.queryByRole('article')).toBeNull()
})
it('окрема кнопка керує паузою, продовженням та закриттям через Escape', async () => {
  mount('audio.mobile-trigger', 'TAP_PREVIEW_PLAY_BUTTON')
  fireEvent.click(pin('Океан Ельзи'))
  await waitFor(() =>
    expect((button(/Слухати фрагмент/) as HTMLButtonElement).disabled).toBe(
      false,
    ),
  )
  expect(samples).toHaveLength(0)
  fireEvent.click(button(/Слухати фрагмент/))
  await screen.findByTestId('playing-indicator')
  fireEvent.click(button(/Пауза/))
  fireEvent.click(button(/Продовжити/))
  await screen.findByTestId('playing-indicator')
  fireEvent.keyDown(pin('Океан Ельзи'), { key: 'Escape' })
  expect(screen.queryByRole('article')).toBeNull()
  expect(screen.queryByTestId('playing-indicator')).toBeNull()
})
it('наведення чекає 250 мс і вимагає жесту для увімкнення звуку', async () => {
  mount('audio.desktop-hover', 'EQUALIZER_BADGE')
  await act(async () => {})
  vi.useFakeTimers()
  const p = pin('Океан Ельзи')
  fireEvent.pointerEnter(p)
  act(() => vi.advanceTimersByTime(200))
  fireEvent.pointerLeave(p)
  act(() => vi.advanceTimersByTime(100))
  expect(screen.queryByRole('status')).toBeNull()
  fireEvent.pointerEnter(p)
  act(() => vi.advanceTimersByTime(250))
  expect(screen.getByRole('status').textContent).toMatch(/натисніть/)
  expect(samples).toHaveLength(0)
  fireEvent.pointerLeave(p)
  fireEvent.click(button('Увімкнути звук'))
  fireEvent.pointerEnter(p)
  await act(() => vi.advanceTimersByTimeAsync(250))
  expect(screen.getByTestId('playing-indicator').textContent).toContain(
    ocean.name,
  )
  expect(document.querySelector('.artist-eq')).toBeTruthy()
  fireEvent.pointerLeave(p)
  expect(screen.queryByTestId('playing-indicator')).toBeNull()
})
it('інший фрагмент використовує інший локальний кліп того самого музиканта', async () => {
  mount('audio.player', 'MINI_BAR')
  await screen.findByRole('button', { name: 'Інший фрагмент' })
  fireEvent.click(button(/Слухати фрагмент/))
  await screen.findByTestId('playing-indicator')
  const first = samples.at(-1)
  fireEvent.click(button('Інший фрагмент'))
  await waitFor(() => expect(samples.at(-1)).not.toBe(first))
  expect(document.querySelector('.artist-minibar .artist-player')).toBeTruthy()
})
it.each(['HERO', 'EDITORIAL', 'COMPACT'])(
  'обраний пін веде до профілю %s, нараховує повторні лайки і повертає на карту',
  async (variant) => {
    const back = vi.fn()
    const selected: Pin = data.pins.find((p) => p.slug === 'midnyi-papir')!
    render(
      <ArtistJourneyCard
        pin={selected}
        data={data}
        theme="atlas"
        previewVariant="BOTTOM_SHEET"
        pageVariant={variant}
        likeVariant="CUSTOM_SYMBOL"
        onBack={back}
      />,
    )
    fireEvent.click(button('Детальніше'))
    await screen.findByTestId('musician-name')
    expect(screen.getByTestId('musician-name').textContent).toBe(selected.name)
    expect(screen.queryByTestId('text-source')).toBeNull()
    expect(screen.getByTestId('musician-materials')).toBeTruthy()
    expect(screen.getByTestId('musician-origin').textContent).toMatch(/Полтава/)
    const value = () =>
      Number(screen.getByTestId('likes-count').textContent?.replace(/\D/g, ''))
    const initial = value()
    for (let i = 0; i < 30; i++)
      fireEvent.click(screen.getByTestId('like-button'))
    expect(value()).toBe(initial + 30)
    fireEvent.click(button('Повернутися на карту'))
    expect(back).toHaveBeenCalledOnce()
  },
)
it('надсилає 120 лайків трьома пакетами і повторює 429 без втрати нових дотиків', async () => {
  vi.useFakeTimers()
  const send = vi.fn(async (count: number) => ({
    ok: true as const,
    likesCount: 100 + count,
  }))
  const b = createLikeBatcher(100, send)
  for (let i = 0; i < 120; i++) b.tap()
  await vi.advanceTimersByTimeAsync(600)
  expect(send.mock.calls.map((c) => c[0])).toEqual([50, 50, 20])
  expect(b.get()).toBe(220)
  const limited = vi
    .fn()
    .mockResolvedValueOnce({ ok: false, retryAfterSec: 2 })
    .mockResolvedValue({ ok: true, likesCount: 125 })
  const c = createLikeBatcher(100, limited)
  for (let i = 0; i < 20; i++) c.tap()
  await vi.advanceTimersByTimeAsync(600)
  for (let i = 0; i < 5; i++) c.tap()
  await vi.advanceTimersByTimeAsync(2000)
  expect(limited.mock.calls.map((c) => c[0])).toEqual([20, 25])
  expect(c.get()).toBe(125)
})
it('форматує часткові дати та групує дійсні музичні посилання першими', () => {
  expect(
    formatLifeDates(
      {
        birth: '1872-09-23',
        death: '1952-11-16',
        activeFrom: null,
        activeTo: null,
      },
      'PERSON',
    ),
  ).toBe('23 вересня 1872 — 16 листопада 1952')
  expect(
    formatLifeDates(
      { birth: '1872-09', death: null, activeFrom: null, activeTo: null },
      'PERSON',
    ),
  ).toBe('нар. вересень 1872')
  expect(
    groupLinks([
      { group: 'SOCIAL', platform: 'X', url: 'javascript:alert(1)' },
      {
        group: 'REFERENCE',
        platform: 'WIKIPEDIA',
        url: 'https://uk.wikipedia.org/',
      },
      { group: 'MUSIC', platform: 'SPOTIFY', url: 'https://open.spotify.com/' },
    ]).map((g) => g.group),
  ).toEqual(['MUSIC', 'REFERENCE'])
})

it('утримання 400 мс запускає звук без відкриття картки, рух пальця скасовує жест', async () => {
  class Pointer extends MouseEvent {
    readonly pointerType = 'touch'
    readonly pointerId = 1
    readonly isPrimary = true
  }
  vi.stubGlobal('PointerEvent', Pointer)
  mount('audio.mobile-trigger', 'LONG_PRESS_PLAY')
  await act(async () => {})
  vi.useFakeTimers()
  const target = pin('Океан Ельзи')
  fireEvent.pointerDown(target, { clientX: 10, clientY: 10, button: 0 })
  act(() => vi.advanceTimersByTime(200))
  fireEvent.pointerMove(target, { clientX: 24, clientY: 10 })
  act(() => vi.advanceTimersByTime(300))
  fireEvent.pointerUp(target)
  fireEvent.click(target)
  expect(screen.queryByRole('article')).toBeNull()
  expect(samples).toHaveLength(0)
  fireEvent.pointerDown(target, { clientX: 8, clientY: 8, button: 0 })
  act(() => vi.advanceTimersByTime(399))
  expect(samples).toHaveLength(0)
  await act(() => vi.advanceTimersByTimeAsync(1))
  fireEvent.pointerUp(target)
  fireEvent.click(target)
  expect(screen.getByTestId('playing-indicator')).toBeTruthy()
  expect(screen.queryByRole('article')).toBeNull()
  fireEvent.pointerDown(target, { clientX: 8, clientY: 8, button: 0 })
  fireEvent.pointerUp(target)
  fireEvent.click(target)
  expect(screen.getByRole('article')).toBeTruthy()
  expect(screen.queryByTestId('playing-indicator')).toBeNull()
})
it('шторка розгортається свайпом, закривається жестом вниз та дотиком до карти', async () => {
  class Pointer extends MouseEvent {
    readonly pointerId = 1
  }
  vi.stubGlobal('PointerEvent', Pointer)
  mount('musician.preview', 'BOTTOM_SHEET')
  await act(async () => {})
  const handle = button('Розгорнути картку')
  fireEvent.pointerDown(handle, { clientY: 90 })
  fireEvent.pointerUp(handle, { clientY: 15 })
  expect(button('Згорнути картку')).toBeTruthy()
  fireEvent.pointerDown(handle, { clientY: 20 })
  fireEvent.pointerUp(handle, { clientY: 100 })
  expect(screen.queryByRole('article')).toBeNull()
  fireEvent.click(pin('Океан Ельзи'))
  fireEvent.click(document.querySelector('.artist-map-fallback')!)
  expect(screen.queryByRole('article')).toBeNull()
})

it('блокування браузером допускає повторний старт, запізніла помилка попереднього піна не зупиняє новий', async () => {
  mount('audio.mobile-trigger', 'TAP_PREVIEW_AND_PLAY')
  await act(async () => {})
  const play = vi
    .spyOn(Media.prototype, 'play')
    .mockRejectedValueOnce(
      new DOMException('Gesture required', 'NotAllowedError'),
    )
  fireEvent.click(pin('Океан Ельзи'))
  expect((await screen.findByRole('status')).textContent).toMatch(
    /Натисніть кнопку/,
  )
  await act(async () => {
    fireEvent.click(button(/Слухати фрагмент/))
  })
  expect(screen.getByTestId('playing-indicator')).toBeTruthy()
  fireEvent.click(button(/Пауза/))
  let reject!: (reason: Error) => void
  play.mockImplementationOnce(
    () =>
      new Promise<void>((_, fail) => {
        reject = fail
      }),
  )
  fireEvent.click(pin('Океан Ельзи'))
  fireEvent.click(pin('Один в каное'))
  await act(async () => {
    reject(new Error('obsolete'))
  })
  expect(screen.getByTestId('playing-indicator').textContent).toContain(
    'Один в каное',
  )
  expect(screen.queryByRole('status')).toBeNull()
  play.mockRestore()
})
it('тихе згортання вкладки відправляє накопичені лайки з keepalive і не зменшує показаний лічильник', async () => {
  const send = vi.fn().mockResolvedValue({ ok: true, likesCount: 1 })
  const batch = createLikeBatcher(100, send)
  batch.tap()
  batch.tap()
  await batch.flush(true)
  expect(send).toHaveBeenCalledWith(2, { keepalive: true })
  expect(batch.get()).toBe(102)
})
