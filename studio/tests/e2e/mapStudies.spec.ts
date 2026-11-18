import { expect, test } from '@playwright/test'

test('від бази карти до повного профілю Львова й назад', async ({ page }) => {
  await page.goto('/v/map.aggregation/REGION_PLACE_PIN')
  await expect(page.getByTestId('region-bubble').first()).toBeVisible()
  await page.getByLabel('Область або місто').selectOption('LVIV')
  await page.getByLabel('Населений пункт').selectOption('Q36036')
  const artist = page.getByLabel('Музикант', { exact: true })
  await artist.selectOption({ label: 'Океан Ельзи' })
  await page.getByRole('button', { name: 'Профіль музиканта' }).click()
  await expect(
    page.getByRole('heading', { name: 'Океан Ельзи', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: /Повернутися на карту/ }).click()
  await expect(artist).toBeVisible()
  await expect(page.getByTestId('origin-notice')).toBeVisible()
})

test('геолокація показує область, симуляція дозволяє перевірити відмову', async ({
  page,
}) => {
  await page.goto('/v/map.initial-view/UKRAINE_WITH_NEAR_ME_PROMPT')
  await page.getByText('Перевірити геолокацію').click()
  await page.getByLabel('Джерело координат').selectOption('kyiv')
  await page.getByRole('button', { name: 'Біля мене' }).click()
  await expect(page.getByRole('status')).toHaveText('Київ')
  await page.getByLabel('Джерело координат').selectOption('deny')
  await page.getByRole('button', { name: 'Біля мене' }).click()
  await expect(page.getByRole('status')).toContainText('недоступна')
})
