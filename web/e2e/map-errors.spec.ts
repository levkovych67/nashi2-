import { expect, test, type Page } from '@playwright/test'
import { mapEl, text } from './helpers/map'

// The mock API answers the map endpoints with an error while window.__nashiMockFail names them (see mocks/handlers.ts).
const failWith = (page: Page, fail: Record<string, number | 'network'>) =>
  page.addInitScript((f) => (window.__nashiMockFail = f), fail)
const recover = (page: Page) => page.evaluate(() => delete window.__nashiMockFail)

test('API down: the map says so instead of staying empty, and retry loads it', async ({ page }) => {
  await failWith(page, { '/map/pins': 'network', '/map/regions': 'network', '/map/places': 'network', '/filters': 'network', '/config/public': 'network' })
  await page.goto('/')
  const alert = page.getByTestId('map-error')
  await expect(alert).toContainText(text('errors.network'), { timeout: 15_000 })
  await expect(page.getByTestId('region-bubble')).toHaveCount(0)

  await recover(page)
  await alert.getByRole('button', { name: text('errors.retry') }).click()
  await expect(alert).toBeHidden()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect.poll(() => page.getByTestId('region-bubble').count()).toBeGreaterThanOrEqual(20)
  await expect(page.getByTestId('results-count')).toBeVisible()
})

test('a server error on the pins keeps the regions on the map and offers a retry', async ({ page }) => {
  await failWith(page, { '/map/pins': 500 })
  await page.goto('/')
  const alert = page.getByTestId('map-error')
  await expect(alert).toContainText(text('errors.generic'), { timeout: 15_000 })
  await expect.poll(() => page.getByTestId('region-bubble').count()).toBeGreaterThanOrEqual(20)
  await expect(page.getByTestId('results-count')).toHaveCount(0)

  await recover(page)
  await page.getByTestId('map-retry').click()
  await expect(alert).toBeHidden()
  await expect(page.getByTestId('results-count')).toBeVisible()
})
