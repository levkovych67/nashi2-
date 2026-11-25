import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiProblem } from '../../api/client'
import type { SubmissionFormValues } from './schema'
import { uploadSubmission } from './upload'

class FakeXhr {
  static last: FakeXhr
  method = ''
  url = ''
  body: FormData | null = null
  status = 0
  responseText = ''
  headers: Record<string, string> = {}
  upload: { onprogress: ((e: { lengthComputable: boolean; loaded: number; total: number }) => void) | null } = { onprogress: null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  aborted = false
  constructor() {
    FakeXhr.last = this
  }
  open(method: string, url: string) {
    this.method = method
    this.url = url
  }
  setRequestHeader() {}
  getResponseHeader(name: string) {
    return this.headers[name.toLowerCase()] ?? null
  }
  send(body: FormData) {
    this.body = body
  }
  abort() {
    this.aborted = true
    this.onabort?.()
  }
  respond(status: number, text: string, headers: Record<string, string> = {}) {
    this.status = status
    this.responseText = text
    this.headers = headers
    this.onload?.()
  }
}

const values: SubmissionFormValues = {
  name: 'Гурт',
  kind: 'BAND',
  originPlaceId: 'Q36036',
  birthPlaceId: null,
  summary: 'x'.repeat(40),
  genres: [],
  links: ['https://open.spotify.com/artist/abc'],
  youtubeChannelUrl: null,
  wikipediaUrl: null,
  contact: '',
  consent: true,
  images: [new File(['a'], 'a.jpg', { type: 'image/jpeg' }), new File(['b'], 'b.png', { type: 'image/png' })],
}

const readText = (b: Blob) =>
  new Promise<string>((res) => {
    const r = new FileReader()
    r.onload = () => res(String(r.result))
    r.readAsText(b)
  })

afterEach(() => vi.unstubAllGlobals())

describe('uploadSubmission', () => {
  it('reports progress and resolves the 202 body', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr)
    const seen: number[] = []
    const done = uploadSubmission(values, (r) => seen.push(r))
    const xhr = FakeXhr.last
    expect(xhr.method).toBe('POST')
    expect(xhr.url).toMatch(/\/submissions$/)
    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 25, total: 100 })
    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 100, total: 100 })
    xhr.respond(202, JSON.stringify({ id: 's1', status: 'PENDING' }))
    await expect(done).resolves.toEqual({ id: 's1', status: 'PENDING' })
    expect(seen).toEqual([0.25, 1])
  })

  it('sends data as a JSON part (with the empty bot trap) and every image', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr)
    const done = uploadSubmission(values, () => {})
    const body = FakeXhr.last.body!
    const data = body.get('data') as File
    expect(data.type).toBe('application/json')
    const json = JSON.parse(await readText(data))
    expect(json).toMatchObject({ name: 'Гурт', kind: 'BAND', consent: true, faxNumber: '' })
    expect(json).not.toHaveProperty('images')
    expect(body.getAll('images')).toHaveLength(2)
    FakeXhr.last.respond(202, '{"id":"s","status":"PENDING"}')
    await done
  })

  it('rejects with ApiProblem carrying errors[] from problem+json', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr)
    const done = uploadSubmission(values, () => {})
    FakeXhr.last.respond(
      400,
      JSON.stringify({ code: 'VALIDATION_FAILED', title: 'Bad', errors: [{ field: 'summary', message: 'm' }] }),
      { 'content-type': 'application/problem+json' },
    )
    await expect(done).rejects.toMatchObject({ status: 400, code: 'VALIDATION_FAILED', errors: [{ field: 'summary', message: 'm' }] })
  })

  it('maps a network error to ApiProblem status 0', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr)
    const done = uploadSubmission(values, () => {})
    FakeXhr.last.onerror?.()
    const err = await done.catch((e) => e)
    expect(err).toBeInstanceOf(ApiProblem)
    expect(err).toMatchObject({ status: 0, code: 'NETWORK' })
  })

  it('aborts through the signal', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXhr)
    const ctl = new AbortController()
    const done = uploadSubmission(values, () => {}, ctl.signal)
    ctl.abort()
    await expect(done).rejects.toMatchObject({ name: 'AbortError' })
    expect(FakeXhr.last.aborted).toBe(true)
  })
})
