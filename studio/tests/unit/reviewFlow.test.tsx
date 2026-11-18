import { readFile } from 'node:fs/promises'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Comparison, Fullscreen, Overview } from '../../src/workbench/Workbench'
import { decodeSelection, STORAGE_KEY } from '../../src/selection/selection'
import { reset } from '../../src/selection/store'

class ResizeStub {
  observe() {}
  disconnect() {}
}
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', ResizeStub)
  vi.stubGlobal('fetch', async (path: string) => {
    const text = await readFile(
      new URL('../../../contracts' + path, import.meta.url),
      'utf8',
    )
    return { ok: true, json: () => JSON.parse(text) }
  })
  reset()
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
describe('перегляд та фіксація дизайну', () => {
  it('відкриває чотири напрями, зберігає вибір і дає QR саме для нього', async () => {
    render(
      <MemoryRouter initialEntries={['/d/identity']}>
        <Routes>
          <Route path="/d/:decisionId" element={<Comparison />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findAllByText('Музиканти на карті')
    expect(screen.getAllByRole('article')).toHaveLength(4)
    const atlas = within(screen.getAllByRole('article')[0])
    fireEvent.click(atlas.getByRole('button', { name: 'Обрати' }))
    expect(
      decodeSelection(localStorage.getItem(STORAGE_KEY)).draft.identity.variant,
    ).toBe('atlas')
    fireEvent.click(atlas.getByRole('button', { name: 'QR-код' }))
    expect((await atlas.findByAltText(/QR-код/)).getAttribute('src')).toMatch(
      /^data:image\/svg/,
    )
    expect(atlas.getByText('На весь екран').getAttribute('href')).toMatch(
      /\/v\/identity\/atlas\?identity=atlas$/,
    )
    fireEvent.click(atlas.getByRole('button', { name: /Профіль гурту/ }))
    expect(
      await atlas.findByRole('heading', { name: 'Океан Ельзи' }),
    ).toBeTruthy()
    fireEvent.click(atlas.getByRole('button', { name: /Повернутися на карту/ }))
    expect(
      atlas.getByRole('heading', { name: 'Музиканти на карті' }),
    ).toBeTruthy()
  })
  it('залишає геомітку й фільтр доступними в повноекранному варіанті', async () => {
    render(
      <MemoryRouter
        initialEntries={[
          '/v/map.cluster/BUBBLE_COUNT_LABEL?identity=nichna-karta',
        ]}
      >
        <Routes>
          <Route path="/v/:decisionId/:variantId" element={<Fullscreen />} />
        </Routes>
      </MemoryRouter>,
    )
    const select = await screen.findByRole('combobox')
    expect(select.querySelectorAll('option')).toHaveLength(28)
    fireEvent.change(select, { target: { value: 'SEVASTOPOL_CITY' } })
    expect(screen.getByRole('heading', { name: 'Севастополь' })).toBeTruthy()
    expect(screen.getByTestId('origin-notice')).toBeTruthy()
    expect(
      document.querySelector('[data-direction="nichna-karta"]'),
    ).toBeTruthy()
  })
  it('відкриває живі варіанти онбордингу і витримує пошкоджений маршрут', async () => {
    const app = render(
      <MemoryRouter initialEntries={['/d/onboarding']}>
        <Routes>
          <Route path="/d/:decisionId" element={<Comparison />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(
      await screen.findByRole('heading', { name: 'Три короткі підказки' }),
    ).toBeTruthy()
    app.unmount()
    render(
      <MemoryRouter initialEntries={['/v/constructor/bad']}>
        <Routes>
          <Route path="/v/:decisionId/:variantId" element={<Fullscreen />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(
      screen.getByRole('heading', { name: 'Варіант не знайдено' }),
    ).toBeTruthy()
  })
  it('групує рішення каталогу й показує реальну географію', async () => {
    render(
      <MemoryRouter>
        <Overview />
      </MemoryRouter>,
    )
    await screen.findByRole('img', { name: /Музична карта України з Кримом/ })
    fireEvent.click(screen.getByRole('button', { name: 'Звук' }))
    expect(screen.getByRole('link', { name: /Керування звуком/ })).toBeTruthy()
    expect(
      screen.queryByRole('link', { name: /Позначка музиканта/ }),
    ).toBeNull()
  })
})
