import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import { NavigationStudy } from '../../src/workbench/NavigationStudy'
import { shouldShowOnboarding } from '../../src/proto/onboarding'
import type { AtlasData } from '../../src/workbench/data'
import { sendSelectionToTeam } from '../../src/selection/webhook'

const data: AtlasData = {
  regions: [],
  places: [],
  pins: [
    {
      id: 'band',
      slug: 'band',
      name: 'Океан Ельзи',
      kind: 'BAND',
      region: 'LVIV',
      placeId: 'Q36036',
      lat: 49.8,
      lon: 24,
      hasAudio: true,
    },
  ],
  profiles: new Set(),
  shapes: { type: 'FeatureCollection', features: [] },
}
afterEach(() => {
  cleanup()
  localStorage.clear()
})

it('keeps first-visit tips dismissed after remount and lets the visitor replay them', () => {
  expect(shouldShowOnboarding(null)).toBe(false)
  const props = { id: 'onboarding', variant: 'FIRST_VISIT_TIPS', data }
  const first = render(<NavigationStudy {...props} />)
  expect(screen.getByRole('heading', { name: 'Що показує карта' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Далі' }))
  expect(screen.getByRole('heading', { name: 'Як слухати' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Пропустити' }))
  first.unmount()
  render(<NavigationStudy {...props} />)
  expect(screen.queryByRole('heading', { name: 'Що показує карта' })).toBeNull()
  fireEvent.click(
    screen.getByRole('button', { name: 'Показати підказки ще раз' }),
  )
  expect(screen.getByRole('heading', { name: 'Що показує карта' })).toBeTruthy()
})
for (const variant of ['MAP_ONLY_SHEETS', 'TAB_BAR', 'TOP_BAR_MENU'])
  it(
    'reaches a searched musician and returns to the map through ' + variant,
    () => {
      render(
        <NavigationStudy id="nav.structure" variant={variant} data={data} />,
      )
      if (variant === 'TOP_BAR_MENU')
        fireEvent.click(screen.getByRole('button', { name: 'Меню' }))
      fireEvent.click(
        screen.getByRole('button', {
          name: variant === 'MAP_ONLY_SHEETS' ? 'Пошук і фільтри' : 'Список',
        }),
      )
      fireEvent.change(screen.getByRole('searchbox'), {
        target: { value: 'океан' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Океан Ельзи' }))
      expect(screen.getByRole('heading', { name: 'Океан Ельзи' })).toBeTruthy()
      fireEvent.click(
        screen.getByRole('button', {
          name: variant === 'MAP_ONLY_SHEETS' ? 'Закрити шторку' : 'До карти',
        }),
      )
      expect(screen.queryByRole('heading', { name: 'Список' })).toBeNull()
    },
  )
it('keeps export local without a configured endpoint and reports a failed send', async () => {
  expect(await sendSelectionToTeam(undefined, {})).toBe('disabled')
  expect(
    await sendSelectionToTeam('/example', {}, async () => {
      throw Error('offline')
    }),
  ).toBe('failed')
})
