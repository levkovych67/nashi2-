import { expect, test } from '@playwright/test'
import pinsFile from '../../contracts/fixtures/map-pins.json' with { type: 'json' }
import { mapEl, openFilters, resultsCount, text } from './helpers/map'

const pins = pinsFile.pins
const lviv = pins.filter((p) => p.region === 'LVIV').length

test('region filter writes URL, survives reload, reset clears', async ({ page, isMobile }) => {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect.poll(() => resultsCount(page)).toBe(pins.length)

  await openFilters(page, isMobile)
  await page.getByRole('checkbox', { name: /Львівська область/ }).check()
  await page.getByRole('button', { name: text('filters.apply') }).click()
  await expect(page).toHaveURL(/[?&]region=LVIV\b/)
  await expect.poll(() => resultsCount(page)).toBe(lviv)
  if (isMobile) await expect(page.getByTestId('filters-open')).toContainText('(1)')

  await page.reload()
  await expect(page).toHaveURL(/[?&]region=LVIV\b/)
  await expect.poll(() => resultsCount(page)).toBe(lviv)

  await openFilters(page, isMobile)
  await page.getByRole('button', { name: text('filters.reset') }).click()
  await expect(page).not.toHaveURL(/region=/)
  await expect.poll(() => resultsCount(page)).toBe(pins.length)
})

test('?region=LVIV counts the LVIV musicians from the fixtures', async ({ page }) => {
  await page.goto('/?region=LVIV')
  await expect.poll(() => resultsCount(page)).toBe(lviv)
  await expect(page.getByTestId('results-count')).toContainText(String(lviv))
})

test('the region list is searchable and several regions combine', async ({ page, isMobile }) => {
  await page.goto('/')
  await openFilters(page, isMobile)
  await page.getByRole('searchbox').fill('льв')
  await expect(page.getByRole('checkbox')).toHaveCount(1)
  await page.getByRole('checkbox', { name: /Львівська область/ }).check()
  await page.getByRole('searchbox').fill('')
  await page.getByRole('checkbox', { name: /Київська область/ }).check()
  await page.getByRole('button', { name: text('filters.apply') }).click()
  await expect(page).toHaveURL(/region=LVIV(,|%2C)KYIV\b/)
  const expected = pins.filter((p) => p.region === 'LVIV' || p.region === 'KYIV').length
  await expect.poll(() => resultsCount(page)).toBe(expected)
})
