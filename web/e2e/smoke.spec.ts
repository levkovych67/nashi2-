import { expect, test } from '@playwright/test'

test('map page renders main landmark', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('main')).toBeVisible()
})

test('musician page shows the name from fixtures', async ({ page }) => {
  await page.goto('/m/okean-elzy')
  await expect(page.getByRole('heading', { name: 'Океан Ельзи' })).toBeVisible()
})
