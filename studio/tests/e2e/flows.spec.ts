import { expect, test } from '@playwright/test'
import { join } from 'node:path'
import { submissionScreens } from '../../scripts/qa/submissionScenario'

const combinations: Record<string, Record<string, string>> = {
  default: {},
  A: {
    'map.aggregation': 'PLACE_PIN',
    'map.pin': 'DROP_ICON',
    'audio.mobile-trigger': 'TAP_PREVIEW_PLAY_BUTTON',
    'musician.preview': 'FLOATING_CARD',
    'musician.page': 'EDITORIAL',
    'filters.region': 'CHIPS_SCROLL',
    'submission.form': 'WIZARD',
    'nav.structure': 'TAB_BAR',
  },
  B: {
    'map.aggregation': 'CHOROPLETH_PLACE_PIN',
    'map.pin': 'DOT_LABEL',
    'audio.mobile-trigger': 'LONG_PRESS_PLAY',
    'musician.preview': 'BOTTOM_SHEET',
    'musician.page': 'COMPACT',
    'filters.region': 'MAP_TAP_REGION',
    'submission.form': 'SINGLE_PAGE',
    'nav.structure': 'TOP_BAR_MENU',
  },
}
for (const [name, choices] of Object.entries(combinations)) {
  test.describe('Набір ' + name, () => {
    test.beforeEach(async ({ page }) => {
      const draft = Object.fromEntries(
        Object.entries(choices).map(([id, variant]) => [id, { variant }]),
      )
      await page.addInitScript(
        ({ key, draft }) => localStorage.setItem(key, draft),
        {
          key: 'nashi-studio:draft:v1',
          draft: Buffer.from(JSON.stringify({ v: 1, s: draft })).toString(
            'base64url',
          ),
        },
      )
    })
    test('карта, фрагмент, профіль, лайк і попередня камера', async ({
      page,
    }, info) => {
      const errors: string[] = []
      page.on('pageerror', (err) => errors.push(err.message))
      await page.goto('/flow/map-to-musician?device=' + info.project.name)
      const viewport = page.locator('.scenario-frame')
      const current = page.getByTestId('flow-progress')
      let initialLikes = ''
      for (let step = 1; step <= 10; step++) {
        await expect(current).toHaveAttribute('data-step', String(step))
        if (step === 6)
          await expect(viewport.locator('.journey-gesture')).toHaveAttribute(
            'data-trigger',
            info.project.name === 'mobile'
              ? choices['audio.mobile-trigger'] || 'TAP_PREVIEW_AND_PLAY'
              : 'RING_PROGRESS',
          )
        if (step === 7)
          await expect(viewport.getByRole('article')).toHaveClass(
            new RegExp(choices['musician.preview'] || 'BOTTOM_SHEET'),
          )
        if (step === 8) {
          await expect(viewport.getByTestId('musician-name')).toBeVisible()
          initialLikes = await viewport.getByTestId('likes-count').innerText()
        }
        if (step === 9)
          await expect(viewport.getByTestId('likes-count')).not.toHaveText(
            initialLikes,
          )
        if (step === 10)
          await expect(
            viewport.getByLabel('Музикант', { exact: true }),
          ).toBeVisible()
        if (step < 8 || step === 10)
          await expect(
            viewport.locator('[data-testid="origin-notice"]:visible').first(),
          ).toBeVisible()
        await viewport.screenshot({
          path: join(info.outputDir, 'map-' + step + '.png'),
        })
        if (step < 10) await page.getByTestId('flow-next').click()
      }
      expect(errors).toEqual([])
    })
    test('заявка з помилкою 413, заміною фото й повторним надсиланням', async ({
      page,
    }, info) => {
      await page.goto('/flow/suggest-musician?device=' + info.project.name)
      const visited = new Set<number>()
      await submissionScreens(
        page,
        async (label) => {
          visited.add(
            Number(
              await page.getByTestId('flow-progress').getAttribute('data-step'),
            ),
          )
          await page
            .locator('.scenario-frame')
            .screenshot({ path: join(info.outputDir, label + '.png') })
        },
        choices['submission.form'] === 'WIZARD',
      )
      await expect(page.getByTestId('flow-progress')).toHaveAttribute(
        'data-step',
        '6',
      )
      expect([...visited].sort()).toEqual([1, 2, 3, 4, 5, 6])
    })
  })
}
