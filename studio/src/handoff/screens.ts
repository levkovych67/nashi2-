import { submissionScreens } from '../../scripts/qa/submissionScenario'
import { mkdir } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { chromium, type Page } from '@playwright/test'
import { createServer } from 'vite'
import { DECISIONS } from '../decisions/catalog'
import { encodeSelection, STORAGE_KEY } from '../selection/selection'
import { DEVICES, type DeviceKind } from '../shell/devices'
import type { DecisionFile } from './review'

type Save = (relative: string) => Promise<void>
const mapSteps = [
  'ukraine',
  'region',
  'places',
  'lviv',
  'pins',
  'audio',
  'preview',
  'profile',
  'likes',
  'return',
]
async function mapScreens(page: Page, save: Save) {
  for (let i = 0; i < mapSteps.length; i++) {
    await page
      .locator('[data-testid="flow-progress"][data-step="' + (i + 1) + '"]')
      .waitFor()
    await save(String(i + 1).padStart(2, '0') + '-' + mapSteps[i])
    if (i < mapSteps.length - 1) await page.getByTestId('flow-next').click()
  }
}

async function captureDevice(
  page: Page,
  origin: string,
  out: string,
  device: DeviceKind,
  file: DecisionFile,
) {
  const files: string[] = []
  const save = async (relative: string) => {
    const path = relative + '--' + device + '.png'
    await mkdir(dirname(join(out, path)), { recursive: true })
    await page.evaluate(async () => {
      await document.fonts.ready
      await new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      )
    })
    await page.screenshot({
      path: join(out, path),
      animations: 'disabled',
      scale: 'css',
    })
    files.push(path)
  }
  for (const { id } of DECISIONS) {
    const entry = file.decisions[id]
    await page.goto(
      origin +
        '/v/' +
        id +
        '/' +
        entry.variant +
        '?identity=' +
        file.decisions.identity.variant +
        '&offline=1',
    )
    await page.locator('.full-demo .proto').waitFor()
    await page
      .getByText(/^Завантажуємо/)
      .first()
      .waitFor({ state: 'hidden' })
    await save('screens/' + id + '/' + entry.variant)
  }
  for (const flow of ['map-to-musician', 'suggest-musician']) {
    await page.goto(
      origin + '/flow/' + flow + '?device=' + device + '&offline=1',
    )
    await page.locator('.scenario-frame .proto').waitFor()
    // The device preview uses the same contract viewport as the screenshots.
    // Expand only its outer framing, keeping its actual controls and scroll position.
    await page.addStyleTag({
      content:
        '.scenario-frame.capturing-flow { position:fixed !important; inset:0 !important; z-index:1000 !important; width:100vw !important; height:100dvh !important; max-width:none !important; max-height:none !important; border:0 !important; }',
    })
    const shot: Save = async (name) => {
      // Keep guidance buttons reachable between captures.
      await page
        .locator('.scenario-frame')
        .evaluate((el) => el.classList.add('capturing-flow'))
      try {
        await save('screens/flows/' + flow + '/' + name)
      } finally {
        await page
          .locator('.scenario-frame')
          .evaluate((el) => el.classList.remove('capturing-flow'))
      }
    }
    if (flow === 'map-to-musician') await mapScreens(page, shot)
    else
      await submissionScreens(
        page,
        shot,
        file.decisions['submission.form'].variant === 'WIZARD',
      )
  }
  return files
}

export async function captureScreens(
  root: string,
  out: string,
  file: DecisionFile,
) {
  const existing = process.env.HANDOFF_STUDIO_URL
  const local = (value: string) =>
    ['localhost', '127.0.0.1', '[::1]'].includes(new URL(value).hostname)
  if (existing && !local(existing))
    throw Error('HANDOFF_STUDIO_URL має бути локальною адресою')
  const server = existing
    ? null
    : await createServer({
        root,
        server: {
          host: '127.0.0.1',
          port: 0,
          strictPort: true,
          open: false,
          hmr: false,
        },
        define: {
          'import.meta.env.VITE_STUDIO_OFFLINE_MAP': JSON.stringify('1'),
        },
        logLevel: 'error',
      })
  try {
    await server?.listen()
    const address = server?.httpServer?.address()
    const origin = existing
      ? new URL(existing).origin
      : address && typeof address !== 'string'
        ? 'http://127.0.0.1:' + address.port
        : null
    if (!origin) throw Error('Не вдалося запустити локальну студію')
    const endpoint = process.env.HANDOFF_LOCAL_CDP
    if (endpoint && !local(endpoint))
      throw Error('HANDOFF_LOCAL_CDP має бути локальною адресою')
    const browser = endpoint
      ? await chromium.connectOverCDP(endpoint)
      : await chromium.launch({
          headless: true,
          executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
        })
    try {
      const picked = Object.fromEntries(
        Object.entries(file.decisions).map(([key, entry]) => [
          key,
          { variant: entry.variant, params: entry.params },
        ]),
      )
      const files: string[] = []
      for (const device of ['mobile', 'desktop'] as const) {
        const context = await browser.newContext({
          viewport: DEVICES[device],
          deviceScaleFactor: 1,
          hasTouch: device === 'mobile',
          isMobile: device === 'mobile',
          reducedMotion: 'reduce',
          locale: 'uk-UA',
        })
        try {
          await context.route(/^https?:\/\//, (route) =>
            new URL(route.request().url()).origin === origin
              ? route.continue()
              : route.abort(),
          )
          await context.addInitScript(
            ({ key, value }) => localStorage.setItem(key, value),
            { key: STORAGE_KEY, value: encodeSelection(picked) },
          )
          const page = await context.newPage()
          page.setDefaultTimeout(15000)
          files.push(...(await captureDevice(page, origin, out, device, file)))
        } finally {
          await context.close()
        }
      }
      return files
    } finally {
      await browser.close()
    }
  } finally {
    await server?.close()
  }
}
