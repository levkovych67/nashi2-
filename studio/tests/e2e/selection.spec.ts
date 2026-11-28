import { expect, test } from '@playwright/test'

test('a shared draft survives navigation and reload and opens its fullscreen QR', async ({ page }) => {
  const payload = { v: 1, s: { 'map.cluster': { variant: 'BUBBLE_COUNT_LABEL' } } }
  const sel = Buffer.from(JSON.stringify(payload)).toString('base64url')
  await page.goto('/d/map.cluster?sel=' + sel)
  await expect(page).toHaveURL(/\/d\/map\.cluster$/)
  const option = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Кількість і назва', exact: true }) })
  await expect(option.getByRole('button', { name: '✓ Обрано' })).toHaveAttribute('aria-pressed', 'true')
  await page.reload()
  await expect(option.getByRole('button', { name: '✓ Обрано' })).toBeVisible()
  await option.getByRole('button', { name: 'QR-код' }).click()
  await expect(option.getByRole('img', { name: /QR-код для відкриття/ })).toBeVisible()
  await option.getByRole('link', { name: 'На весь екран' }).click()
  await expect(page.getByTestId('studio-chrome')).toHaveCount(0)
  await page.getByRole('combobox').selectOption('KYIV_CITY')
  await expect(page.getByRole('heading', { name: 'Київ', exact: true })).toBeVisible()
  await expect(page.getByTestId('origin-notice')).toBeVisible()
})

test('a bad link displays a Ukrainian notice, including on the audit route', async ({ page }) => {
  await page.goto('/audit?sel=%25%25%25')
  // The audit route has its own status (the finding count), so pick the notice among the status regions.
  await expect(
    page.getByRole('status').filter({ hasText: 'Частину вибору з посилання не розпізнано' }),
  ).toBeVisible()
  await expect(page).toHaveURL(/\/audit$/)
})
