import { readFile } from 'node:fs/promises'
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { FlowPage } from '../../src/pages/FlowPage'
import { scenarios } from '../../src/flows/scenarios'
import { choose, reset } from '../../src/selection/store'

const choices: Record<string, string>[] = [
  {},
  {
    'map.aggregation': 'PLACE_PIN',
    'map.pin': 'DROP_ICON',
    'audio.mobile-trigger': 'TAP_PREVIEW_PLAY_BUTTON',
    'musician.preview': 'FLOATING_CARD',
    'musician.page': 'EDITORIAL',
    'filters.region': 'CHIPS_SCROLL',
    'submission.form': 'WIZARD',
    'nav.structure': 'TAB_BAR',
  },
  {
    'map.aggregation': 'CHOROPLETH_PLACE_PIN',
    'map.pin': 'DOT_LABEL',
    'audio.mobile-trigger': 'LONG_PRESS_PLAY',
    'musician.preview': 'BOTTOM_SHEET',
    'musician.page': 'COMPACT',
    'filters.region': 'MAP_TAP_REGION',
    'submission.form': 'SINGLE_PAGE',
    'nav.structure': 'TOP_BAR_MENU',
  },
]
const next = () => fireEvent.click(screen.getByTestId('flow-next'))
const step = () => Number(screen.getByTestId('flow-progress').dataset.step)
function mount(id: string) {
  return render(
    <MemoryRouter initialEntries={['/flow/' + id]}>
      <Routes>
        <Route path="/flow/:scenarioId" element={<FlowPage />} />
      </Routes>
    </MemoryRouter>,
  )
}
class Media {
  src = ''
  currentTime = 0
  volume = 1
  muted = false
  paused = true
  play() {
    this.paused = false
    return Promise.resolve()
  }
  pause() {
    this.paused = true
  }
}
beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
  vi.stubGlobal('Audio', Media)
  vi.stubGlobal('fetch', async (path: string) => ({
    ok: true,
    json: async () =>
      JSON.parse(
        await readFile(
          new URL('../../../contracts' + path, import.meta.url),
          'utf8',
        ),
      ),
  }))
  HTMLElement.prototype.scrollIntoView = vi.fn()
  reset()
})
afterEach(() => {
  cleanup()
  reset()
  vi.unstubAllGlobals()
})
it('визначає десять кроків карти та шість кроків заявки', () => {
  expect(scenarios['map-to-musician'].steps).toHaveLength(10)
  expect(scenarios['suggest-musician'].steps).toHaveLength(6)
})
it.each(choices.map((c, i) => [i, c] as const))(
  'проходить 10 станів для набору %i, діє на справжній профіль та повертає камеру міста',
  async (_, c) => {
    for (const [id, v] of Object.entries(c)) choose(id, v)
    mount('map-to-musician')
    await screen.findByTestId('flow-progress')
    expect(step()).toBe(1)
    for (let i = 2; i <= 6; i++) {
      next()
      expect(step()).toBe(i)
    }
    expect(
      document.querySelector('.journey-gesture')?.getAttribute('data-trigger'),
    ).toBe(c['audio.mobile-trigger'] || 'TAP_PREVIEW_AND_PLAY')
    await act(async () => {
      next()
    })
    expect(step()).toBe(7)
    expect(screen.getByRole('article').className).toContain(
      c['musician.preview'] || 'BOTTOM_SHEET',
    )
    next()
    expect(step()).toBe(8)
    await screen.findByTestId('musician-name')
    expect(document.querySelector('.artist-profile')?.className).toContain(
      c['musician.page'] || 'HERO',
    )
    const initial = screen.getByTestId('likes-count').textContent
    fireEvent.click(screen.getByTestId('like-button'))
    expect(step()).toBe(9)
    expect(screen.getByTestId('likes-count').textContent).not.toBe(initial)
    next()
    expect(step()).toBe(10)
    expect(screen.getByLabelText('Музикант')).toBeTruthy()
    expect(screen.getByTestId('flow-next').hasAttribute('disabled')).toBe(true)
    fireEvent.click(screen.getByTestId('flow-back'))
    expect(step()).toBe(9)
  },
)
it('перший крок заявки використовує вибрану навігацію, далі валідація форми не дозволяє завершити порожню заявку', async () => {
  choose('nav.structure', 'TAB_BAR')
  choose('submission.form', 'WIZARD')
  mount('suggest-musician')
  await screen.findByTestId('flow-progress')
  const nav = screen.getByRole('navigation', { name: 'Вкладки' })
  fireEvent.click(within(nav).getByRole('button', { name: 'Запропонувати' }))
  expect(step()).toBe(2)
  expect(screen.getByLabelText('Ім’я або назва гурту')).toBeTruthy()
  next()
  await waitFor(() =>
    expect(screen.getByTestId('flow-progress').textContent).toMatch(/Виправте/),
  )
  expect(step()).toBe(2)
  fireEvent.change(screen.getByLabelText('Ім’я або назва гурту'), {
    target: { value: 'Гурт для тесту' },
  })
  next()
  await waitFor(() => expect(step()).toBe(3))
  expect(
    screen.getByRole('combobox', { name: 'Місто походження' }),
  ).toBeTruthy()
})
