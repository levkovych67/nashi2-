import { expect, test } from '@playwright/test'
import { mapEl, text } from './helpers/map'

const zoomOf = async (map: ReturnType<typeof mapEl>) => Number(await map.getAttribute('data-zoom'))

test('the + and − buttons zoom the map without gestures, down to the next level and back', async ({ page, isMobile }) => {
  await page.goto('/')
  const map = mapEl(page)
  await expect(map).toHaveAttribute('data-level', 'REGION')
  await expect(map).toHaveAttribute('data-zoom', /\d/)
  const zoomIn = page.getByRole('button', { name: text('map.zoomIn') })
  const zoomOut = page.getByRole('button', { name: text('map.zoomOut') })
  if (isMobile) {
    for (const button of [zoomIn, zoomOut]) {
      const box = (await button.boundingBox())!
      expect(box.width).toBeGreaterThanOrEqual(44)
      expect(box.height).toBeGreaterThanOrEqual(44)
    }
  }

  const start = await zoomOf(map)
  await zoomIn.click()
  await expect.poll(() => zoomOf(map)).toBeCloseTo(start + 1, 1)
  // + until the places show (regionMaxZoom 7)
  await expect(async () => {
    if ((await map.getAttribute('data-level')) !== 'PLACE') await zoomIn.click()
    await expect(map).toHaveAttribute('data-level', 'PLACE', { timeout: 2_000 })
  }).toPass({ timeout: 15_000 })

  // − back to the regions, and on to the minimum zoom, where − is disabled
  await expect(async () => {
    if (await zoomOut.isEnabled()) await zoomOut.click()
    await expect(zoomOut).toBeDisabled({ timeout: 2_000 })
  }).toPass({ timeout: 20_000 })
  await expect(map).toHaveAttribute('data-level', 'REGION')
  await expect(zoomIn).toBeEnabled()
})
