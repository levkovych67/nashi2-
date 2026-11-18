import { expect, test } from '@playwright/test'

test('the studio opens in Ukrainian with indexing disabled', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle('Студія «Наші V2»')
  await expect(page.locator('html')).toHaveAttribute('lang', 'uk')
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex')
  await expect(page.getByRole('heading', { level: 1, name: 'Студія «Наші V2»' })).toBeVisible()
  await expect(page.getByTestId('studio-chrome')).toBeVisible()
})

test('the fullscreen variant works on a direct visit without studio chrome', async ({ page }) => {
  const response = await page.goto('/v/map.pin/AVATAR_RING?identity=atlas')
  expect(response?.ok()).toBe(true)
  await expect(page.getByTestId('studio-chrome')).toHaveCount(0)
  await expect(page.getByTestId('origin-notice')).toBeVisible()
})
