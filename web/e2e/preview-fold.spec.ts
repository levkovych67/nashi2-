import { expect, test } from '@playwright/test'
import { text } from './helpers/map'

// Phone viewport 390x664 (the iPhone 13 profile, shorter than 844 because of the browser bars): the origin explanation (D-14) and the link to the page are visible in the card without scrolling.
test.beforeEach(({ isMobile }) => test.skip(!isMobile, 'phone layout'))

async function expectCardComplete(page: import('@playwright/test').Page) {
  const card = page.getByTestId('musician-preview')
  await expect(card.getByTestId('preview-origin-notice')).toBeInViewport({ ratio: 1 })
  await expect(card.getByRole('link', { name: text('preview.openProfile') })).toBeInViewport({ ratio: 1 })
  await expect(card.getByRole('button', { name: text('common.close') })).toBeInViewport({ ratio: 1 })
}

test('with a fragment playing, notice and profile link are in view', async ({ page }) => {
  await page.goto('/?musician=okean-elzy')
  const card = page.getByTestId('musician-preview')
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: text('preview.play') }).tap()
  await expect(card.getByTestId('audio-caption')).toBeVisible()
  await expectCardComplete(page)
  await expect(card.getByRole('button', { name: text('preview.stop') })).toBeInViewport({ ratio: 1 })
})

test('without audio, notice and profile link are in view', async ({ page }) => {
  // the mock fixtures give this musician audio: take it away from the pin the card receives
  await page.addInitScript(() => {
    const original = window.fetch.bind(window)
    window.fetch = async (...args) => {
      const res = await original(...args)
      const url = args[0] instanceof Request ? args[0].url : String(args[0])
      if (!/\/map\/pins/.test(url)) return res
      const body = await res.json()
      for (const p of body.pins) if (p.slug === 'okean-elzy') p.hasAudio = false
      return new Response(JSON.stringify(body), { status: res.status, headers: res.headers })
    }
  })
  await page.goto('/?musician=okean-elzy')
  await expect(page.getByTestId('musician-preview').getByText(text('musician.noAudio'))).toBeVisible()
  await expectCardComplete(page)
})
