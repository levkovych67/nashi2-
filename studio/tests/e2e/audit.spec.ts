import { test, expect } from '@playwright/test'

test('рішення, аудит і підтвердження зі справжнім знімком пов’язані', async ({
  page,
}) => {
  await page.goto('/audit?area=pin&severity=high')
  const finding = page.locator('#A-10')
  await expect(finding.getByRole('heading', { name: /109/ })).toBeVisible()
  await expect(finding.getByRole('img')).toBeVisible()
  expect(
    await finding
      .getByRole('img')
      .evaluate(
        (img: HTMLImageElement) => img.complete && img.naturalWidth > 300,
      ),
  ).toBe(true)
  await finding.getByRole('link', { name: 'Кількість на карті' }).click()
  await page.getByText(/Що ми бачили у V1/).click()
  await page.getByRole('link', { name: /^A-10/ }).click()
  await expect(page).toHaveURL(/\/audit#A-10$/)
  await expect(page.locator('#A-10')).toBeInViewport()
})
