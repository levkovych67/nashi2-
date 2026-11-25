import { expect, test } from '@playwright/test'
import { useRegionPlaceDefaults, bubble, mapEl, text } from './helpers/map'

// What the map does with the customer's approved decisions (decisions.json: all 17 APPROVED on 2026-11-25), and, via an
// injected override, with the core's plain defaults that the approval replaced.

test('approved CHOROPLETH_PLACE_PIN: numerals and the «Менше — Більше» key at region level only', async ({ page }) => {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  const legend = page.getByTestId('choropleth-legend')
  await expect(legend).toHaveText(`${text('map.choroplethLess')}${text('map.choroplethMore')}`)
  await expect(bubble(page, 'Львівська область').locator('.n-bubble--numeral')).toBeVisible()

  await bubble(page, 'Львівська область').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  await expect(legend).toHaveCount(0)
})

test('approved UKRAINE_WITH_NEAR_ME_PROMPT: the prompt with its caption until it is pressed', async ({ page, context }) => {
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ longitude: 24.03, latitude: 49.84 })
  await page.goto('/')
  const button = page.getByTestId('near-me')
  await expect(button).toHaveAttribute('data-prompt', 'true')
  await expect(page.getByTestId('near-me-hint')).toHaveText(text('map.nearMePrompt'))
  await button.click()
  await expect(page.getByTestId('near-me-hint')).toHaveCount(0)
  await expect(button).toHaveAttribute('data-prompt', 'false')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
})

test('the plain core defaults (REGION_PLACE_PIN, UKRAINE) still work when injected: no key, no prompt', async ({ page }) => {
  await useRegionPlaceDefaults(page)
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect(page.getByTestId('region-bubble').first()).toBeVisible()
  await expect(page.getByTestId('choropleth-legend')).toHaveCount(0)
  await expect(page.getByTestId('near-me')).toHaveAttribute('data-prompt', 'false')
})
