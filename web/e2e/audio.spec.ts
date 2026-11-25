import { expect, test, type Page } from '@playwright/test'
import { openLvivPins, text } from './helpers/map'

const audioStatus = (page: Page) => page.evaluate(() => window.__nashiAudioState?.status)
const audioPin = (page: Page) => page.locator('[data-testid="pin"][data-has-audio="true"]').first()

/** The city level mostly shows clusters; zoom into them until a pin with a fragment is on screen. */
async function openAudioPin(page: Page) {
  await openLvivPins(page)
  await expect(async () => {
    if ((await audioPin(page).count()) === 0) await page.getByTestId('cluster-bubble').first().click({ timeout: 2_000 })
    await expect(audioPin(page)).toBeVisible({ timeout: 2_000 })
  }).toPass({ timeout: 20_000 })
  await page.waitForTimeout(800) // the camera has stopped
}

test.describe('desktop hover', () => {
  test.beforeEach(({ isMobile }) => test.skip(isMobile, 'hover is a desktop gesture'))

  test('hovering a pin plays its fragment, leaving stops it', async ({ page }) => {
    await openAudioPin(page)
    await page.mouse.click(5, 300) // user activation opens the browser's autoplay gate
    const pin = audioPin(page)
    await pin.hover()
    await expect.poll(() => audioStatus(page), { timeout: 1_000 }).toBe('playing')
    await expect(pin.getByTestId('playing-badge')).toBeVisible()
    await page.mouse.move(700, 300)
    await expect.poll(() => audioStatus(page), { timeout: 3_000 }).toBe('idle')
    await expect(page.getByTestId('playing-badge')).toHaveCount(0)
  })

  test('before any click the browser blocks sound and the hint shows', async ({ page }) => {
    // Reaching a pin takes clicks, and a click is a user activation that opens the autoplay gate for good. So the
    // block the browser applies before any gesture is emulated: play() rejects with NotAllowedError.
    await page.addInitScript(() => {
      const play = HTMLMediaElement.prototype.play
      HTMLMediaElement.prototype.play = function (this: HTMLMediaElement) {
        if ((window as unknown as { __blockAudio?: boolean }).__blockAudio) {
          return Promise.reject(new DOMException('blocked', 'NotAllowedError'))
        }
        return play.call(this)
      }
    })
    await openAudioPin(page)
    await page.evaluate(() => ((window as unknown as { __blockAudio?: boolean }).__blockAudio = true))
    await audioPin(page).hover()
    await expect.poll(() => audioStatus(page), { timeout: 2_000 }).toBe('blocked')
    await expect(page.getByText(text('map.soundBlockedHint'))).toBeVisible()
  })

  test('muted: hover does not start audio', async ({ page }) => {
    await openAudioPin(page)
    await page.mouse.click(5, 300)
    await page.getByTestId('sound-toggle').click()
    await expect(page.getByTestId('sound-toggle')).toHaveText(text('map.soundOff'))
    await audioPin(page).hover()
    await page.waitForTimeout(800)
    expect(await audioStatus(page)).toBe('idle')
    await page.getByTestId('sound-toggle').click()
    await expect(page.getByTestId('sound-toggle')).toHaveText(text('map.soundOn'))
  })
})

test.describe('mobile tap', () => {
  test.beforeEach(({ isMobile }) => test.skip(!isMobile, 'tap is a phone gesture'))

  test('tap opens the preview and plays, a second tap stops', async ({ page }) => {
    await openAudioPin(page)
    // the controls cover the top of the map and the preview sheet the bottom: tap the highest pin between them
    const pins = await page.locator('[data-testid="pin"][data-has-audio="true"]').all()
    const tops = await Promise.all(pins.map(async (p) => (await p.boundingBox())!.y))
    const free = tops.map((y, i) => ({ y, i })).filter(({ y }) => y > 130)
    const pin = pins[free.sort((a, b) => a.y - b.y)[0].i]
    await pin.tap()
    await expect(page.getByTestId('musician-preview')).toBeVisible()
    await expect.poll(() => audioStatus(page), { timeout: 3_000 }).toBe('playing')
    await page.waitForTimeout(700) // two taps within 500 ms are the map's double-tap zoom, not two presses
    await pin.tap()
    await expect.poll(() => audioStatus(page), { timeout: 3_000 }).toBe('idle')
  })
})
