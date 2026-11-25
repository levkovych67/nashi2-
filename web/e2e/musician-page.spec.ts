import { expect, test } from '@playwright/test'
import { text } from './helpers/map'

test('unknown slug shows not found with a link to the map', async ({ page }) => {
  await page.goto('/m/no-such-musician')
  await expect(page.getByRole('heading', { name: text('errors.notFound') })).toBeVisible()
  await page.getByRole('link', { name: text('errors.backToMap') }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByTestId('map')).toBeVisible()
})

test('youtube iframe loads only after click', async ({ page }) => {
  // The mock fixtures have no videos: add one to the musician response the page receives.
  await page.addInitScript(() => {
    const original = window.fetch.bind(window)
    window.fetch = async (...args) => {
      const res = await original(...args)
      const url = args[0] instanceof Request ? args[0].url : String(args[0])
      if (!/\/musicians\/okean-elzy$/.test(url)) return res
      const body = await res.json()
      body.materials.videos = [{ youtubeId: 'dQw4w9WgXcQ', title: 'Тестове відео' }]
      return new Response(JSON.stringify(body), { status: res.status, headers: res.headers })
    }
  })
  const youtubeRequests: string[] = []
  await page.route(/youtube/, (route) => {
    youtubeRequests.push(route.request().url())
    return route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>player</title>' })
  })
  await page.goto('/m/okean-elzy')
  const block = page.getByTestId('musician-youtube')
  await expect(block).toBeVisible()
  await page.waitForTimeout(500)
  await expect(block.locator('iframe')).toHaveCount(0)
  expect(youtubeRequests).toEqual([])
  await block.getByRole('button', { name: text('musician.playVideo').replace('{title}', 'Тестове відео') }).click()
  await expect(block.locator('iframe')).toHaveAttribute('src', 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ')
  await expect.poll(() => youtubeRequests.length).toBeGreaterThan(0)
})

test('page shows all blocks and «Показати на карті» goes to the map with ?musician=<slug>', async ({ page }) => {
  await page.goto('/m/okean-elzy')
  await expect(page.getByTestId('musician-name')).toHaveText('Океан Ельзи')
  await expect(page.getByTestId('musician-origin').getByTestId('origin-notice')).toBeVisible()
  await expect(page.getByTestId('text-source')).toContainText('CC BY-SA')
  await page.getByRole('link', { name: text('musician.showOnMap') }).click()
  await expect(page).toHaveURL(/\/\?musician=okean-elzy$/)
})
