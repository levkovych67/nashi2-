import { expect, test } from '@playwright/test'
import { approveMapProposal, bubble, mapEl, text } from './helpers/map'

// What the map does once the studio's proposal is APPROVED (injected for the test, decisions.json stays PROPOSED).

test('CHOROPLETH_PLACE_PIN: numerals and the «Менше — Більше» key at region level only', async ({ page }) => {
  await approveMapProposal(page)
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  const legend = page.getByTestId('choropleth-legend')
  await expect(legend).toHaveText(`${text('map.choroplethLess')}${text('map.choroplethMore')}`)
  await expect(bubble(page, 'Львівська область').locator('.n-bubble--numeral')).toBeVisible()

  await bubble(page, 'Львівська область').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  await expect(legend).toHaveCount(0)
})

test('UKRAINE_WITH_NEAR_ME_PROMPT: the prompt with its caption until it is pressed', async ({ page, context }) => {
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ longitude: 24.03, latitude: 49.84 })
  await approveMapProposal(page)
  await page.goto('/')
  const button = page.getByTestId('near-me')
  await expect(button).toHaveAttribute('data-prompt', 'true')
  await expect(page.getByTestId('near-me-hint')).toHaveText(text('map.nearMePrompt'))
  await button.click()
  await expect(page.getByTestId('near-me-hint')).toHaveCount(0)
  await expect(button).toHaveAttribute('data-prompt', 'false')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
})

test('without the override the PROPOSED decisions keep the defaults: no key, no prompt', async ({ page }) => {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect(page.getByTestId('region-bubble').first()).toBeVisible()
  await expect(page.getByTestId('choropleth-legend')).toHaveCount(0)
  await expect(page.getByTestId('near-me')).toHaveAttribute('data-prompt', 'false')
})
