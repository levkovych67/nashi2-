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

test.describe('with reduced motion (the camera jumps instead of flying)', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } })

  test('?musician=<slug> is handled once: no render loop on a slow device', async ({ page }) => {
    const loops: string[] = []
    page.on('console', (m) => m.type() === 'error' && /Maximum update depth/.test(m.text()) && loops.push(m.text()))
    const cdp = await page.context().newCDPSession(page)
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 })
    await page.goto('/?musician=okean-elzy')
    await expect(page.getByTestId('musician-preview')).toBeVisible({ timeout: 30_000 })
    await expect(page).not.toHaveURL(/musician=/)
    await expect(mapEl(page)).toHaveAttribute('data-level', 'PIN')
    // the page still answers (a loop would keep the main thread busy) and logged no loop on the way
    expect(await page.evaluate(() => document.readyState)).toBe('complete')
    expect(loops).toEqual([])
  })
})

test('?musician=<unknown> shows the not-found toast and keeps the overview', async ({ page }) => {
  await page.goto('/?musician=does-not-exist')
  await expect(page.getByTestId('toast')).toHaveText(text('errors.notFound'))
  await expect(page.getByTestId('musician-preview')).toHaveCount(0)
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
})
