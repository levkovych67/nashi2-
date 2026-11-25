import { expect, test, type Page } from '@playwright/test'
import { text } from './helpers/map'

const photo = (name: string) => new URL(`./files/${name}`, import.meta.url).pathname
const SUMMARY = 'Львівський гурт, що пише авторські композиції та грає на великих сценах.'

const next = (page: Page) => page.getByRole('button', { name: text('submit.next') }).click()
const stepTitle = (page: Page, id: 'who' | 'where' | 'about' | 'links' | 'photos' | 'review') =>
  expect(page.getByTestId('step-title')).toHaveText(text(`submit.step.${id}` as 'submit.step.who'))

async function pickOrigin(page: Page, typed = 'Бро') {
  await page.getByRole('combobox', { name: text('submit.field.originPlaceId.label') }).fill(typed)
  await page.getByRole('option', { name: 'Броди, Львівська область' }).click()
}

/** Walks steps 1-5 with valid data and stops on the review step. */
async function reachReview(page: Page, name: string, kind: 'PERSON' | 'BAND' = 'BAND') {
  await page.goto('/suggest')
  await page.getByLabel(text('submit.field.name.label')).fill(name)
  await page.getByRole('radio', { name: text(`submit.kind.${kind}`) }).check()
  await next(page)
  await stepTitle(page, 'where')
  await pickOrigin(page)
  await next(page)
  await stepTitle(page, 'about')
  await page.getByLabel(text('submit.field.summary.label')).fill(SUMMARY)
  await next(page)
  await stepTitle(page, 'links')
  await link(page, 1).fill('https://open.spotify.com/artist/abc')
  await next(page)
  await stepTitle(page, 'photos')
  await page.getByLabel(text('submit.field.images.label')).setInputFiles(photo('photo1.jpg'))
  await expect(page.locator('.n-suggest__photos li')).toHaveCount(1)
  await next(page)
  await stepTitle(page, 'review')
}

const link = (page: Page, n: number) =>
  page.getByRole('textbox', { name: text('submit.link.n').replace('{n}', String(n)), exact: true })

const send = (page: Page, label = text('submit.send')) => page.getByRole('button', { name: label }).click()

test('happy path: autocomplete, links with badge, two photos, success', async ({ page }) => {
  await page.goto('/suggest')
  await expect(page.getByRole('heading', { level: 1, name: text('submit.title') })).toBeVisible()
  await page.getByLabel(text('submit.field.name.label')).fill('Тестовий гурт')
  await page.getByRole('radio', { name: text('submit.kind.BAND') }).check()
  await next(page)

  await stepTitle(page, 'where')
  await pickOrigin(page)
  await expect(page.getByRole('combobox', { name: text('submit.field.originPlaceId.label') })).toHaveValue('Броди, Львівська область')
  await next(page)

  await stepTitle(page, 'about')
  await page.getByLabel(text('submit.field.summary.label')).fill(SUMMARY)
  const genre = page.getByLabel(text('submit.field.genres.label'))
  await genre.fill('фолк')
  await genre.press('Enter')
  await expect(page.locator('.n-suggest__chips li')).toHaveText(/фолк/)
  await next(page)

  await stepTitle(page, 'links')
  await link(page, 1).fill('https://open.spotify.com/artist/abc')
  await expect(page.getByTestId('platform-badge').first()).toHaveText(text('musician.platform.SPOTIFY'))
  await page.getByRole('button', { name: text('submit.link.add') }).click()
  await link(page, 2).fill('https://soundcloud.com/some-band')
  await expect(page.getByTestId('platform-badge').nth(1)).toHaveText(text('musician.platform.SOUNDCLOUD'))
  await next(page)

  await stepTitle(page, 'photos')
  await page.getByLabel(text('submit.field.images.label')).setInputFiles([photo('photo1.jpg'), photo('photo2.png')])
  await expect(page.locator('.n-suggest__photos li')).toHaveCount(2)
  await next(page)

  await stepTitle(page, 'review')
  const review = page.getByTestId('review')
  await expect(review).toContainText('Тестовий гурт')
  await expect(review).toContainText('Броди, Львівська область')
  await expect(review).toContainText('soundcloud.com/some-band')
  await page.getByLabel(text('submit.consent')).check()
  await send(page)

  // real upload progress is shown and a second press cannot send again
  await expect(page.getByTestId('submit-progress')).toBeVisible()
  await expect(page.getByRole('button', { name: text('submit.sending') })).toBeDisabled()
  await expect(page.getByRole('heading', { name: text('submit.success') })).toBeVisible({ timeout: 10_000 })
  await expect(page.getByTestId('suggest-form')).toHaveCount(0)

  // the draft is gone: a new visit starts empty
  await page.goto('/suggest')
  await expect(page.getByLabel(text('submit.field.name.label'))).toHaveValue('')
})

test('each step validates its own fields; going back keeps the values', async ({ page }) => {
  await page.goto('/suggest')
  await next(page)
  await expect(page.getByRole('alert').filter({ hasText: text('submit.error.required') })).toBeVisible()
  await stepTitle(page, 'who')
  await expect(page.getByLabel(text('submit.field.name.label'))).toBeFocused()
  await page.getByLabel(text('submit.field.name.label')).fill('Тарас')
  await next(page)
  await stepTitle(page, 'where')
  await page.getByRole('combobox', { name: text('submit.field.originPlaceId.label') }).fill('Бро')
  await next(page)
  await expect(page.getByRole('alert').filter({ hasText: text('submit.error.placePick') })).toBeVisible()
  await stepTitle(page, 'where')
  await page.getByRole('button', { name: text('submit.back') }).click()
  await expect(page.getByLabel(text('submit.field.name.label'))).toHaveValue('Тарас')
})

test('server validation errors are shown at the field, on the step that holds it', async ({ page }) => {
  await reachReview(page, 'Тестовий гурт [msw:400]')
  await page.getByLabel(text('submit.consent')).check()
  await send(page)
  await stepTitle(page, 'about')
  await expect(page.getByText('Опис не пройшов перевірку редакції')).toBeVisible()
  await expect(page.getByLabel(text('submit.field.summary.label'))).toBeFocused()
  await expect(page.getByLabel(text('submit.field.summary.label'))).toHaveValue(SUMMARY)
})

test('network failure keeps the data and allows a retry', async ({ page }) => {
  const name = 'Тестовий гурт [msw:abort]'
  await reachReview(page, name)
  await page.getByLabel(text('submit.consent')).check()
  await send(page)
  await expect(page.getByTestId('submit-error')).toHaveText(text('submit.error.offline'))
  await expect(page.getByTestId('review')).toContainText(name)
  await expect(page.getByLabel(text('submit.consent'))).toBeChecked()
  await send(page, text('submit.retry'))
  await expect(page.getByRole('heading', { name: text('submit.success') })).toBeVisible({ timeout: 10_000 })
})

test('birth place field only for a person', async ({ page }) => {
  await page.goto('/suggest')
  await page.getByLabel(text('submit.field.name.label')).fill('Тарас')
  await next(page)
  await stepTitle(page, 'where')
  await expect(page.getByRole('combobox', { name: text('submit.field.birthPlaceId.label') })).toBeVisible()
  await page.getByRole('button', { name: text('submit.back') }).click()
  await page.getByRole('radio', { name: text('submit.kind.BAND') }).check()
  await next(page)
  await stepTitle(page, 'where')
  await expect(page.getByRole('combobox', { name: text('submit.field.birthPlaceId.label') })).toHaveCount(0)
})

test('a non-image renamed to .jpg is rejected in the browser', async ({ page }) => {
  await page.goto('/suggest')
  await page.getByLabel(text('submit.field.name.label')).fill('Тарас')
  await next(page)
  await pickOrigin(page)
  await next(page)
  await page.getByLabel(text('submit.field.summary.label')).fill(SUMMARY)
  await next(page)
  await link(page, 1).fill('https://open.spotify.com/artist/abc')
  await next(page)
  await stepTitle(page, 'photos')
  await page.getByLabel(text('submit.field.images.label')).setInputFiles(photo('fake.jpg'))
  await expect(page.getByTestId('photo-problems')).toContainText(`fake.jpg: ${text('submit.error.imageUnreadable')}`)
  await expect(page.locator('.n-suggest__photos li')).toHaveCount(0)
  await next(page)
  await expect(page.getByText(text('submit.error.imagesCount'))).toBeVisible()
})
