import { expect, test } from '@playwright/test'
import { mapEl, settled, text } from './helpers/map'

test('?musician=<slug> flies to the pin and opens its preview', async ({ page }) => {
  await page.goto('/?musician=okean-elzy')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PIN', { timeout: 15_000 })
  const preview = page.getByTestId('musician-preview')
  await expect(preview.getByRole('heading', { name: 'Океан Ельзи' })).toBeVisible()
  await expect(preview.getByTestId('preview-origin-notice')).toBeVisible()
  await expect(page).not.toHaveURL(/musician=/)
  // the camera lands at the pin: it ends up near the centre of the map
  const pin = page.locator('[data-testid="pin"][data-slug="okean-elzy"]')
  await expect(pin).toBeVisible()
  await settled(pin)
  const map = (await mapEl(page).boundingBox())!
  const box = (await pin.boundingBox())!
  expect(Math.abs(box.x + box.width / 2 - (map.x + map.width / 2))).toBeLessThan(map.width * 0.1)
  expect(Math.abs(box.y + box.height / 2 - (map.y + map.height / 2))).toBeLessThan(map.height * 0.1)
})

test('?musician=<unknown> shows the not-found toast and keeps the overview', async ({ page }) => {
  await page.goto('/?musician=does-not-exist')
  await expect(page.getByTestId('toast')).toHaveText(text('errors.notFound'))
  await expect(page.getByTestId('musician-preview')).toHaveCount(0)
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
})
