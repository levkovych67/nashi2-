import { expect, test, type Locator, type Page } from '@playwright/test'
import { useRegionPlaceDefaults, bubble, mapEl, openFilters, openLvivPins, settled, text } from './helpers/map'

// Visual baselines of the key screens on phone and desktop. MSW fixtures and the blank map style keep them deterministic.
// Remote photos are answered locally with a flat SVG, animations are frozen, and the likes count is masked.

const SHOT = { maxDiffPixelRatio: 0.01, animations: 'disabled', caret: 'hide', timeout: 40_000 } as const
const PHOTO = '<svg xmlns="http://www.w3.org/2000/svg" width="500" height="500"><rect width="500" height="500" fill="#4a5568"/><circle cx="250" cy="200" r="90" fill="#718096"/><rect x="110" y="320" width="280" height="180" rx="90" fill="#718096"/></svg>'
const SUMMARY = 'Львівський гурт, що пише авторські композиції та грає на великих сценах.'
const photo = (name: string) => new URL(`./files/${name}`, import.meta.url).pathname

test.use({ contextOptions: { reducedMotion: 'reduce' } })

test.beforeEach(async ({ page }) => {
  test.setTimeout(120_000) // screenshots wait for stillness; slow machines need room
  await page.route(/thumb\.wikimedia\.org|upload\.wikimedia\.org/, (route) =>
    route.fulfill({ contentType: 'image/svg+xml', body: PHOTO }),
  )
})

/** Mock data is answered inside the page, so the response is patched there: hasAudio=false for Okean Elzy. */
async function withoutAudio(page: Page, what: 'pins' | 'musician') {
  await page.addInitScript((kind) => {
    const original = window.fetch.bind(window)
    window.fetch = async (...args) => {
      const res = await original(...args)
      const url = args[0] instanceof Request ? args[0].url : String(args[0])
      const hit = kind === 'pins' ? /\/map\/pins/.test(url) : /\/musicians\/okean-elzy$/.test(url)
      if (!hit) return res
      const body = await res.json()
      if (kind === 'pins') {
        for (const p of body.pins) if (p.slug === 'okean-elzy') p.hasAudio = false
      } else body.hasAudio = false
      return new Response(JSON.stringify(body), { status: res.status, headers: res.headers })
    }
  }, what)
}

const likes = (page: Page): Locator[] => [page.getByTestId('likes-count')]

async function shot(page: Page, name: string, mask: Locator[] = []) {
  await page.evaluate(() => document.fonts.ready)
  await expect(page).toHaveScreenshot(name, { ...SHOT, mask })
}

test('map: region level (approved: choropleth key, near-me prompt)', async ({ page }) => {
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect(page.getByTestId('choropleth-legend')).toBeVisible()
  await expect(page.getByTestId('near-me-hint')).toBeVisible()
  const first = bubble(page, 'Львівська область')
  await expect(first).toBeVisible()
  await settled(first)
  await shot(page, 'map-region.png')
})

test('map: region level with the core defaults (REGION_PLACE_PIN, no key, no prompt)', async ({ page }) => {
  await useRegionPlaceDefaults(page)
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect(page.getByTestId('choropleth-legend')).toHaveCount(0)
  await expect(page.getByTestId('near-me-hint')).toHaveCount(0)
  const first = bubble(page, 'Львівська область')
  await expect(first).toBeVisible()
  await settled(first)
  await shot(page, 'map-region-default.png')
})

test('map: place level', async ({ page }) => {
  await page.goto('/')
  await bubble(page, 'Львівська область').click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE')
  const city = bubble(page, 'Львів')
  await expect(city).toBeVisible()
  await settled(city)
  await shot(page, 'map-place.png')
})

test('map: pin level', async ({ page }) => {
  await openLvivPins(page)
  const pin = page.getByTestId('pin').first()
  await settled(pin)
  await shot(page, 'map-pin.png')
})

test('musician preview', async ({ page }) => {
  await page.goto('/?musician=okean-elzy')
  await expect(page.getByTestId('musician-preview')).toBeVisible({ timeout: 15_000 })
  await shot(page, 'preview.png')
})

test('musician page top', async ({ page }) => {
  await page.goto('/m/okean-elzy')
  await expect(page.getByTestId('likes-count')).toBeVisible()
  await page.locator('.n-musician').evaluate((el) => (el.scrollTop = 0))
  await shot(page, 'musician-page.png', likes(page))
})

test('suggestion form: step 1', async ({ page }) => {
  await page.goto('/suggest')
  await expect(page.getByTestId('step-title')).toHaveText(text('submit.step.who'))
  await shot(page, 'suggest-step1.png')
})

test('suggestion form: review step', async ({ page }) => {
  const next = () => page.getByRole('button', { name: text('submit.next') }).click()
  const title = (id: string) => expect(page.getByTestId('step-title')).toHaveText(text(`submit.step.${id}` as 'submit.step.who'))
  await page.goto('/suggest')
  await page.getByLabel(text('submit.field.name.label')).fill('Тестовий гурт')
  await page.getByRole('radio', { name: text('submit.kind.BAND') }).check()
  await next()
  await title('where')
  await page.getByRole('combobox', { name: text('submit.field.originPlaceId.label') }).fill('Бро')
  await page.getByRole('option', { name: 'Броди, Львівська область' }).click()
  await next()
  await title('about')
  await page.getByLabel(text('submit.field.summary.label')).fill(SUMMARY)
  await next()
  await title('links')
  await page.getByRole('textbox', { name: text('submit.link.n').replace('{n}', '1'), exact: true }).fill('https://open.spotify.com/artist/abc')
  await next()
  await title('photos')
  await page.getByLabel(text('submit.field.images.label')).setInputFiles(photo('photo1.jpg'))
  await expect(page.locator('.n-suggest__photos li')).toHaveCount(1)
  await next()
  await title('review')
  await shot(page, 'suggest-review.png')
})

test('empty state: musician page without a fragment', async ({ page }) => {
  await withoutAudio(page, 'musician')
  await page.goto('/m/okean-elzy')
  await expect(page.getByText(text('musician.noAudio'))).toBeVisible()
  // the hero is covered by musician-page.png; here the audio area with the empty state is scrolled into view
  const none = page.getByText(text('musician.noAudio'))
  await none.scrollIntoViewIfNeeded()
  await expect.poll(() => none.evaluate((el) => el.getBoundingClientRect().top < innerHeight)).toBe(true)
  await shot(page, 'empty-no-fragment-page.png', likes(page))
})

test('empty state: preview without a fragment', async ({ page }) => {
  await withoutAudio(page, 'pins')
  await page.goto('/?musician=okean-elzy')
  const card = page.getByTestId('musician-preview')
  await expect(card.getByText(text('musician.noAudio'))).toBeVisible({ timeout: 15_000 })
  await shot(page, 'empty-no-fragment-preview.png')
})

test('empty state: nothing found in the region filter', async ({ page, isMobile }) => {
  await page.goto('/')
  await openFilters(page, isMobile)
  await page.getByRole('searchbox').fill('яяяя')
  await expect(page.getByText(text('list.empty'))).toBeVisible()
  await shot(page, 'empty-nothing-found.png')
})
