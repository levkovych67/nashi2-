import { expect, test } from '@playwright/test'
import { activatePin, mapEl, openLvivPins, settled, text } from './helpers/map'

test('Back from a musician page returns to the same place on the map, with the preview open', async ({ page, isMobile }) => {
  await openLvivPins(page)
  const map = mapEl(page)
  const pin = page.getByTestId('pin').first()
  await settled(pin)
  const slug = (await pin.getAttribute('data-slug'))!
  await activatePin(pin, isMobile)
  const preview = page.getByTestId('musician-preview')
  await expect(preview).toBeVisible()
  const zoom = (await map.getAttribute('data-zoom'))!
  const selected = page.locator(`[data-testid="pin"][data-slug="${slug}"]`)
  const where = (await selected.boundingBox())!

  await preview.getByRole('link', { name: text('preview.openProfile') }).click()
  await expect(page).toHaveURL(new RegExp(`/m/${slug}$`))
  await expect(map).toHaveCount(0)

  await page.goBack()
  await expect(map).toHaveAttribute('data-level', 'PIN')
  await expect(map).toHaveAttribute('data-zoom', zoom)
  await expect(page.getByTestId('musician-preview')).toBeVisible()
  await expect(selected).toHaveAttribute('aria-pressed', 'true')
  await settled(selected)
  const back = (await selected.boundingBox())!
  expect(Math.abs(back.x - where.x)).toBeLessThan(2)
  expect(Math.abs(back.y - where.y)).toBeLessThan(2)
})

test('a reload keeps the camera; a fresh visit (new tab) still opens on the whole of Ukraine', async ({ page }) => {
  await openLvivPins(page)
  await page.reload()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PIN')
  const fresh = await page.context().newPage()
  await fresh.goto('/')
  await expect(mapEl(fresh)).toHaveAttribute('data-level', 'REGION')
})
