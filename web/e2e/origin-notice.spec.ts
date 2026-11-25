import { expect, test } from '@playwright/test'
import { bubble, drillInto, mapEl, originNoticeText } from './helpers/map'

test('origin notice stays visible on every level', async ({ page }) => {
  await page.goto('/')
  const notice = page.getByTestId('origin-notice')

  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect(notice).toBeVisible()
  await expect(notice).toHaveText(originNoticeText)

  await bubble(page, 'Львівська область').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  await expect(notice).toBeVisible()

  await drillInto(page, 'Львів', 'PIN')
  await expect(notice).toBeVisible()

  // the musician preview repeats the explanation and does not hide the legend
  await page.getByTestId('pin').first().click()
  await expect(page.getByTestId('musician-preview')).toContainText(originNoticeText)
  await expect(notice).toBeVisible()
})
