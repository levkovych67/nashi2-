import { expect, test, type Request } from '@playwright/test'
import { bubble, drillInto, mapEl, text } from './helpers/map'

// Acceptance checks named in the ТЗ (docs/tasks/02-tech-team-task.md): §5 likes «E2E (100 тапів = +100)» and
// NFR-03 «Playwright-тест кількості маркерів».

const digits = (s: string | null) => Number((s ?? '').replace(/\D/g, ''))
const isLikePost = (r: Request) => r.method() === 'POST' && /\/musicians\/[^/]+\/likes$/.test(r.url())

test('§5: 100 taps add exactly 100, sent as two batches of 50, and the server keeps them after a reload', async ({ page }) => {
  const posts: Request[] = []
  let answered = 0
  page.on('request', (r) => isLikePost(r) && posts.push(r))
  page.on('response', (r) => isLikePost(r.request()) && answered++)
  await page.goto('/m/okean-elzy')
  const count = page.getByTestId('likes-count')
  await expect(count).toContainText(/\d/)
  const before = digits(await count.textContent())

  // one synchronous burst: the batch limit (50), not the 600 ms window, splits it
  await page.getByTestId('like-button').evaluate((el) => {
    for (let i = 0; i < 100; i++) (el as HTMLElement).click()
  })
  expect(digits(await count.textContent())).toBe(before + 100) // optimistic, at once
  await expect.poll(() => answered).toBe(2)
  expect(posts.map((r) => r.postDataJSON())).toEqual([{ count: 50 }, { count: 50 }])
  expect(digits(await count.textContent())).toBe(before + 100) // the server's answers agree, no dip

  await page.reload()
  await expect(page.getByTestId('likes-count')).toContainText(/\d/)
  expect(digits(await page.getByTestId('likes-count').textContent())).toBe(before + 100)
  // nothing else was sent: no third batch, no keepalive on the reload
  expect(posts.map((r) => r.postDataJSON())).toEqual([{ count: 50 }, { count: 50 }])
})

test('NFR-03: at pin level in Kyiv (603 musicians in the fixtures) the map holds at most 300 DOM markers', async ({ page }) => {
  const map = mapEl(page)
  const markers = page.locator('.maplibregl-marker')
  const counted: number[] = []
  const count = async () => {
    const n = await markers.count()
    counted.push(n)
    expect(n).toBeLessThanOrEqual(300)
  }
  const step = async (name: 'map.zoomIn' | 'map.zoomOut') => {
    const zoom = (await map.getAttribute('data-zoom'))!
    await page.getByRole('button', { name: text(name) }).click()
    await expect(map).not.toHaveAttribute('data-zoom', zoom)
    await expect(map).toHaveAttribute('data-level', 'PIN')
  }

  await page.goto('/')
  await expect(map).toHaveAttribute('data-level', 'REGION')
  await bubble(page, 'Київ').click()
  await expect(map).toHaveAttribute('data-level', 'PLACE')
  await drillInto(page, 'Київ', 'PIN')
  await expect(page.getByTestId('pin').first()).toBeVisible()
  await count()
  // every pin zoom over the city centre, up to 17: the clusters split into ever more markers on the way
  const zoomIn = page.getByRole('button', { name: text('map.zoomIn') })
  while (await zoomIn.isEnabled()) {
    await step('map.zoomIn')
    await count()
  }
  test.info().annotations.push({ type: 'markers', description: counted.join(' → ') })
  expect(Math.max(...counted)).toBeGreaterThan(counted[0]) // the sweep did reach denser views than the first step
})
