import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { SubmissionStudy } from '../../src/workbench/SubmissionStudy'
import type { AtlasData } from '../../src/workbench/data'

vi.mock('../../src/workbench/submission', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('../../src/workbench/submission')>()
  return { ...actual, downscaleImage: async (file: File) => file }
})
const data: AtlasData = {
  places: [
    {
      placeId: 'Q36036',
      name: 'Львів',
      region: 'LVIV',
      count: 12,
      lat: 49,
      lon: 24,
    },
  ],
  regions: [
    {
      code: 'LVIV',
      name: 'Львівська область',
      center: [24, 49],
      bbox: [22, 48, 25, 50],
      count: 30,
    },
  ],
  shapes: { type: 'FeatureCollection', features: [] },
  pins: [],
  profiles: new Set(),
}
const jpeg = (name: string) => new File(['photo'], name, { type: 'image/jpeg' })
beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => {
      throw new Error('Анкета має залишатися локально')
    }),
  )
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL() {
        return 'blob:local-photo'
      }
      static revokeObjectURL() {}
    },
  )
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function fill(label: RegExp, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}
function click(name: string) {
  fireEvent.click(screen.getByRole('button', { name }))
}
function selectOrigin() {
  const input = screen.getByRole('combobox', { name: 'Місто походження' })
  fireEvent.change(input, { target: { value: 'льв' } })
  expect(
    screen.getByRole('option', { name: /Львів.*Львівська область/ }),
  ).toBeTruthy()
  fireEvent.keyDown(input, { key: 'ArrowDown' })
  fireEvent.keyDown(input, { key: 'Enter' })
}
describe('локальна форма пропозиції', () => {
  it('повідомляє про помилки на blur та фокусує перше неправильне поле', () => {
    render(<SubmissionStudy variant="SINGLE_PAGE" data={data} />)
    const name = screen.getByLabelText('Ім’я або назва гурту')
    fireEvent.blur(name)
    expect(name.getAttribute('aria-invalid')).toBe('true')
    click('Надіслати пропозицію')
    expect(document.activeElement).toBe(name)
    expect(
      document.querySelectorAll('[aria-invalid="true"]').length,
    ).toBeGreaterThanOrEqual(5)
    expect(
      document
        .querySelector('input[name="faxNumber"]')
        ?.getAttribute('tabindex'),
    ).toBe('-1')
    const origin = screen.getByLabelText('Місто походження')
    fireEvent.change(origin, { target: { value: 'Невибране місто' } })
    fireEvent.blur(origin)
    expect(origin.getAttribute('aria-invalid')).toBe('true')
  })
  it('перевіряє кількість і MIME, зберігає порядок і дозволяє змінити фото', async () => {
    render(<SubmissionStudy variant="SINGLE_PAGE" data={data} />)
    const input = screen.getByLabelText('Додати фото')
    fireEvent.change(input, { target: { files: Array(6).fill(jpeg('x.jpg')) } })
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      expect.stringContaining('5 фото'),
    )
    fireEvent.change(input, {
      target: {
        files: [
          new File(['x'], 'bad.gif', { type: 'image/gif' }),
          jpeg('а.jpg'),
          jpeg('б.jpg'),
        ],
      },
    })
    expect(await screen.findByText('2. б.jpg')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toMatch(/JPEG, PNG або WebP/)
    click('Перемістити раніше б.jpg')
    expect(screen.getByText('1. б.jpg')).toBeTruthy()
    click('Видалити фото а.jpg')
    expect(screen.queryByText(/2\. а.jpg/)).toBeNull()
  })
  it.each(['SINGLE_PAGE', 'WIZARD'])(
    'зберігає дані після 413 й завершує %s повторним надсиланням',
    async (variant) => {
      const wizard = variant === 'WIZARD'
      render(<SubmissionStudy variant={variant} data={data} />)
      fireEvent.change(screen.getByLabelText('Наступна відповідь демо'), {
        target: { value: 'PAYLOAD_TOO_LARGE' },
      })
      fill(/Ім’я або назва/, 'Польові записи')
      fireEvent.click(screen.getByRole('radio', { name: 'Гурт' }))
      if (wizard) click('Далі')
      expect(screen.queryByLabelText(/Місце народження/)).toBeNull()
      selectOrigin()
      if (wizard) {
        click('Назад')
        expect(screen.getByLabelText(/Ім’я або назва/)).toHaveProperty(
          'value',
          'Польові записи',
        )
        click('Далі')
        click('Далі')
      }
      fill(
        /Короткий опис/,
        'Український гурт, що записує власні пісні та польові звуки міста.',
      )
      if (wizard) click('Далі')
      fill(/Посилання на виконавця/, 'https://open.spotify.com/artist/1')
      expect(screen.getByText('Spotify')).toBeTruthy()
      if (wizard) click('Далі')
      fireEvent.change(screen.getByLabelText('Додати фото'), {
        target: { files: [jpeg('гурт.jpg')] },
      })
      expect(await screen.findByText('1. гурт.jpg')).toBeTruthy()
      if (wizard) click('Далі')
      fireEvent.click(screen.getByRole('checkbox'))
      click('Надіслати пропозицію')
      expect(screen.getByRole('progressbar')).toBeTruthy()
      expect(
        await screen.findByRole('alert', {}, { timeout: 2800 }),
      ).toHaveProperty('textContent', expect.stringContaining('413'))
      expect(screen.getByRole('checkbox')).toHaveProperty('checked', true)
      if (wizard) expect(screen.getByText('гурт.jpg')).toBeTruthy()
      else
        expect(screen.getByLabelText(/Ім’я або назва/)).toHaveProperty(
          'value',
          'Польові записи',
        )
      click('Повторити надсилання')
      expect(
        await screen.findByRole(
          'heading',
          { name: 'Пропозицію прийнято' },
          { timeout: 2800 },
        ),
      ).toBe(document.activeElement)
      expect(fetch).not.toHaveBeenCalled()
      click('Запропонувати ще одного')
      await waitFor(() =>
        expect(screen.getByLabelText(/Ім’я або назва/)).toHaveProperty(
          'value',
          '',
        ),
      )
    },
  )
})
