import { chromium, expect, type Page } from '@playwright/test'
import { copyFile, mkdir, mkdtemp, readdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// Run against the populated local V1 frontend and its backend.
const origin = new URL(process.env.V1_URL || 'http://localhost:4173')
if (!['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname))
  throw Error('V1_URL має вказувати на локальний V1.')
const dest = fileURLToPath(new URL('../public/audit/', import.meta.url))
const stage = await mkdtemp(join(tmpdir(), 'nashi-v1-captures-'))
const browser = await chromium.launch(
  process.env.CHROMIUM_PATH
    ? { executablePath: process.env.CHROMIUM_PATH }
    : {},
)
type Artist = { slug: string; name: string; type?: string; city?: string }
const snap = async (page: Page, name: string) => {
  await page.evaluate(async () => {
    await document.fonts.ready
  })
  await page.screenshot({
    path: join(stage, name + '.png'),
    animations: 'disabled',
  })
}
try {
  const desktop = await browser.newPage({
    baseURL: origin.origin,
    viewport: { width: 1440, height: 900 },
    locale: 'uk-UA',
  })
  await desktop.goto('/')
  await expect(
    desktop.getByRole('searchbox', { name: /Пошук за ім/ }),
  ).toBeVisible()
  await expect(
    desktop.getByRole('button', { name: /наблизити|цій точці/ }).first(),
  ).toBeVisible()
  await snap(desktop, 'map-desktop')
  const response = await desktop.request.get('/api/v1/map/pins')
  if (!response.ok()) throw Error('API локальної V1 не повернуло піни.')
  const pins: Artist[] = await response.json()
  const artist = pins.find((p) => p.slug === '999') || pins.find((p) => p.slug)
  if (!artist) throw Error('У локальному V1 немає артиста з профілем.')
  const mobile = await browser.newPage({
    baseURL: origin.origin,
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    locale: 'uk-UA',
  })
  await mobile.goto('/')
  await expect(
    mobile.getByRole('button', { name: /наблизити|цій точці/ }).first(),
  ).toBeVisible()
  await snap(mobile, 'map-mobile')
  await mobile.getByRole('button', { name: 'Фільтри', exact: true }).click()
  await expect(mobile.getByRole('heading', { name: 'Фільтри' })).toBeVisible()
  await snap(mobile, 'filter-sheet')
  await mobile.goto('/?region=LVIV')
  await expect(
    mobile.getByRole('button', { name: 'Фільтри (1)' }),
  ).toBeVisible()
  await expect(
    mobile.getByRole('button', { name: /наблизити|цій точці/ }).first(),
  ).toBeVisible()
  await snap(mobile, 'lviv')
  await mobile.goto('/?artist=' + encodeURIComponent(artist.slug))
  const profile = mobile.getByRole('link', { name: 'Переглянути профіль' })
  await expect(profile).toBeVisible()
  await snap(mobile, 'preview')
  await profile.click()
  await expect(
    mobile.getByRole('heading', { name: artist.name, exact: true }),
  ).toBeVisible()
  await snap(mobile, 'profile')
  await mobile.goto('/submit')
  await expect(
    mobile.getByRole('heading', { name: 'Запропонувати' }),
  ).toBeVisible()
  await snap(mobile, 'submit')
  await desktop
    .getByRole('button', { name: /^Антитіла .*Київ/ })
    .first()
    .click()
  await desktop.getByRole('button', { name: 'Закрити', exact: true }).click()
  const stack = desktop
    .getByRole('button', { name: /та ще \d{2,} — показати всіх у цій точці/ })
    .first()
  await expect(stack).toBeVisible()
  await stack.click()
  await snap(desktop, 'kyiv-stack')
  await stack.click()
  for (let i = 0; i < 4; i++) {
    await desktop.getByRole('button', { name: 'Zoom out' }).click()
    await desktop.waitForTimeout(350) // MapLibre animates each one-level navigation command.
  }
  await expect(
    desktop.getByRole('button', { name: /наблизити/ }).first(),
  ).toBeVisible()
  await snap(desktop, 'kyiv-cluster')
  await mkdir(dest, { recursive: true })
  for (const name of await readdir(stage))
    await copyFile(join(stage, name), join(dest, name))
  console.log('Збережено 9 знімків локальної V1 у ' + dest)
} finally {
  await browser.close()
  await rm(stage, { recursive: true, force: true })
}
