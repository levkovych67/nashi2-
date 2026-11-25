import { expect, test } from '@playwright/test'
import { mapEl, text } from './helpers/map'

test('?musician=<slug> flies to the pin and opens its preview', async ({ page }) => {
  await page.goto('/?musician=okean-elzy')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PIN', { timeout: 15_000 })
  const preview = page.getByTestId('musician-preview')
  await expect(preview.getByRole('heading', { name: 'Океан Ельзи' })).toBeVisible()
  await expect(preview.getByTestId('preview-origin-notice')).toBeVisible()
  await expect(page).not.toHaveURL(/musician=/)
  await expect(page.locator('[data-testid="pin"][data-slug="okean-elzy"]')).toBeVisible()
})

test('?musician=<unknown> shows the not-found toast and keeps the overview', async ({ page }) => {
  await page.goto('/?musician=does-not-exist')
  await expect(page.getByTestId('toast')).toHaveText(text('errors.notFound'))
  await expect(page.getByTestId('musician-preview')).toHaveCount(0)
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
})
