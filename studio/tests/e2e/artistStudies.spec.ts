import { expect, test, type Locator } from '@playwright/test'
import { join } from 'node:path'

// The pins first render on the static Lviv plate and move into the map once its
// tiles have loaded. A hover or a hold that began on the static pin ends with it,
// so the gesture is repeated on the pin until the expected reaction shows.
const onSettledPin = (gesture: () => Promise<void>) =>
  expect(gesture).toPass({ timeout: 15_000 })
const centre = async (pin: Locator) => {
  await pin.scrollIntoViewIfNeeded()
  const box = (await pin.boundingBox())!
  return [box.x + box.width / 2, box.y + box.height / 2] as const
}

for (const variant of [
  'TAP_PREVIEW_AND_PLAY',
  'TAP_PREVIEW_PLAY_BUTTON',
  'LONG_PRESS_PLAY',
]) {
  test('Жест на піні · ' + variant, async ({ page }, info) => {
    test.skip(info.project.name !== 'mobile', 'Мобільний жест')
    await page.goto('/v/audio.mobile-trigger/' + variant)
    const pin = page.getByRole('button', { name: 'Океан Ельзи, є фрагмент' })
    const state = page.getByTestId('playing-indicator')
    if (variant === 'LONG_PRESS_PLAY') {
      await onSettledPin(async () => {
        await page.mouse.move(...(await centre(pin)))
        await page.mouse.down()
        try {
          await expect(state).toContainText('Океан Ельзи')
        } finally {
          await page.mouse.up()
        }
      })
      await expect(page.getByRole('article')).toHaveCount(0)
    } else {
      await pin.tap()
      await expect(page.getByRole('article')).toBeVisible()
      if (variant === 'TAP_PREVIEW_PLAY_BUTTON') {
        await expect(state).toHaveCount(0)
        await page.getByRole('button', { name: 'Слухати фрагмент' }).tap()
      }
      await expect(state).toContainText('Океан Ельзи')
      await page.getByRole('button', { name: 'Пауза' }).tap()
      await expect(state).toHaveCount(0)
    }
    await page
      .getByRole('button', { name: 'Формула води, фрагмента немає' })
      .tap()
    await expect(
      page.getByText('Фрагмента немає', { exact: true }),
    ).toBeVisible()
    await expect(state).toHaveCount(0)
  })
}
for (const variant of ['RING_PROGRESS', 'EQUALIZER_BADGE']) {
  test('Наведення та вимкнення звуку · ' + variant, async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop', 'Наведення вказівника')
    await page.goto('/v/audio.desktop-hover/' + variant)
    const pin = page.getByRole('button', { name: 'Океан Ельзи, є фрагмент' })
    await onSettledPin(async () => {
      await pin.hover()
      await expect(page.getByRole('status')).toContainText('натисніть')
    })
    await page.getByRole('button', { name: 'Увімкнути звук' }).click()
    await onSettledPin(async () => {
      await pin.hover()
      await expect(page.getByTestId('playing-indicator')).toContainText(
        'Океан Ельзи',
      )
    })
    await page.getByRole('button', { name: 'Вимкнути звук' }).click()
    await expect(page.getByTestId('playing-indicator')).toHaveCount(0)
  })
}
for (const variant of ['HERO', 'EDITORIAL', 'COMPACT']) {
  test(
    'Профіль із фото, без фото, з каналом і з заявки · ' + variant,
    async ({ page }, info) => {
      await page.goto('/v/musician.page/' + variant)
      for (const name of [
        'Океан Ельзи',
        'Формула води',
        'Мертвий Півень',
        'Мідний папір',
      ]) {
        await page
          .getByRole('combobox', { name: 'Виконавець' })
          .selectOption({ label: name })
        await expect(page.getByTestId('musician-name')).toHaveText(name)
        await expect(page.getByTestId('musician-materials')).toHaveCount(1)
        await expect(page.getByTestId('musician-origin')).toHaveCount(1)
        await expect(page.getByTestId('like-button')).toBeVisible()
        if (name === 'Формула води')
          await expect(
            page.getByTestId('musician-image-fallback'),
          ).toBeVisible()
        if (name === 'Мідний папір')
          await expect(page.getByTestId('text-source')).toHaveCount(0)
        if (name === 'Океан Ельзи')
          await expect(page.getByTestId('musician-youtube')).toHaveCount(1)
        await page
          .locator('.artist-profile')
          .screenshot({ path: join(info.outputDir, name + '.png') })
      }
    },
  )
}
for (const variant of ['HEART_BURST', 'APPLAUSE', 'CUSTOM_SYMBOL']) {
  test(
    'Повторні лайки, пакет 429 і повторне надсилання · ' + variant,
    async ({ page }) => {
      await page.goto('/v/like.button/' + variant)
      const likes = page.getByTestId('likes-count')
      const read = async () =>
        Number((await likes.innerText()).replace(/\D/g, ''))
      const initial = await read()
      await page.getByText('Умови надсилання', { exact: true }).click()
      await page.getByRole('checkbox').check()
      const reaction = page.getByTestId('like-button')
      await reaction.scrollIntoViewIfNeeded()
      const box = (await reaction.boundingBox())!
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, {
        clickCount: 30,
      })
      await expect.poll(read).toBe(initial + 30)
      const sent = () =>
        page.evaluate(
          () =>
            (
              window as Window & {
                __likeRequests?: { status: number; count: number }[]
              }
            ).__likeRequests || [],
        )
      await expect
        .poll(async () =>
          (await sent())
            .filter((x) => x.status === 200)
            .reduce((sum, r) => sum + r.count, 0),
        )
        .toBe(30)
      expect((await sent()).some((x) => x.status === 429)).toBe(true)
    },
  )
}
