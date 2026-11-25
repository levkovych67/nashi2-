import { expect, test } from '@playwright/test'
import { activatePin, bubble, drillInto, mapEl, openLvivPins } from './helpers/map'

test('opens on all of Ukraine with region bubbles and origin notice', async ({ page }) => {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  const regions = page.getByTestId('region-bubble')
  await expect.poll(() => regions.count()).toBeGreaterThanOrEqual(20)
  // the whole country fits the screen: every region bubble is inside the viewport
  for (const region of await regions.all()) await expect(region).toBeInViewport()
  await expect(page.getByTestId('origin-notice')).toBeVisible()
})

test('drills down region → place → pins', async ({ page }) => {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await bubble(page, 'Львівська область').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  await expect(page.getByTestId('region-bubble')).toHaveCount(0)
  await drillInto(page, 'Львів', 'PIN')
  const pin = page.getByTestId('pin').first()
  await expect(pin).toBeVisible()
  await expect(pin).toHaveAttribute('data-slug', /.+/)
  await expect(pin).toHaveAttribute('data-has-audio', /^(true|false)$/)
  await expect(pin).toHaveAccessibleName(/.+, .+/)

  await page.getByTestId('back-to-ukraine').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect.poll(() => page.getByTestId('region-bubble').count()).toBeGreaterThanOrEqual(20)
})

test('deep link ?region=LVIV opens the region', async ({ page }) => {
  await page.goto('/?region=LVIV')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  await expect(bubble(page, 'Львів')).toBeInViewport()
  await expect(page).toHaveURL(/[?&]region=LVIV\b/)
})

test('tapping a pin opens the musician preview', async ({ page, isMobile }) => {
  await openLvivPins(page)
  const pin = page.getByTestId('pin').first()
  const name = (await pin.getAttribute('aria-label'))!.split(', ')[0]
  await activatePin(pin, isMobile)
  await expect(pin).toHaveAttribute('aria-pressed', 'true')
  const preview = page.getByTestId('musician-preview')
  await expect(preview).toBeVisible()
  await expect(preview).toContainText(name)
  await page.keyboard.press('Escape')
  await expect(preview).toBeHidden()
})

test('touch targets are at least 44px on mobile', async ({ page, isMobile }) => {
  test.skip(!isMobile, 'touch targets are a phone requirement')
  await page.goto('/')
  const region = page.getByTestId('region-bubble').first()
  await expect(region).toBeVisible()
  for (const id of ['filters-open', 'near-me', 'sound-toggle']) {
    const box = (await page.getByTestId(id).boundingBox())!
    expect(box.width, id).toBeGreaterThanOrEqual(44)
    expect(box.height, id).toBeGreaterThanOrEqual(44)
  }
  const regionBox = (await region.boundingBox())!
  expect(regionBox.width).toBeGreaterThanOrEqual(44)
  expect(regionBox.height).toBeGreaterThanOrEqual(44)

  await bubble(page, 'Львівська область').click()
  await drillInto(page, 'Львів', 'PIN')
  const pin = page.getByTestId('pin').first()
  await expect(pin).toBeVisible()
  const pinBox = (await pin.boundingBox())!
  expect(pinBox.width).toBeGreaterThanOrEqual(44)
  expect(pinBox.height).toBeGreaterThanOrEqual(44)
})
