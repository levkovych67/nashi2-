import { expect, test } from '@playwright/test'
import { bubble, mapEl, text } from './helpers/map'

test.describe('with a position in Lviv', () => {
  test.use({ geolocation: { latitude: 49.84, longitude: 24.03 }, permissions: ['geolocation'] })

  test('near me flies to the user region without writing ?region', async ({ page }) => {
    await page.goto('/')
    await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
    await page.getByTestId('near-me').click()
    await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
    await expect(bubble(page, 'Львів')).toBeVisible()
    expect(new URL(page.url()).searchParams.has('region')).toBe(false)
  })
})

test.describe('with a position outside Ukraine', () => {
  test.use({ geolocation: { latitude: 51.5, longitude: -0.12 }, permissions: ['geolocation'] })

  test('shows a toast and leaves the map alone', async ({ page }) => {
    await page.goto('/')
    await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
    await page.getByTestId('near-me').click()
    await expect(page.getByTestId('toast')).toHaveText(text('map.nearMeDenied'))
    await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  })
})

test.describe('without permission', () => {
  test.use({ permissions: [] })

  test('denied: toast, and the map stays usable', async ({ page }) => {
    await page.goto('/')
    await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
    await page.getByTestId('near-me').click()
    await expect(page.getByTestId('toast')).toHaveText(text('map.nearMeDenied'))
    await bubble(page, 'Львівська область').click()
    await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  })
})
