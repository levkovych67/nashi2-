import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { mapEl, openFilters, openLvivPins, text } from './helpers/map'

async function violations(page: Page) {
  const result = await new AxeBuilder({ page: page as unknown as ConstructorParameters<typeof AxeBuilder>[0]['page'] }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze()
  return result.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => {
      // WCAG 2.5.8 "equivalent": the 24 region bubbles of the country overview crowd a phone screen and overlap each other,
      // but every region is also reachable through the region filter list (a separate control that meets the size).
      const nodes = v.id === 'target-size' ? v.nodes.filter((n) => !n.html.includes('data-testid="region-bubble"')) : v.nodes
      return nodes.length ? `${v.id} (${v.impact}): ${nodes.map((n) => n.target.join(' ')).join(' | ')}` : ''
    })
    .filter(Boolean)
}

test('map, musician page and suggest form have no serious or critical violations', async ({ page, isMobile }) => {
  await page.goto('/')
  await expect(page.getByTestId('region-bubble').first()).toBeVisible()
  expect(await violations(page), 'map').toEqual([])

  await openLvivPins(page)
  await page.getByTestId('pin').first().click()
  await expect(page.getByTestId('musician-preview')).toBeVisible()
  expect(await violations(page), 'map with preview').toEqual([])

  await page.goto('/')
  await openFilters(page, isMobile)
  expect(await violations(page), 'map with filters').toEqual([])

  await page.goto('/m/okean-elzy')
  await expect(page.getByRole('heading', { name: 'Океан Ельзи' })).toBeVisible()
  expect(await violations(page), 'musician').toEqual([])

  await page.goto('/suggest')
  await expect(page.getByRole('form').or(page.locator('form')).first()).toBeVisible()
  expect(await violations(page), 'suggest').toEqual([])
  await page.getByLabel(text('submit.field.name.label')).fill('Тестовий гурт')
  await page.getByRole('radio', { name: text('submit.kind.BAND') }).check()
  expect(await violations(page), 'suggest filled').toEqual([])

  await page.goto('/about')
  await expect(page.getByRole('main')).toBeVisible()
  expect(await violations(page), 'about').toEqual([])
})

test('pins and bubbles are reachable by keyboard and show focus', async ({ page }) => {
  await page.goto('/')
  const first = page.getByTestId('region-bubble').first()
  await expect(first).toBeVisible()
  const activeId = () => page.evaluate(() => document.activeElement?.getAttribute('data-testid'))
  let reached = false
  for (let i = 0; i < 60 && !reached; i++) {
    await page.keyboard.press('Tab')
    reached = (await activeId()) === 'region-bubble'
  }
  expect(reached, 'Tab reaches a region bubble').toBe(true)
  const outline = await page.evaluate(() => {
    const s = getComputedStyle(document.activeElement!)
    return { style: s.outlineStyle, width: parseFloat(s.outlineWidth), shadow: s.boxShadow }
  })
  expect((outline.style !== 'none' && outline.width > 0) || outline.shadow !== 'none').toBe(true)

  await openLvivPins(page)
  const pin = page.getByTestId('pin').first()
  await pin.focus()
  await expect(pin).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByTestId('musician-preview')).toBeVisible()
  await expect(pin).toHaveAttribute('aria-pressed', 'true')
})

test('reduced motion makes camera moves instant', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  await expect(mapEl(page)).toHaveAttribute('data-level', 'REGION')
  await expect(page.getByTestId('region-bubble').first()).toBeVisible()
  await page.getByRole('button', { name: /^Львівська область, / }).first().click()
  await expect(mapEl(page)).toHaveAttribute('data-level', 'PLACE', { timeout: 1_000 })
  // the camera is already at rest: a place bubble does not move between two consecutive frames
  const place = page.getByRole('button', { name: /^Львів, / }).first()
  await expect(place).toBeInViewport({ timeout: 1_000 })
  const moved = await place.evaluate(
    (el) =>
      new Promise<boolean>((resolve) => {
        const x = el.getBoundingClientRect().x
        requestAnimationFrame(() => requestAnimationFrame(() => resolve(el.getBoundingClientRect().x !== x)))
      }),
  )
  expect(moved).toBe(false)
})

test('the honeypot is never both aria-hidden and focusable', async ({ page }) => {
  await page.goto('/suggest')
  const honeypot = page.locator('input[name="faxNumber"]')
  await expect(honeypot).toHaveCount(1)
  const state = await honeypot.evaluate((el) => {
    let hidden = false
    for (let n: Element | null = el; n; n = n.parentElement) if (n.getAttribute('aria-hidden') === 'true') hidden = true
    return { hidden, tabindex: el.getAttribute('tabindex') }
  })
  expect(state.hidden && state.tabindex !== '-1').toBe(false)
  await page.keyboard.press('Tab')
  for (let i = 0; i < 12; i++) {
    expect(await page.evaluate(() => (document.activeElement as HTMLInputElement | null)?.name)).not.toBe('faxNumber')
    await page.keyboard.press('Tab')
  }
})

test('the focus stays on the submit control while a suggestion is being sent', async ({ page }) => {
  const photo = new URL('./files/photo1.jpg', import.meta.url).pathname
  const next = () => page.getByRole('button', { name: text('submit.next') }).click()
  await page.goto('/suggest')
  await page.getByLabel(text('submit.field.name.label')).fill('Тестовий гурт')
  await page.getByRole('radio', { name: text('submit.kind.BAND') }).check()
  await next()
  await page.getByRole('combobox', { name: text('submit.field.originPlaceId.label') }).fill('Бро')
  await page.getByRole('option', { name: 'Броди, Львівська область' }).click()
  await next()
  await page.getByLabel(text('submit.field.summary.label')).fill('Львівський гурт, що пише авторські композиції та грає на великих сценах.')
  await next()
  await page.getByRole('textbox', { name: text('submit.link.n').replace('{n}', '1'), exact: true }).fill('https://open.spotify.com/artist/abc')
  await next()
  await page.getByLabel(text('submit.field.images.label')).setInputFiles(photo)
  await expect(page.locator('.n-suggest__photos li')).toHaveCount(1)
  await next()
  await page.getByLabel(text('submit.consent')).check()
  expect(await violations(page), 'suggest review').toEqual([])

  const submit = page.locator('button[type="submit"]')
  await submit.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByTestId('submit-progress')).toBeVisible()
  await expect(submit).toBeFocused()
  await expect(submit).toHaveAttribute('aria-disabled', 'true')
  await expect(page.getByRole('heading', { name: text('submit.success') })).toBeVisible({ timeout: 10_000 })
})
