import { expect, test, type Request } from '@playwright/test'

const digits = (s: string | null) => Number((s ?? '').replace(/\D/g, ''))

test('7 taps add 7 at once, one POST of 7 follows within 600 ms, and the server keeps them after a reload', async ({ page }) => {
  const posts: Request[] = []
  page.on('request', (r) => r.method() === 'POST' && /\/musicians\/[^/]+\/likes$/.test(r.url()) && posts.push(r))
  await page.goto('/m/okean-elzy')
  const count = page.getByTestId('likes-count')
  await expect(count).toContainText(/\d/)
  const before = digits(await count.textContent())

  const request = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/likes'))
  const button = page.getByTestId('like-button')
  for (let i = 0; i < 7; i++) await button.click()
  const tapped = Date.now()
  expect(digits(await count.textContent())).toBe(before + 7) // optimistic: no waiting for the server
  const sent = await request
  expect(Date.now() - tapped).toBeLessThan(1200) // 600 ms batch window plus the tap loop and the round trip
  expect(sent.postDataJSON()).toEqual({ count: 7 })
  await expect.poll(() => posts.length).toBe(1)
  await page.waitForTimeout(800)
  expect(posts).toHaveLength(1)
  expect(digits(await count.textContent())).toBe(before + 7)

  await page.reload()
  await expect(page.getByTestId('likes-count')).toContainText(/\d/)
  expect(digits(await page.getByTestId('likes-count').textContent())).toBe(before + 7)
})
