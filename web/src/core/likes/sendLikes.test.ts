import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../api/mocks/node'
import { sendLikes } from './sendLikes'

const LIKES = '*/musicians/:id/likes'

afterEach(() => vi.restoreAllMocks())

describe('sendLikes', () => {
  it('POSTs {count} and returns the new likesCount', async () => {
    let body: unknown
    server.use(
      http.post(LIKES, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ likesCount: 42 })
      }),
    )
    expect(await sendLikes('abc', 7, { keepalive: false })).toEqual({ ok: true, likesCount: 42 })
    expect(body).toEqual({ count: 7 })
  })

  it('passes keepalive through to fetch', async () => {
    const spy = vi.spyOn(globalThis, 'fetch')
    await sendLikes('0020093dc8080b4a9cf48a5d', 1, { keepalive: true })
    expect(spy.mock.calls[0][1]?.keepalive).toBe(true)
  })

  it('maps 429 with Retry-After to retryAfterMs, falling back to 1000', async () => {
    const limited = (headers: Record<string, string>) =>
      HttpResponse.json(
        { code: 'RATE_LIMITED' },
        { status: 429, headers: { 'Content-Type': 'application/problem+json', ...headers } },
      )
    server.use(http.post(LIKES, () => limited({ 'Retry-After': '3' })))
    expect(await sendLikes('abc', 1, { keepalive: false })).toEqual({ ok: false, retryAfterMs: 3000 })
    server.use(http.post(LIKES, () => limited({})))
    expect(await sendLikes('abc', 1, { keepalive: false })).toEqual({ ok: false, retryAfterMs: 1000 })
  })

  it('throws on other failures', async () => {
    server.use(http.post(LIKES, () => HttpResponse.json({}, { status: 500 })))
    await expect(sendLikes('abc', 1, { keepalive: false })).rejects.toMatchObject({ status: 500 })
  })
})
