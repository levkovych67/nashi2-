import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from './mocks/node'
import { ApiProblem, apiFetch } from './client'

const problem = (body: object, status: number, headers: Record<string, string> = {}) =>
  HttpResponse.json(body, { status, headers: { 'Content-Type': 'application/problem+json', ...headers } })

describe('apiFetch', () => {
  it('repeats array params', async () => {
    let search = ''
    server.use(
      http.get('*/api/v2/musicians', ({ request }) => {
        search = new URL(request.url).search
        return HttpResponse.json({})
      }),
    )
    await apiFetch('/musicians', { query: { region: ['LVIV', 'KYIV_CITY'], q: undefined, page: 0 } })
    expect(search).toBe('?region=LVIV&region=KYIV_CITY&page=0')
  })

  it('maps problem+json to ApiProblem with field errors', async () => {
    server.use(
      http.post('*/api/v2/submissions', () =>
        problem(
          { status: 400, code: 'VALIDATION_FAILED', title: 'Некоректні дані', errors: [{ field: 'summary', message: 'Мінімум 30 символів' }] },
          400,
        ),
      ),
    )
    const err = await apiFetch('/submissions', { method: 'POST' }).catch((e) => e)
    expect(err).toBeInstanceOf(ApiProblem)
    expect(err).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'summary', message: 'Мінімум 30 символів' }],
    })
  })

  it('reads Retry-After into retryAfterSec', async () => {
    server.use(
      http.get('*/api/v2/map/pins', () => problem({ status: 429, code: 'RATE_LIMITED', title: 'x' }, 429, { 'Retry-After': '12' })),
    )
    const err = await apiFetch('/map/pins').catch((e) => e)
    expect(err).toMatchObject({ status: 429, code: 'RATE_LIMITED', retryAfterSec: 12 })
  })

  it('network failure becomes ApiProblem status 0 code NETWORK', async () => {
    server.use(http.get('*/api/v2/map/pins', () => HttpResponse.error()))
    const err = await apiFetch('/map/pins').catch((e) => e)
    expect(err).toBeInstanceOf(ApiProblem)
    expect(err).toMatchObject({ status: 0, code: 'NETWORK' })
  })

  it('returns undefined on 204', async () => {
    server.use(http.get('*/api/v2/musicians/x/fragment', () => new HttpResponse(null, { status: 204 })))
    expect(await apiFetch('/musicians/x/fragment')).toBeUndefined()
  })
})
