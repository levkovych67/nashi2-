import { expect, test } from '@playwright/test'
import { DECISIONS } from '../../src/decisions/catalog'
import { variants } from '../../src/decisions/variants'

test('all comparison pages mount every registered prototype and offer clean direct links', async ({
  page,
}) => {
  test.setTimeout(150_000)
  for (const decision of DECISIONS) {
    await page.goto('/d/' + decision.id)
    const options = page.locator('.comparisons .option')
    await expect(options).toHaveCount(variants[decision.id].length)
    for (const [index, variant] of variants[decision.id].entries()) {
      const option = options.nth(index)
      await expect(
        option.locator('[data-variant="' + variant.id + '"]'),
      ).toHaveCount(1)
      await expect(
        option.getByRole('link', { name: 'На весь екран', exact: true }),
      ).toHaveAttribute(
        'href',
        new RegExp('/v/' + decision.id + '/' + variant.id + '[?]'),
      )
    }
    expect(await page.getByRole('link').allTextContents()).not.toEqual(
      expect.arrayContaining([expect.stringMatching(/[↗↖→←]/)]),
    )
  }
})
test('system states open directly and recover in the selected identity', async ({
  page,
}) => {
  await page.goto('/states?state=api')
  await expect(
    page.getByRole('heading', { name: 'Помилка сервера' }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Повторити', exact: true }).click()
  await expect(
    page.getByRole('img', { name: /Музична карта України/ }),
  ).toBeVisible()
  await page.getByRole('combobox', { name: 'Стан' }).selectOption('rate')
  await page.getByTestId('like-button').click()
  await expect(page.getByTestId('likes-count')).toHaveText('1 лайк')
})
