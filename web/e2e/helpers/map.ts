import { expect, type Locator, type Page } from '@playwright/test'
import copy from '../../src/design/copy.uk.json' with { type: 'json' }

export const originNoticeText = copy['map.originNotice']

export const mapEl = (page: Page) => page.getByTestId('map')

/** Accessible name of a bubble is `cluster.ariaLabel`: "<name>, музикантів: <count>". */
export const bubble = (page: Page, name: string) =>
  page.getByRole('button', { name: new RegExp(`^${name}, `) }).first()

/**
 * Taps the bubble named `name` until the map reaches `level`. With the fixtures a city first sits inside a cluster
 * named after it, and each tap on a cluster zooms one expansion step closer.
 */
export async function drillInto(page: Page, name: string, level: 'PLACE' | 'PIN') {
  const map = mapEl(page)
  await expect(async () => {
    if ((await map.getAttribute('data-level')) !== level) await bubble(page, name).click({ timeout: 3_000 })
    await expect(map).toHaveAttribute('data-level', level, { timeout: 3_000 })
  }).toPass({ timeout: 20_000 })
}

/** Ukraine → Lviv oblast → the city of Lviv at pin level. */
export async function openLvivPins(page: Page) {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await bubble(page, 'Львівська область').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  await drillInto(page, 'Львів', 'PIN')
  await expect(page.getByTestId('pin').first()).toBeVisible()
}

/** Phones tap (touch → audio gesture), desktops click with the mouse (hover plays, click opens the preview). */
export async function activatePin(pin: Locator, isMobile: boolean) {
  if (isMobile) await pin.tap()
  else await pin.click()
}
